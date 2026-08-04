"""Reproduce Fig. 5 of the ReSACO paper (Section V-B "Convergence"):
compares how quickly the Deployment Phase (Algorithm 4) adapts to a new,
previously-unseen scenario starting from two different initializations:

  - meta-initialization: theta_adapt starts from the meta-learned theta*
    produced by scripts/train_meta.py (Algorithm 1).
  - random initialization: theta_adapt starts from a freshly, randomly
    initialized SAC agent.

Both are then refined identically for K=300 "episodes" (each episode is
one Outer-Loop-style round of N=50 SAC-Update steps, matching the paper's
"each episode represents one Outer Loop iteration"), continually adapting
to the *same* new scenario across episodes, with the average greedy reward
evaluated after every episode. The paper's expectation: meta-init starts
higher and converges within a handful of episodes; random-init starts much
lower and needs far more episodes (and is noisier along the way).

Usage:
    python scripts/plot_convergence.py [--episodes 300] [--inner 50] [--seed 999]
"""

import argparse
import os
import sys

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

_RESACO_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, _RESACO_DIR)                      # ReSACO/  -> `resaco`, `bridge`
sys.path.insert(0, os.path.dirname(_RESACO_DIR))     # repo root -> `mec_core`, `baselines`

from mec_core import config
from resaco.reptile import inner_loop, _evaluate
from resaco.sac import SACAgent
from mec_core.scenario import sample_scenario
from mec_core.seeding import seed_everything


def run_curve(theta, scenario, num_episodes, num_inner_updates, seed, label):
    rng_seed = seed
    rewards = []
    for episode in range(1, num_episodes + 1):
        theta = inner_loop(theta, scenario, num_inner_updates, seed=rng_seed)
        rng_seed += 1

        eval_agent = SACAgent()
        eval_agent.load_params(theta)
        avg_reward, _, _ = _evaluate(eval_agent, scenario, seed=rng_seed)
        rewards.append(avg_reward)
        rng_seed += 1

        if episode % max(1, num_episodes // 10) == 0:
            print(f"  [{label}] episode {episode}/{num_episodes} avg_reward={avg_reward:.3f}")
    return rewards


def normalize(*curves):
    """Min-max normalize all curves onto a shared [0, 1] scale, matching
    the paper's "Normalized Reward" y-axis."""
    all_values = [v for curve in curves for v in curve]
    lo, hi = min(all_values), max(all_values)
    span = (hi - lo) or 1.0
    return [[(v - lo) / span for v in curve] for curve in curves]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--episodes", type=int, default=300)
    parser.add_argument("--inner", type=int, default=config.NUM_INNER_SAC_UPDATES)
    parser.add_argument("--seed", type=int, default=999,
                         help="seed for the new test scenario (kept out of train_meta.py's training pool)")
    parser.add_argument("--scenario-source", type=str, default="mixed",
                         choices=["mixed", "table2", "apps", "nextgen", "sdv"],
                         help="how the held-out test scenario's task profile is drawn; "
                              "'table2' matches the paper's own metadata construction")
    parser.add_argument("--theta", type=str, default=os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "checkpoints", "theta_star.pt"))
    parser.add_argument("--out", type=str, default=os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "checkpoints", "convergence.png"))
    args = parser.parse_args()

    import torch

    seed_everything(args.seed)  # see resaco/seeding.py

    if not os.path.exists(args.theta):
        print(f"ERROR: {args.theta} not found. Run scripts/train_meta.py first.")
        sys.exit(1)
    theta_star = torch.load(args.theta, map_location="cpu")

    test_scenario = sample_scenario(__import__("random").Random(args.seed),
                                    source=args.scenario_source)
    p = test_scenario.app_profile
    print(f"Test scenario (held out, seed={args.seed}): devices={test_scenario.number_of_mobile_devices} "
          f"app={p.name} interarrival={p.poisson_interarrival:.1f}s "
          f"vm_util(edge/cloud/mobile)={p.vm_utilization_on_edge:.1f}/"
          f"{p.vm_utilization_on_cloud:.2f}/{p.vm_utilization_on_mobile:.1f}")

    print(f"\nRunning meta-initialization curve ({args.episodes} episodes x {args.inner} SAC updates)...")
    meta_rewards = run_curve(theta_star, test_scenario, args.episodes, args.inner,
                              seed=args.seed * 2, label="meta-init")

    print(f"\nRunning random-initialization curve ({args.episodes} episodes x {args.inner} SAC updates)...")
    random_theta = SACAgent().get_params()
    random_rewards = run_curve(random_theta, test_scenario, args.episodes, args.inner,
                                seed=args.seed * 3, label="random-init")

    norm_meta, norm_random = normalize(meta_rewards, random_rewards)

    episodes = list(range(1, args.episodes + 1))
    plt.figure(figsize=(8, 5), dpi=150)
    plt.plot(episodes, norm_meta, label="Meta Initialization", color="#2b83ba", linewidth=1.5)
    plt.plot(episodes, norm_random, label="Random Initialization", color="#fdae61", linewidth=1.5)
    plt.xlabel("Episode")
    plt.ylabel("Normalized Reward")
    plt.title("Convergence Comparison between Meta-Initialization and Random Initialization")
    plt.legend(loc="lower right")
    plt.grid(True, linestyle="--", alpha=0.5)
    plt.tight_layout()

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    plt.savefig(args.out, bbox_inches="tight")
    print(f"\nSaved convergence plot -> {args.out}")

    csv_path = os.path.splitext(args.out)[0] + ".csv"
    with open(csv_path, "w") as f:
        f.write("episode,meta_init_reward,meta_init_normalized,random_init_reward,random_init_normalized\n")
        for i, ep in enumerate(episodes):
            f.write(f"{ep},{meta_rewards[i]},{norm_meta[i]},{random_rewards[i]},{norm_random[i]}\n")
    print(f"Saved convergence data -> {csv_path}")


if __name__ == "__main__":
    main()
