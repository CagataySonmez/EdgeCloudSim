"""Train the comparison baselines for scripts/compare_algorithms.py:
the four from Section V-C of the ReSACO paper -- SAC (no
meta-initialization), DDPG, A2C, A3C -- plus MULTITASK, the multi-task
(joint) pretraining control (P2-1).

The Section V-C four get the same K * N update budget as ReSACO's
meta-training (Section V-B-1 defaults: 300 * 50 = 15,000 transitions),
cycling through the same scenario pool, with no Reptile Outer Loop.

MULTITASK is the *strongest* non-meta control: one SACAgent, one replay
buffer shared across scenarios, trained scenario-by-scenario exactly like
Reptile samples them -- but with plain SGD instead of meta-updates. Its
env-interaction budget matches ReSACO's TRUE total including warm-ups:
K * (batch_size + N) = 300 * 114 = 34,200 interactions (see README's
"One accounting note"). In the MAML/Reptile literature this control often
matches or beats meta-learning; without it, "the advantage comes from
meta-learning" is not an established causal claim.

Usage:
    python scripts/train_baselines.py [--scenarios M] [--steps N] [--seed S]
"""

import argparse
import os
import random
import sys

import torch

_RESACO_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, _RESACO_DIR)                      # ReSACO/  -> `resaco`, `bridge`
sys.path.insert(0, os.path.dirname(_RESACO_DIR))     # repo root -> `mec_core`, `baselines`

from mec_core import config
from mec_core.env import MECOffloadEnv
from resaco.sac import SACAgent
from baselines.ddpg import DDPGAgent
from baselines.a2c import A2CAgent
from baselines.a3c import train_a3c
from mec_core.scenario import sample_scenario_pool
from mec_core.seeding import seed_everything


