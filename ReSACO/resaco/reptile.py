"""Reptile-based meta-training: Outer Loop (Algorithm 1) + Inner Loop (Algorithm 2)."""

import copy
import random

import numpy as np
import torch

from mec_core import config
from mec_core.env import MECOffloadEnv
from mec_core.normalize import normalize_state
from mec_core.replay_buffer import ReplayBuffer
from mec_core.scenario import sample_scenario
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


def _schedule_alpha(meta_lr: float, k: int, num_outer_iterations: int, schedule: str) -> float:
    """Effective Reptile outer stepsize alpha_k at iteration k (1-based).

    "constant": alpha_k = meta_lr, every iteration -- the paper's Table II
                setup (alpha = 0.001 throughout).
    "linear":   alpha_k = meta_lr * (1 - (k-1)/K) -- the Reptile paper's
                own protocol (Nichol et al. 2018 anneal the outer stepsize
                linearly toward 0; they *start* it at 1.0, i.e. three
                orders of magnitude above Table II's 0.001). The (k-1)
                keeps the first iteration at exactly meta_lr and the final
                one at meta_lr/K -- annealing with 1 - k/K instead would
                make alpha_K exactly 0, silently discarding the entire
                final outer iteration's training.
    """
    if schedule == "constant":
        return meta_lr
    if schedule == "linear":
        return meta_lr * (1.0 - (k - 1) / num_outer_iterations)
    raise ValueError(f"unknown meta_lr_schedule {schedule!r} (use 'constant' or 'linear')")


