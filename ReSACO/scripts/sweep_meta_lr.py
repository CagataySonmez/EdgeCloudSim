"""meta_lr sweep: does Reptile meta-initialization beat random initialization
at ANY outer stepsize? (Phase-1 hypothesis H1.)

Background: Eq. 8 is an EMA, so at Table II defaults (meta_lr=0.001, K=300)
74.1% of theta* is literally its random initialization
(reptile.residual_init_fraction). H1 says THAT -- not "M=10 is too little
signal" -- is why Fig. 5 doesn't reproduce. This script measures it: for
each (meta_lr, schedule, seed) it meta-trains theta*, then runs
plot_convergence.py's protocol (adaptation curves on a never-seen scenario,
meta-init vs random-init, identical env seeds) and reports

    delta_vs_random_init = mean(meta adaptation curve)
                         - mean(random adaptation curve)

i.e. the paper's Fig. 5 claim as one scalar: > 0 means the meta
initialization adapts better/faster on a new scenario. Aggregation across
seeds uses IQM + percentile-bootstrap 95% CIs (stats.py, Agarwal et al.
2021).

Judgment rule (fixed in the plan before running):
  - If ANY config's delta CI lower bound is > 0  -> H1 SUPPORTED: the
    default meta_lr was the bottleneck; README's "M=10 is not much signal"
    diagnosis must be corrected.
  - If EVERY config's CI contains 0              -> H1 REJECTED: formalize
    as a negative result ("Reptile initialization does not help in this
    problem setting"), together with Phase 2's multi-task control.

Grid (defaults): meta_lr in {0.001, 0.01, 0.1, 0.5} under the constant
schedule, plus the Reptile paper's own protocol -- linear annealing
starting at 1.0 -- as a fifth config; 5 seeds each; everything else at
paper defaults (M=10, K=300, N=50) with a fixed 20-scenario held-out eval
set. Direct function calls throughout (no subprocesses).

Outputs (appended per run, so a crash loses nothing):
    checkpoints/sweep_meta_lr.csv   one row per (meta_lr, schedule, seed)
    checkpoints/sweep_meta_lr.png   delta_vs_random_init vs meta_lr (log x),
                                    IQM + bootstrap-95%-CI band per schedule
--smoke shrinks the grid (2 seeds, K=15, 5 eval scenarios, 3 adaptation
episodes) and redirects both outputs to *_smoke.* so real sweep data is
never polluted.
"""

import argparse
import csv
import math
import os
import sys

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

_RESACO_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, _RESACO_DIR)                      # ReSACO/  -> `resaco`, `bridge`
sys.path.insert(0, os.path.dirname(_RESACO_DIR))     # repo root -> `mec_core`, `baselines`

from mec_core import config
from mec_core.scenario import sample_scenario_pool
from mec_core.seeding import seed_everything
from resaco.reptile import outer_loop, residual_init_fraction, sample_heldout_scenarios
from resaco.sac import SACAgent
from plot_convergence import run_curve
from stats import bootstrap_ci, iqm

CSV_COLUMNS = [
    "meta_lr", "schedule", "seed", "residual_random_init",
    "final_eval_reward_mean", "final_eval_reward_std",
    "final_entropy", "final_alpha", "action_entropy_of_greedy_counts",
    "delta_vs_random_init",
    # transparency extras (not part of the required schema, kept last):
    "meta_adapt_mean", "random_adapt_mean", "outer_iterations",
]


def _action_entropy(counts):
    """Entropy (nats) of the greedy-eval action histogram: ~ln(12)=2.48 for
    a uniform spread, 0.0 for a policy collapsed onto one action."""
    p = np.asarray(counts, dtype=float)
    p = p / p.sum()
    p = p[p > 0]
    return float(-(p * np.log(p)).sum() + 0.0)  # + 0.0 folds -0.0 into 0.0


