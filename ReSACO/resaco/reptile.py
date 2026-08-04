"""Reptile-based meta-training: Outer Loop (Algorithm 1) + Inner Loop (Algorithm 2)."""

import copy
import random

import numpy as np
import torch

from mec_core import config
from mec_core.env import MECOffloadEnv
from mec_core.normalize import normalize_state
from mec_core.replay_buffer import ReplayBuffer
from .sac import SACAgent


def _interpolate_params(theta, theta_k, alpha):
    """theta <- theta + alpha * (theta_k - theta)   (Eq. 8)"""
    new_theta = {}
    for group in theta:
        new_theta[group] = {}
        for key in theta[group]:
            new_theta[group][key] = theta[group][key] + alpha * (
                theta_k[group][key] - theta[group][key]
            )
    return new_theta


def inner_loop(theta, scenario, num_inner_updates: int, agent_kwargs=None, seed=None,
               replay_buffer=None):
    """Algorithm 2: refine a local copy of theta on scenario `scenario` for
    `num_inner_updates` SAC iterations. Returns the refined local theta_k.

    Note on an ambiguity in the paper: Algorithm 1 lines 6-9 wrap the
    "Invoke Algorithm 2" call in its own for n = 1..N loop, while
    Algorithm 2/3 and the Section V-B text ("we make N = 50 SAC updates
    per scenario", "each episode represents one Outer Loop iteration")
    describe N SAC-Update iterations *inside* one Algorithm-2 invocation.
    Nesting both readings would give N^2 updates per outer iteration and
    contradict V-B's accounting, so this implementation follows the
    textual/V-B reading: one Algorithm-2 call performing N SAC updates.

    `replay_buffer`: optionally reuse an existing buffer instead of
    Algorithm 2's fresh-D-per-inner-loop. A warm buffer skips the
    batch_size-transition warm-up entirely and gives the very first update
    a richer sample -- a deliberate speed/quality deviation from the paper,
    used only when outer_loop(persist_buffers=True)."""
    agent_kwargs = agent_kwargs or {}
    local_agent = SACAgent(**agent_kwargs)
    local_agent.load_params(theta)
    if replay_buffer is not None:
        local_agent.replay_buffer = replay_buffer

    env = MECOffloadEnv(scenario, seed=seed)
    local_agent.sac_update_loop(env, num_transitions=num_inner_updates)

    return local_agent.get_params()


def outer_loop(
    scenarios,
    num_outer_iterations: int = config.NUM_OUTER_ITERATIONS,
    num_inner_updates: int = config.NUM_INNER_SAC_UPDATES,
    meta_lr: float = config.META_LR,
    agent_kwargs=None,
    seed: int = None,
    progress_every: int = 20,
    reward_log=None,
    metrics_log=None,
    eval_every: int = 1,
    persist_buffers: bool = False,
):
    """Algorithm 1: repeatedly sample a scenario, refine a local copy via
    the Inner Loop, and shift the global meta-parameter theta towards it.

    If `metrics_log` (a list) is given, every evaluated iteration appends a
    dict with avg_reward plus policy-health diagnostics: the greedy action
    counts over the eval rollout, the stochastic policy's mean entropy on
    the visited states, and the current entropy temperature alpha -- enough
    to see a collapse-onto-one-action forming *during* training instead of
    discovering it afterwards from the served policy's behavior.

    `eval_every`: run the (training-independent) evaluation rollout only
    every this many iterations -- at 1 the eval overhead is ~a third of the
    whole run's env interactions, purely for logging granularity.

    `persist_buffers`: keep one replay buffer per scenario across outer
    iterations instead of Algorithm 2's fresh D each inner loop. Skips the
    per-iteration warm-up and reuses past experience -- faster wall-clock
    per real update and more sample-diverse minibatches, at the cost of a
    documented deviation from the paper's procedure (off by default).

    Returns the final meta-learned parameter theta*.
    """
    agent_kwargs = agent_kwargs or {}
    rng = random.Random(seed)

    global_agent = SACAgent(**agent_kwargs)
    theta = global_agent.get_params()
    scenario_buffers = {} if persist_buffers else None

    for k in range(1, num_outer_iterations + 1):
        scenario_idx = rng.randrange(len(scenarios))
        scenario = scenarios[scenario_idx]
        replay_buffer = None
        if persist_buffers:
            # created empty on first visit, then mutated in place by every
            # inner loop that revisits this scenario
            replay_buffer = scenario_buffers.setdefault(
                scenario_idx, ReplayBuffer(config.REPLAY_BUFFER_SIZE))
        theta_k = inner_loop(
            theta, scenario, num_inner_updates, agent_kwargs=agent_kwargs,
            seed=rng.randint(0, 2**31), replay_buffer=replay_buffer,
        )
        theta = _interpolate_params(theta, theta_k, meta_lr)

        want_log = (reward_log is not None or metrics_log is not None) and k % eval_every == 0
        if want_log or (progress_every and k % progress_every == 0):
            eval_agent = SACAgent(**agent_kwargs)
            eval_agent.load_params(theta)
            avg_reward, action_counts, policy_entropy = _evaluate(
                eval_agent, scenario, seed=rng.randint(0, 2**31))
            if reward_log is not None:
                reward_log.append(avg_reward)
            if metrics_log is not None:
                metrics_log.append({
                    "iteration": k,
                    "avg_reward": avg_reward,
                    "policy_entropy": policy_entropy,
                    "alpha": eval_agent.alpha,
                    "action_counts": action_counts,
                })
            if progress_every and k % progress_every == 0:
                print(f"[Outer Loop] iter {k}/{num_outer_iterations} "
                      f"avg_reward={avg_reward:.3f} entropy={policy_entropy:.3f} "
                      f"alpha={eval_agent.alpha:.3f}")

    return theta


def _evaluate(agent, scenario, num_steps: int = 50, seed=None):
    """Greedy rollout on `scenario`. Returns (avg_reward, action_counts,
    mean stochastic-policy entropy over the visited states)."""
    env = MECOffloadEnv(scenario, seed=seed)
    state = env.reset()
    total = 0.0
    action_counts = [0] * agent.action_dim
    states = []
    for _ in range(num_steps):
        states.append(np.asarray(state, dtype=np.float32))
        action = agent.select_action(state, greedy=True)
        action_counts[action] += 1
        state, reward, _, _ = env.step(action)
        total += reward

    with torch.no_grad():
        batch = torch.as_tensor(normalize_state(np.stack(states)), dtype=torch.float32,
                                device=agent.device)
        probs = agent.actor.action_probs(batch)
        entropy = float(-(probs * torch.log(probs + 1e-8)).sum(dim=-1).mean().item())

    return total / num_steps, action_counts, entropy
