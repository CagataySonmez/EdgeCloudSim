"""Train the four comparison baselines used in Section V-C of the ReSACO
paper: SAC (no meta-initialization), DDPG, A2C, A3C.

Each baseline gets the same total environment-interaction budget as
ReSACO's meta-training (K * N transitions, Section V-B-1 defaults:
300 * 50 = 15,000) so the comparison in scripts/compare_algorithms.py is
apples-to-apples. Unlike ReSACO, none of these baselines use Reptile's
Outer Loop -- they train directly (i.e. from scratch, no meta-init),
cycling through the same scenario pool used to meta-train ReSACO.

Usage:
    python scripts/train_baselines.py [--scenarios M] [--steps N] [--seed S]
"""

import argparse
import os
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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--scenarios", type=int, default=config.NUM_META_SCENARIOS)
    parser.add_argument("--steps", type=int, default=config.NUM_OUTER_ITERATIONS * config.NUM_INNER_SAC_UPDATES,
                         help="total env-interaction budget per algorithm (default matches ReSACO's K*N)")
    parser.add_argument("--a3c-workers", type=int, default=4)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--out-dir", type=str, default=os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "checkpoints"))
    args = parser.parse_args()

    os.makedirs(args.out_dir, exist_ok=True)

    seed_everything(args.seed)  # see resaco/seeding.py
    scenarios = sample_scenario_pool(args.scenarios, seed=args.seed)
    print(f"Training baselines on {len(scenarios)} scenarios, {args.steps} transitions each...")

    print("\n[1/4] Training SAC (no meta-init)...")
    sac_agent = train_sac_no_meta(scenarios, args.steps, args.seed)
    sac_path = os.path.join(args.out_dir, "sac_no_meta.pt")
    torch.save(sac_agent.get_params(), sac_path)
    print(f"  saved -> {sac_path}")

    print("\n[2/4] Training DDPG...")
    ddpg_agent = train_ddpg(scenarios, args.steps, args.seed)
    ddpg_path = os.path.join(args.out_dir, "ddpg.pt")
    torch.save(ddpg_agent.get_params(), ddpg_path)
    print(f"  saved -> {ddpg_path}")

    print("\n[3/4] Training A2C...")
    a2c_agent = train_a2c(scenarios, args.steps, args.seed)
    a2c_path = os.path.join(args.out_dir, "a2c.pt")
    torch.save(a2c_agent.get_params(), a2c_path)
    print(f"  saved -> {a2c_path}")

    print(f"\n[4/4] Training A3C ({args.a3c_workers} async workers)...")
    updates_per_worker = max(1, args.steps // (args.a3c_workers * 20))  # rollout_len=20
    a3c_agent = train_a3c(scenarios, num_workers=args.a3c_workers,
                           updates_per_worker=updates_per_worker, seed=args.seed)
    a3c_path = os.path.join(args.out_dir, "a3c.pt")
    torch.save(a3c_agent.get_params(), a3c_path)
    print(f"  saved -> {a3c_path}")

    print("\nAll baselines trained.")


if __name__ == "__main__":
    main()