def residual_init_fraction(meta_lr: float, num_outer_iterations: int,
                           schedule: str = "constant") -> float:
    """Fraction of the RANDOM INITIALIZATION still present in theta* after
    K outer iterations.

    Eq. 8 is an exponential moving average -- theta <- (1-alpha_k)*theta +
    alpha_k*theta_k -- so the initial (random) theta_0 survives into theta*
    with weight prod_k (1 - alpha_k): exactly (1 - alpha)^K under the
    constant schedule, and the numerically-computed product otherwise. At
    the paper's Table II defaults (alpha=0.001, K=300) this is 0.741 --
    74% of theta* is literally its random initialization, before even
    asking how informative the theta_k pulls that fill the other 26% are.
    This one number is the core diagnostic for hypothesis H1 (that Fig. 5
    fails to reproduce because theta* barely differs from a random init),
    which is why outer_loop prints it at startup.
    """
    residual = 1.0
    for k in range(1, num_outer_iterations + 1):
        residual *= 1.0 - _schedule_alpha(meta_lr, k, num_outer_iterations, schedule)
    return residual


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
    eval_scenarios=None,
    meta_lr_schedule: str = "constant",
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

    `eval_scenarios`: optional FIXED held-out evaluation set (see
    sample_heldout_scenarios). Without it, each logged iteration evaluates
    on the scenario that was just trained on, so the learning curve mixes
    real progress with per-iteration scenario-difficulty variance. With it,
    every logged iteration additionally evaluates theta on this same fixed
    set (each scenario under a fixed eval seed drawn once up front), and
    the metrics_log entry gains "eval_reward_mean"/"eval_reward_std". The
    held-out evals draw nothing from the training RNG stream (greedy
    rollouts on per-scenario seeds fixed up front), so given the same
    logging settings, a run with eval_scenarios produces bit-identical
    theta* to one without.

    `meta_lr_schedule`: how the Reptile outer stepsize evolves over k --
    "constant" (default, the paper's setup) or "linear" (Reptile's own
    annealing; see _schedule_alpha). "constant" reproduces the previous
    behavior exactly, float-op for float-op.

    Returns the final meta-learned parameter theta*.
    """
    agent_kwargs = agent_kwargs or {}
    rng = random.Random(seed)

    # The single most important diagnostic of this training run: how much
    # of the random initialization Eq. 8's EMA will leave inside theta*.
    residual = residual_init_fraction(meta_lr, num_outer_iterations, meta_lr_schedule)
    print(f"[Outer Loop] meta_lr={meta_lr:g} schedule={meta_lr_schedule} "
          f"K={num_outer_iterations}: prod(1-alpha_k) = {residual:.4g} "
          f"-- that fraction of the random init survives in theta*")

    # Eval seeds fixed once so iteration-to-iteration eval curves compare
    # theta on identical episodes (not fresh eval noise), and drawn from a
    # stream separate from `rng` so enabling held-out eval cannot perturb
    # the scenario picks / inner seeds of the training run itself.
    heldout_eval_seeds = None
    if eval_scenarios:  # an empty list disables held-out eval, same as None
        heldout_rng = random.Random(seed if seed is None else seed + 200_003)
        heldout_eval_seeds = [heldout_rng.randint(0, 2**31) for _ in eval_scenarios]

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
        theta = _interpolate_params(
            theta, theta_k,
            _schedule_alpha(meta_lr, k, num_outer_iterations, meta_lr_schedule))

        want_log = (reward_log is not None or metrics_log is not None) and k % eval_every == 0
        if want_log or (progress_every and k % progress_every == 0):
            eval_agent = SACAgent(**agent_kwargs)
            eval_agent.load_params(theta)
            avg_reward, action_counts, policy_entropy = _evaluate(
                eval_agent, scenario, seed=rng.randint(0, 2**31))
            heldout_msg = ""
            entry = None
            if metrics_log is not None:
                entry = {
                    "iteration": k,
                    "avg_reward": avg_reward,
                    "policy_entropy": policy_entropy,
                    "alpha": eval_agent.alpha,
                    "action_counts": action_counts,
                }
            if heldout_eval_seeds is not None:
                heldout = [
                    _evaluate(eval_agent, s, seed=es)[0]
                    for s, es in zip(eval_scenarios, heldout_eval_seeds)
                ]
                eval_mean = float(np.mean(heldout))
                eval_std = float(np.std(heldout))
                heldout_msg = f" heldout_eval={eval_mean:.3f}+-{eval_std:.3f}"
                if entry is not None:
                    entry["eval_reward_mean"] = eval_mean
                    entry["eval_reward_std"] = eval_std
            if reward_log is not None:
                reward_log.append(avg_reward)
            if entry is not None:
                metrics_log.append(entry)
            if progress_every and k % progress_every == 0:
                print(f"[Outer Loop] iter {k}/{num_outer_iterations} "
                      f"avg_reward={avg_reward:.3f} entropy={policy_entropy:.3f} "
                      f"alpha={eval_agent.alpha:.3f}{heldout_msg}")

    return theta


def sample_heldout_scenarios(num: int, training_scenarios, seed: int = None,
                             source: str = "mixed", env_names=None):
    """Samples a FIXED held-out evaluation set for outer_loop(eval_scenarios=...):
    `num` scenarios from the same distribution as the training pool, rejected
    until disjoint from every training scenario (and internally duplicate-free),
    so the held-out curve measures generalization to genuinely unseen
    scenarios rather than re-evaluation of trained ones.

    Deterministic given `seed`, and drawn from a different stream than
    sample_scenario_pool(seed) uses, so training pool and eval set never
    share a sampling sequence even under the same seed.
    """
    rng = random.Random(seed if seed is None else seed + 100_003)
    heldout = []
    attempts = 0
    while len(heldout) < num:
        attempts += 1
        if attempts > 1000 * max(1, num):
            raise RuntimeError(
                f"could not sample {num} scenarios disjoint from the training pool "
                f"after {attempts} attempts -- scenario space too constrained "
                f"(source={source!r}, env_names={env_names!r})")
        s = sample_scenario(rng, source=source, env_names=env_names)
        if s in training_scenarios or s in heldout:
            continue
        heldout.append(s)
    return heldout


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