def run_one(meta_lr, schedule, seed, args):
    """Meta-train one (meta_lr, schedule, seed) config, then measure its
    delta_vs_random_init on a never-seen scenario. Returns one CSV row."""
    seed_everything(seed)
    pool = sample_scenario_pool(args.scenarios, seed=seed, source=args.scenario_source)
    heldout = sample_heldout_scenarios(args.eval_scenarios, pool, seed=seed,
                                       source=args.scenario_source)

    metrics = []
    theta_star = outer_loop(
        pool,
        num_outer_iterations=args.outer,
        num_inner_updates=args.inner,
        meta_lr=meta_lr,
        seed=seed,
        progress_every=0,
        metrics_log=metrics,
        eval_every=args.eval_every,
        eval_scenarios=heldout,
        meta_lr_schedule=schedule,
    )
    last = metrics[-1]

    # the Fig.-5-style control: adapt on a scenario neither trained on nor
    # in the held-out eval set, from theta* vs from a fresh random init,
    # with identical adaptation/env seeds for a paired comparison
    test_scenario = sample_heldout_scenarios(
        1, pool + heldout, seed=seed + 424_243, source=args.scenario_source)[0]

    tag = f"lr={meta_lr:g}/{schedule}/seed={seed}"
    seed_everything(seed + 500_009)
    meta_curve = run_curve(theta_star, test_scenario, args.adapt_episodes,
                           args.inner, seed=seed * 2 + 1, label=f"meta {tag}")
    seed_everything(seed + 500_009)
    random_theta = SACAgent().get_params()
    random_curve = run_curve(random_theta, test_scenario, args.adapt_episodes,
                             args.inner, seed=seed * 2 + 1, label=f"rand {tag}")

    meta_mean = float(np.mean(meta_curve))
    random_mean = float(np.mean(random_curve))
    return {
        "meta_lr": meta_lr,
        "schedule": schedule,
        "seed": seed,
        "residual_random_init": residual_init_fraction(meta_lr, args.outer, schedule),
        "final_eval_reward_mean": last["eval_reward_mean"],
        "final_eval_reward_std": last["eval_reward_std"],
        "final_entropy": last["policy_entropy"],
        "final_alpha": last["alpha"],
        "action_entropy_of_greedy_counts": _action_entropy(last["action_counts"]),
        "delta_vs_random_init": meta_mean - random_mean,
        "meta_adapt_mean": meta_mean,
        "random_adapt_mean": random_mean,
        "outer_iterations": args.outer,
    }


def append_row(csv_path, row):
    write_header = not os.path.exists(csv_path)
    with open(csv_path, "a", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=CSV_COLUMNS)
        if write_header:
            writer.writeheader()
        writer.writerow(row)


def summarize_and_plot(csv_path, png_path):
    """Aggregates the sweep CSV per (schedule, meta_lr) with IQM +
    bootstrap 95% CI, renders the plot, and prints the H1 judgment."""
    with open(csv_path, newline="") as f:
        rows = list(csv.DictReader(f))
    if not rows:
        print("No sweep rows to summarize.")
        return

    groups = {}
    for r in rows:
        key = (r["schedule"], float(r["meta_lr"]))
        groups.setdefault(key, []).append(float(r["delta_vs_random_init"]))

    print("\n=== delta_vs_random_init per config (IQM [95% bootstrap CI], n seeds) ===")
    summary = {}
    h1_supported = []
    for (schedule, lr), deltas in sorted(groups.items()):
        point = iqm(deltas)
        lo, hi = bootstrap_ci(deltas, statistic=iqm)
        residual = float(next(r["residual_random_init"] for r in rows
                              if r["schedule"] == schedule and float(r["meta_lr"]) == lr))
        summary[(schedule, lr)] = (point, lo, hi)
        verdict = "CI > 0" if lo > 0 else ("CI < 0" if hi < 0 else "CI spans 0")
        print(f"  {schedule:9s} meta_lr={lr:<7g} residual_init={residual:7.3g}  "
              f"delta={point:+.4f} [{lo:+.4f}, {hi:+.4f}] (n={len(deltas)}) -> {verdict}")
        if lo > 0:
            h1_supported.append((schedule, lr))

    print("\n=== H1 judgment (rule fixed before the sweep ran) ===")
    if h1_supported:
        cfgs = ", ".join(f"{s}/meta_lr={lr:g}" for s, lr in h1_supported)
        print(f"H1 SUPPORTED: meta-init beats random-init with CI clear of zero at: {cfgs}.")
        print("The README diagnosis 'M=10 scenarios is not much signal' is wrong for those "
              "configs -- the default meta_lr was the bottleneck. Update README (P1-4).")
    else:
        print("H1 NOT SUPPORTED at any tested meta_lr: every config's 95% CI contains 0.")
        print("Formalize as a negative result together with Phase 2's multi-task control.")

    # ---- plot: constant schedule as a line over meta_lr, linear as a point ----
    plt.figure(figsize=(8, 5), dpi=150)
    colors = {"constant": "#2b83ba", "linear": "#d7191c"}
    for schedule in sorted({s for s, _ in summary}):
        lrs = sorted(lr for s, lr in summary if s == schedule)
        pts = np.array([summary[(schedule, lr)][0] for lr in lrs])
        los = np.array([summary[(schedule, lr)][1] for lr in lrs])
        his = np.array([summary[(schedule, lr)][2] for lr in lrs])
        color = colors.get(schedule, None)
        if len(lrs) > 1:
            plt.plot(lrs, pts, "o-", label=f"{schedule} (IQM)", color=color)
            plt.fill_between(lrs, los, his, alpha=0.2, color=color,
                             label=f"{schedule} 95% CI")
        else:
            plt.errorbar(lrs, pts, yerr=[pts - los, his - pts], fmt="D",
                         capsize=5, color=color,
                         label=f"{schedule} @ start lr={lrs[0]:g} (IQM, 95% CI)")
    plt.axhline(0.0, color="gray", linestyle="--", linewidth=1,
                label="no advantage over random init")
    plt.xscale("log")
    plt.xlabel("meta_lr (Reptile outer stepsize, log scale)")
    plt.ylabel("delta_vs_random_init (mean adaptation reward)")
    plt.title("Does meta-initialization beat random initialization? (H1 sweep)")
    plt.legend(loc="best", fontsize=8)
    plt.grid(True, linestyle="--", alpha=0.5)
    plt.tight_layout()
    plt.savefig(png_path, bbox_inches="tight")
    print(f"\nSaved sweep plot -> {png_path}")