def train_sac_no_meta(scenarios, total_steps, seed):
    agent = SACAgent()
    steps_per_scenario = max(1, total_steps // len(scenarios))
    for i, scenario in enumerate(scenarios):
        env = MECOffloadEnv(scenario, seed=seed + i)
        agent.sac_update_loop(env, num_transitions=steps_per_scenario)
    return agent


def train_ddpg(scenarios, total_steps, seed):
    agent = DDPGAgent(epsilon_decay_steps=total_steps)
    steps_per_scenario = max(1, total_steps // len(scenarios))
    for i, scenario in enumerate(scenarios):
        env = MECOffloadEnv(scenario, seed=seed + i)
        agent.train_loop(env, num_transitions=steps_per_scenario)
    return agent


def train_a2c(scenarios, total_steps, seed):
    agent = A2CAgent()
    steps_per_scenario = max(1, total_steps // len(scenarios))
    for i, scenario in enumerate(scenarios):
        env = MECOffloadEnv(scenario, seed=seed + i)
        agent.train_loop(env, num_transitions=steps_per_scenario)
    return agent


def train_multitask(scenarios,
                    num_outer_iterations=config.NUM_OUTER_ITERATIONS,
                    num_inner_updates=config.NUM_INNER_SAC_UPDATES,
                    seed=None, batch_size=config.BATCH_SIZE,
                    progress_every=50):
    """Multi-task (joint) pretraining control for Reptile meta-training.

    One SACAgent and ONE replay buffer shared across all scenarios -- the
    defining difference from meta-training, where every inner loop starts
    a fresh agent/buffer from theta. Episodes mirror outer_loop exactly:
    each of the `num_outer_iterations` episodes draws a scenario from the
    pool with the same RNG protocol and interacts with a fresh
    MECOffloadEnv for (batch_size + num_inner_updates) steps -- the same
    114-per-iteration accounting as one Reptile inner loop (64 warm-up +
    50 counted; README "One accounting note") -- so the TOTAL env
    interactions equal meta-training's exactly:
    num_outer_iterations * (batch_size + num_inner_updates).

    Fairness is defined by equal env interactions (the paper's own
    resource accounting), not equal gradient updates: because the shared
    buffer never resets, updates fire on every step once it holds
    batch_size transitions, so MULTITASK performs more updates from the
    same experience. That is the inherent advantage of joint training
    over fresh-buffer meta-training, and exactly what this control is
    meant to measure -- not an accounting bug.

    Returns (agent, env_steps) so tests can assert the interaction budget.
    """
    rng = random.Random(seed)
    agent = SACAgent()
    env_steps = 0
    steps_per_episode = batch_size + num_inner_updates
    for k in range(1, num_outer_iterations + 1):
        scenario = scenarios[rng.randrange(len(scenarios))]
        env = MECOffloadEnv(scenario, seed=rng.randint(0, 2**31))
        state = env.reset()
        for _ in range(steps_per_episode):
            if len(agent.replay_buffer) < batch_size:
                # same uniform-random warm-up policy as sac_update_loop,
                # drawn from the env's own seeded RNG
                action = env.rng.randrange(agent.action_dim)
            else:
                action = agent.select_action(state)
            next_state, reward, done, info = env.step(action)
            agent.replay_buffer.push(state, action, reward, next_state, float(done))
            agent.update(batch_size=batch_size)
            env_steps += 1
            state = next_state
        if progress_every and k % progress_every == 0:
            print(f"  [MULTITASK] episode {k}/{num_outer_iterations} "
                  f"({env_steps} env interactions)")
    return agent, env_steps


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--scenarios", type=int, default=config.NUM_META_SCENARIOS)
    parser.add_argument("--steps", type=int, default=config.NUM_OUTER_ITERATIONS * config.NUM_INNER_SAC_UPDATES,
                         help="total env-interaction budget for the Section V-C four "
                              "(SAC/DDPG/A2C/A3C; default matches ReSACO's K*N). MULTITASK "
                              "ignores this: its budget is --outer * (batch + --inner), the "
                              "exact env-interaction count of meta-training incl. warm-ups")
    parser.add_argument("--outer", type=int, default=config.NUM_OUTER_ITERATIONS,
                         help="MULTITASK only: number of episodes K (matches train_meta --outer)")
    parser.add_argument("--inner", type=int, default=config.NUM_INNER_SAC_UPDATES,
                         help="MULTITASK only: counted steps per episode N (matches train_meta --inner)")
    parser.add_argument("--a3c-workers", type=int, default=4)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--out-dir", type=str, default=os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "checkpoints"))
    args = parser.parse_args()

    os.makedirs(args.out_dir, exist_ok=True)

    seed_everything(args.seed)  # see resaco/seeding.py
    scenarios = sample_scenario_pool(args.scenarios, seed=args.seed)
    print(f"Training baselines on {len(scenarios)} scenarios, {args.steps} transitions each...")

    print("\n[1/5] Training SAC (no meta-init)...")
    sac_agent = train_sac_no_meta(scenarios, args.steps, args.seed)
    sac_path = os.path.join(args.out_dir, "sac_no_meta.pt")
    torch.save(sac_agent.get_params(), sac_path)
    print(f"  saved -> {sac_path}")

    print("\n[2/5] Training DDPG...")
    ddpg_agent = train_ddpg(scenarios, args.steps, args.seed)
    ddpg_path = os.path.join(args.out_dir, "ddpg.pt")
    torch.save(ddpg_agent.get_params(), ddpg_path)
    print(f"  saved -> {ddpg_path}")

    print("\n[3/5] Training A2C...")
    a2c_agent = train_a2c(scenarios, args.steps, args.seed)
    a2c_path = os.path.join(args.out_dir, "a2c.pt")
    torch.save(a2c_agent.get_params(), a2c_path)
    print(f"  saved -> {a2c_path}")

    print(f"\n[4/5] Training A3C ({args.a3c_workers} async workers)...")
    updates_per_worker = max(1, args.steps // (args.a3c_workers * 20))  # rollout_len=20
    a3c_agent = train_a3c(scenarios, num_workers=args.a3c_workers,
                           updates_per_worker=updates_per_worker, seed=args.seed)
    a3c_path = os.path.join(args.out_dir, "a3c.pt")
    torch.save(a3c_agent.get_params(), a3c_path)
    print(f"  saved -> {a3c_path}")

    print(f"\n[5/5] Training MULTITASK (joint pretraining, "
          f"{args.outer} x ({config.BATCH_SIZE} + {args.inner}) env interactions)...")
    multitask_agent, env_steps = train_multitask(
        scenarios, num_outer_iterations=args.outer, num_inner_updates=args.inner,
        seed=args.seed)
    multitask_path = os.path.join(args.out_dir, "multitask.pt")
    torch.save(multitask_agent.get_params(), multitask_path)
    print(f"  saved -> {multitask_path} ({env_steps} env interactions, "
          f"= meta-training's exact budget)")

    print("\nAll baselines trained.")


if __name__ == "__main__":
    main()
