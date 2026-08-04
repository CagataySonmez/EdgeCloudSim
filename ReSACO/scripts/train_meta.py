"""Run the ReSACO Meta-Learning Phase (Algorithm 1) and save theta*.

Usage:
    python scripts/train_meta.py [--scenarios M] [--outer K] [--inner N] [--out PATH]

Defaults reproduce the paper's Section V-B-1 setup (M=10, K=300, N=50), but
these can be scaled down for a quick smoke test.
"""

import argparse
import math
import os
import sys

import torch

_RESACO_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, _RESACO_DIR)                      # ReSACO/  -> `resaco`, `bridge`
sys.path.insert(0, os.path.dirname(_RESACO_DIR))     # repo root -> `mec_core`, `baselines`

from mec_core import config
from resaco.reptile import outer_loop
from mec_core.scenario import sample_scenario_pool
from mec_core.seeding import seed_everything


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--scenarios", type=int, default=config.NUM_META_SCENARIOS)
    parser.add_argument("--outer", type=int, default=config.NUM_OUTER_ITERATIONS)
    parser.add_argument("--inner", type=int, default=config.NUM_INNER_SAC_UPDATES)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--scenario-source", type=str, default="mixed",
                         choices=["mixed", "table2", "apps", "nextgen", "sdv"],
                         help="task-profile source per scenario: 'table2' = the paper's "
                              "Table II randomized metadata, 'apps' = the four real "
                              "applications.xml profiles, 'nextgen' = the GenAI/XR/V2X/"
                              "smart-city trend workloads, 'sdv' = the ADAS/HD-map/DMS/OTA "
                              "vehicle workloads, 'mixed' (default) = all four")
    parser.add_argument("--eval-every", type=int, default=5,
                         help="run the metrics-logging eval rollout every N outer iterations "
                              "(pure logging overhead -- lower granularity trains faster; "
                              "1 evaluates every iteration like before)")
    parser.add_argument("--env-profiles", type=str, default=None,
                         help="comma-separated ENV_PROFILES names to restrict the environment "
                              "pool (e.g. 'DEFAULT' for a paper-exact run where scenarios vary "
                              "only Table II parameters, never the physical environment); "
                              "default: all profiles, weighted")
    parser.add_argument("--persist-buffers", action="store_true",
                         help="keep one replay buffer per scenario across outer iterations "
                              "instead of the paper's fresh-D-per-inner-loop (Algorithm 2): "
                              "skips the 64-step warm-up every iteration and richens "
                              "minibatches -- faster, but a documented deviation from the paper")
    parser.add_argument("--out", type=str, default=os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "checkpoints", "theta_star.pt"))
    args = parser.parse_args()

    # without this the replay buffer's global-RNG sampling and torch's
    # network init stay unseeded, so --seed alone never made a run
    # reproducible (see resaco/seeding.py)
    seed_everything(args.seed)

    os.makedirs(os.path.dirname(args.out), exist_ok=True)

    print(f"Sampling {args.scenarios} meta-training scenarios "
          f"(source={args.scenario_source} x env profile x device count range)...")
    env_names = args.env_profiles.split(",") if args.env_profiles else None
    scenarios = sample_scenario_pool(args.scenarios, seed=args.seed,
                                     source=args.scenario_source, env_names=env_names)
    for i, s in enumerate(scenarios):
        p = s.app_profile
        print(f"  S{i}: devices={s.number_of_mobile_devices} app={p.name} "
              f"env={s.env_profile.name} "
              f"interarrival={p.poisson_interarrival:.1f}s "
              f"vm_util(edge/cloud/mobile)={p.vm_utilization_on_edge:.1f}/"
              f"{p.vm_utilization_on_cloud:.2f}/{p.vm_utilization_on_mobile:.1f}")

    print(f"\nRunning Outer Loop: K={args.outer} outer iterations, "
          f"N={args.inner} inner SAC updates per iteration...")
    metrics_log = []
    theta_star = outer_loop(
        scenarios,
        num_outer_iterations=args.outer,
        num_inner_updates=args.inner,
        seed=args.seed,
        progress_every=max(1, args.outer // 15),
        metrics_log=metrics_log,
        eval_every=max(1, args.eval_every),
        persist_buffers=args.persist_buffers,
    )

    torch.save(theta_star, args.out)
    print(f"\nSaved meta-learned parameter theta* -> {args.out}")

    if metrics_log:
        metrics_path = os.path.join(os.path.dirname(args.out), "train_meta_metrics.csv")
        action_dim = len(metrics_log[0]["action_counts"])
        with open(metrics_path, "w") as f:
            headers = ["iteration", "avg_reward", "policy_entropy", "alpha"]
            headers += [f"greedy_action_{a}_count" for a in range(action_dim)]
            f.write(",".join(headers) + "\n")
            for m in metrics_log:
                row = [m["iteration"], f"{m['avg_reward']:.6f}",
                       f"{m['policy_entropy']:.6f}", f"{m['alpha']:.6f}"]
                row += m["action_counts"]
                f.write(",".join(str(v) for v in row) + "\n")
        print(f"Saved per-iteration training metrics -> {metrics_path}")

        first, last = metrics_log[0], metrics_log[-1]
        print(f"Reward trend (avg greedy reward per logged iter): "
              f"first={first['avg_reward']:.3f} last={last['avg_reward']:.3f}")
        print(f"Policy entropy: first={first['policy_entropy']:.3f} "
              f"last={last['policy_entropy']:.3f} (uniform = ln({action_dim}) = "
              f"{math.log(action_dim):.3f}); final alpha={last['alpha']:.3f}")
        distinct = sum(1 for c in last["action_counts"] if c > 0)
        if distinct <= 1:
            print("WARNING: final greedy policy used only one action across the eval rollout "
                  "-- likely collapsed; see action count columns in the metrics CSV.")


if __name__ == "__main__":
    main()