def main():
    checkpoints = os.path.join(_RESACO_DIR, "checkpoints")
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--meta-lrs", type=float, nargs="+",
                        default=[0.001, 0.01, 0.1, 0.5],
                        help="constant-schedule grid (default: 0.001 0.01 0.1 0.5)")
    parser.add_argument("--linear-start", type=float, default=1.0,
                        help="starting stepsize of the linear-annealing config "
                             "(the Reptile paper's protocol; default 1.0). "
                             "Set to 0 to skip the linear config entirely.")
    parser.add_argument("--seeds", type=int, nargs="+", default=[0, 1, 2, 3, 4])
    parser.add_argument("--scenarios", type=int, default=config.NUM_META_SCENARIOS)
    parser.add_argument("--outer", type=int, default=config.NUM_OUTER_ITERATIONS)
    parser.add_argument("--inner", type=int, default=config.NUM_INNER_SAC_UPDATES)
    parser.add_argument("--eval-scenarios", type=int, default=20)
    parser.add_argument("--eval-every", type=int, default=5)
    parser.add_argument("--adapt-episodes", type=int, default=25,
                        help="episodes of the meta-vs-random adaptation control "
                             "(each = --inner SAC updates); delta averages the "
                             "whole curve, so this window captures early-adaptation "
                             "speed, which is the paper's actual claim")
    parser.add_argument("--scenario-source", type=str, default="mixed",
                        choices=["mixed", "table2", "apps", "nextgen", "sdv"])
    parser.add_argument("--out-csv", type=str, default=os.path.join(checkpoints, "sweep_meta_lr.csv"))
    parser.add_argument("--out-png", type=str, default=os.path.join(checkpoints, "sweep_meta_lr.png"))
    parser.add_argument("--smoke", action="store_true",
                        help="shrunken sanity run (2 seeds, K=15, 5 eval scenarios, "
                             "3 adaptation episodes) writing to *_smoke.csv/.png so "
                             "real sweep data is never mixed with smoke rows")
    args = parser.parse_args()

    if args.smoke:
        args.seeds = args.seeds[:2]
        args.outer = min(args.outer, 15)
        args.eval_scenarios = min(args.eval_scenarios, 5)
        args.adapt_episodes = min(args.adapt_episodes, 3)
        for attr in ("out_csv", "out_png"):
            root, ext = os.path.splitext(getattr(args, attr))
            if not root.endswith("_smoke"):
                setattr(args, attr, f"{root}_smoke{ext}")

    configs = [(lr, "constant") for lr in args.meta_lrs]
    if args.linear_start > 0:
        configs.append((args.linear_start, "linear"))

    os.makedirs(os.path.dirname(args.out_csv), exist_ok=True)
    total = len(configs) * len(args.seeds)
    done = 0
    for meta_lr, schedule in configs:
        for seed in args.seeds:
            done += 1
            print(f"\n[{done}/{total}] meta_lr={meta_lr:g} schedule={schedule} seed={seed} "
                  f"(K={args.outer}, N={args.inner}, M={args.scenarios})")
            row = run_one(meta_lr, schedule, seed, args)
            append_row(args.out_csv, row)
            print(f"  -> delta_vs_random_init={row['delta_vs_random_init']:+.4f} "
                  f"(meta {row['meta_adapt_mean']:.3f} vs random {row['random_adapt_mean']:.3f}), "
                  f"greedy action entropy={row['action_entropy_of_greedy_counts']:.3f}")

    summarize_and_plot(args.out_csv, args.out_png)


if __name__ == "__main__":
    main()
