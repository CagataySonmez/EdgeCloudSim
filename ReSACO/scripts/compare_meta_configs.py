"""Which meta-training configuration adapts best -- and fastest?

Measures the paper's Fig. 5 claim (meta-initialization adapts to a new
scenario better/faster than random initialization) for several
meta-training configurations, using exactly sweep_meta_lr.py's protocol:
per seed, meta-train theta*, then run adaptation curves on a never-seen
test scenario from theta* and from a paired random init (identical
adaptation/env random streams), and report

    delta_vs_random_init = mean(meta curve) - mean(random curve)
    early_delta          = the same over only the first EARLY_EPISODES
                           episodes ("fast adaptation" proper)

plus the final held-out eval reward and the meta-training wall-clock.
Every config is scored on the SAME held-out set and test scenario per seed
(both drawn from the standard preset-environment distribution, so the
continuous-environment config is not evaluated on its own training
distribution), and the random arm is computed once per seed and shared.

Configs (M=10, N=50 throughout):
    paper           Table II exactly: meta_lr 0.001 constant, B=1, K=300
    h1              Reptile's own schedule (linear from 1.0), B=1, K=300 --
                    the H1 sweep's winner
    batch-eqbudget  h1 + batched Reptile B=4 at K=75: the SAME env budget
                    as h1, run on 4 workers (~1/4 the wall-clock)
    batch           h1 + B=4 at K=300 (4x the env budget, 4 workers)
    batch-envrand   batch + 30% continuously randomized environments

Pre-registered rule for train_meta.py's `--preset fast` (fixed before the
first full run):
    1. candidates = configs whose delta_vs_random_init 95% CI lower bound > 0
    2. best = the candidate with the highest IQM delta
    3. among candidates whose CI overlaps best's CI (statistically
       indistinguishable from it), pick the lowest mean wall-clock
    4. if there are no candidates, fall back to h1 (the already-measured
       H1-sweep winner)

Outputs (appended per run; reruns dedup on (config, seed), keeping the last):
    checkpoints/compare_meta_configs.csv   one row per (config, seed)
    checkpoints/compare_meta_configs.md    summary table + the rule's verdict
--smoke shrinks everything (2 seeds, K/20, 5 eval scenarios, 3 adaptation
episodes) and writes *_smoke.* files.
"""

import argparse
import csv
import os
import sys
import time

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

CONFIGS = {
    "paper": dict(meta_lr=config.META_LR, schedule="constant", batch=1, outer=300,
                  workers=1, env_randomization=0.0),
    "h1": dict(meta_lr=1.0, schedule="linear", batch=1, outer=300,
               workers=1, env_randomization=0.0),
    "batch-eqbudget": dict(meta_lr=1.0, schedule="linear", batch=4, outer=75,
                           workers=4, env_randomization=0.0),
    "batch": dict(meta_lr=1.0, schedule="linear", batch=4, outer=300,
                  workers=4, env_randomization=0.0),
    "batch-envrand": dict(meta_lr=1.0, schedule="linear", batch=4, outer=300,
                          workers=4, env_randomization=0.3),
}
FALLBACK_CONFIG = "h1"
EARLY_EPISODES = 5

CSV_COLUMNS = [
    "config", "seed", "meta_lr", "schedule", "meta_batch", "outer_iterations",
    "workers", "env_randomization", "residual_random_init",
    "delta_vs_random_init", "early_delta", "meta_adapt_mean", "random_adapt_mean",
    "final_eval_reward_mean", "final_entropy", "meta_train_seconds",
    "env_interactions",
    # provenance -- only rows matching the current invocation are aggregated
    "inner_updates", "meta_scenarios", "adapt_episodes", "eval_scenarios",
    "scenario_source", "eval_every",
]
PROVENANCE = {
    "inner_updates": "inner",
    "meta_scenarios": "scenarios",
    "adapt_episodes": "adapt_episodes",
    "eval_scenarios": "eval_scenarios",
    "scenario_source": "scenario_source",
    "eval_every": "eval_every",
}


def shared_eval_setup(seed, args):
    """Held-out set + test scenario for this seed -- identical for every
    config (drawn against the standard preset-environment pool), and for
    the paper/h1 configs identical to what sweep_meta_lr.py evaluates."""
    standard_pool = sample_scenario_pool(args.scenarios, seed=seed, source=args.scenario_source)
    heldout = sample_heldout_scenarios(args.eval_scenarios, standard_pool, seed=seed,
                                       source=args.scenario_source)
    test_scenario = sample_heldout_scenarios(
        1, standard_pool + heldout, seed=seed + 424_243, source=args.scenario_source)[0]
    return heldout, test_scenario


def random_arm(seed, test_scenario, args):
    """The paired random-init adaptation curve (sweep_meta_lr.run_one's
    exact stream layout). Depends only on the seed, so it is shared by
    every config."""
    seed_everything(seed + 700_003)
    random_theta = SACAgent().get_params()
    seed_everything(seed + 500_009)
    return run_curve(random_theta, test_scenario, args.adapt_episodes, args.inner,
                     seed=1_000_000 + seed * 100_000, label=f"rand seed={seed}")


def run_one(name, cfg, seed, heldout, test_scenario, random_curve, args):
    outer = cfg["outer"]
    seed_everything(seed)
    pool = sample_scenario_pool(args.scenarios, seed=seed, source=args.scenario_source,
                                env_randomization=cfg["env_randomization"])
    overlap = sum(s in pool for s in heldout + [test_scenario])
    if overlap:
        print(f"  WARNING: {overlap} eval scenario(s) also occur in {name}'s training pool")

    metrics = []
    started = time.perf_counter()
    theta_star = outer_loop(
        pool, num_outer_iterations=outer, num_inner_updates=args.inner,
        meta_lr=cfg["meta_lr"], seed=seed, progress_every=0, metrics_log=metrics,
        eval_every=args.eval_every, eval_scenarios=heldout,
        meta_lr_schedule=cfg["schedule"], meta_batch_size=cfg["batch"],
        num_workers=cfg["workers"],
    )
    seconds = time.perf_counter() - started
    last = metrics[-1]

    seed_everything(seed + 500_009)
    meta_curve = run_curve(theta_star, test_scenario, args.adapt_episodes, args.inner,
                           seed=1_000_000 + seed * 100_000, label=f"meta {name} seed={seed}")

    early = min(EARLY_EPISODES, args.adapt_episodes)
    return {
        "config": name,
        "seed": seed,
        "meta_lr": cfg["meta_lr"],
        "schedule": cfg["schedule"],
        "meta_batch": cfg["batch"],
        "outer_iterations": outer,
        "workers": cfg["workers"],
        "env_randomization": cfg["env_randomization"],
        "residual_random_init": residual_init_fraction(cfg["meta_lr"], outer, cfg["schedule"]),
        "delta_vs_random_init": float(np.mean(meta_curve) - np.mean(random_curve)),
        "early_delta": float(np.mean(meta_curve[:early]) - np.mean(random_curve[:early])),
        "meta_adapt_mean": float(np.mean(meta_curve)),
        "random_adapt_mean": float(np.mean(random_curve)),
        "final_eval_reward_mean": last["eval_reward_mean"],
        "final_entropy": last["policy_entropy"],
        "meta_train_seconds": seconds,
        # fresh-buffer accounting: every inner loop pays warm-up + N steps
        "env_interactions": outer * cfg["batch"] * (config.BATCH_SIZE + args.inner),
        "inner_updates": args.inner,
        "meta_scenarios": args.scenarios,
        "adapt_episodes": args.adapt_episodes,
        "eval_scenarios": args.eval_scenarios,
        "scenario_source": args.scenario_source,
        "eval_every": args.eval_every,
    }


def append_row(path, row):
    write_header = not os.path.exists(path)
    with open(path, "a", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=CSV_COLUMNS)
        if write_header:
            writer.writeheader()
        writer.writerow(row)


def _ci_str(values):
    point = iqm(values)
    lo, hi = bootstrap_ci(values, statistic=iqm)
    return point, lo, hi, f"{point:+.3f} [{lo:+.3f}, {hi:+.3f}]"


def summarize(csv_path, md_path, args, smoke=False):
    with open(csv_path, newline="") as f:
        raw = list(csv.DictReader(f))
    matching = [r for r in raw
                if all(r.get(col) == str(getattr(args, attr)) for col, attr in PROVENANCE.items())]
    rows = {}
    for r in matching:  # keep-last dedup on (config, seed)
        rows[(r["config"], int(r["seed"]))] = r
    if not rows:
        print("No rows match this invocation's parameters -- nothing to summarize.")
        return None

    groups = {}
    for (name, _seed), r in sorted(rows.items()):
        groups.setdefault(name, []).append(r)

    table, stats = [], {}
    for name in [n for n in CONFIGS if n in groups] + sorted(set(groups) - set(CONFIGS)):
        rs = groups[name]
        delta = [float(r["delta_vs_random_init"]) for r in rs]
        early = [float(r["early_delta"]) for r in rs]
        heldout = [float(r["final_eval_reward_mean"]) for r in rs]
        secs = float(np.mean([float(r["meta_train_seconds"]) for r in rs]))
        d_pt, d_lo, d_hi, d_s = _ci_str(delta)
        _, _, _, e_s = _ci_str(early)
        _, _, _, h_s = _ci_str(heldout)
        stats[name] = (d_pt, d_lo, d_hi, secs)
        r0 = rs[0]
        table.append(
            f"| {name} | {r0['schedule']} {float(r0['meta_lr']):g} | {r0['meta_batch']} | "
            f"{r0['outer_iterations']} | {float(r0['env_randomization']):g} | "
            f"{int(r0['env_interactions']):,} | {d_s} | {e_s} | {h_s} | {secs:.0f}s | {len(rs)} |")

    # ---- the pre-registered rule ----
    candidates = {n: s for n, s in stats.items() if s[1] > 0}
    if candidates:
        best = max(candidates, key=lambda n: candidates[n][0])
        b_lo, b_hi = candidates[best][1], candidates[best][2]
        tied = [n for n, s in candidates.items() if s[1] <= b_hi and s[2] >= b_lo]
        choice = min(tied, key=lambda n: candidates[n][3])
        verdict = (f"Candidates (delta CI > 0): {', '.join(sorted(candidates))}. "
                   f"Highest IQM: {best}. Statistically tied with it: "
                   f"{', '.join(sorted(tied))}. Lowest wall-clock among those: **{choice}**.")
    else:
        choice = FALLBACK_CONFIG
        verdict = (f"No config's delta CI clears 0 -> fallback to **{FALLBACK_CONFIG}** "
                   f"(the H1-sweep winner).")

    header = (
        "| config | meta_lr | B | K | env rand | env interactions | delta vs random init "
        "IQM [95% CI] | early delta (first "
        f"{min(EARLY_EPISODES, args.adapt_episodes)} ep.) | final held-out reward | "
        "meta-train wall-clock | n |\n"
        "|---|---|---|---|---|---|---|---|---|---|---|")
    lines = [
        "# Meta-training configuration comparison" + (" (SMOKE -- not a verdict)" if smoke else ""),
        "",
        f"M={args.scenarios}, N={args.inner}, {args.adapt_episodes} adaptation episodes, "
        f"{args.eval_scenarios} held-out scenarios, scenario source {args.scenario_source}. "
        "Generated by scripts/compare_meta_configs.py.",
        "",
        header,
        *table,
        "",
        "Pre-registered `--preset fast` rule: " + verdict,
        "",
    ]
    with open(md_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print("\n".join(lines))
    print(f"Saved summary -> {md_path}")
    return choice


def main():
    checkpoints = os.path.join(_RESACO_DIR, "checkpoints")
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--configs", nargs="+", default=list(CONFIGS), choices=list(CONFIGS))
    parser.add_argument("--seeds", type=int, nargs="+", default=[0, 1, 2, 3, 4])
    parser.add_argument("--scenarios", type=int, default=config.NUM_META_SCENARIOS)
    parser.add_argument("--inner", type=int, default=config.NUM_INNER_SAC_UPDATES)
    parser.add_argument("--eval-scenarios", type=int, default=20)
    parser.add_argument("--eval-every", type=int, default=5)
    parser.add_argument("--adapt-episodes", type=int, default=25)
    parser.add_argument("--scenario-source", type=str, default="mixed",
                        choices=["mixed", "table2", "apps", "nextgen", "sdv"])
    parser.add_argument("--out-csv", type=str,
                        default=os.path.join(checkpoints, "compare_meta_configs.csv"))
    parser.add_argument("--out-md", type=str,
                        default=os.path.join(checkpoints, "compare_meta_configs.md"))
    parser.add_argument("--summarize-only", action="store_true",
                        help="skip training; re-summarize the existing CSV")
    parser.add_argument("--smoke", action="store_true")
    args = parser.parse_args()

    configs = {n: dict(CONFIGS[n]) for n in args.configs}
    if args.smoke:
        args.seeds = args.seeds[:2]
        args.eval_scenarios = min(args.eval_scenarios, 5)
        args.adapt_episodes = min(args.adapt_episodes, 3)
        args.eval_every = 1
        for cfg in configs.values():
            cfg["outer"] = max(2, cfg["outer"] // 20)
        for attr in ("out_csv", "out_md"):
            root, ext = os.path.splitext(getattr(args, attr))
            if not root.endswith("_smoke"):
                setattr(args, attr, f"{root}_smoke{ext}")
    for name, cfg in configs.items():
        if cfg["outer"] % args.eval_every != 0:
            parser.error(f"--eval-every {args.eval_every} must divide {name}'s K={cfg['outer']} "
                         f"(the final metrics row must be iteration K)")

    os.makedirs(os.path.dirname(args.out_csv), exist_ok=True)
    if not args.summarize_only:
        total = len(configs) * len(args.seeds)
        done = 0
        for seed in args.seeds:
            heldout, test_scenario = shared_eval_setup(seed, args)
            print(f"\n=== seed {seed}: random-init control curve ===")
            random_curve = random_arm(seed, test_scenario, args)
            for name, cfg in configs.items():
                done += 1
                print(f"\n[{done}/{total}] {name} seed={seed} {cfg}")
                row = run_one(name, cfg, seed, heldout, test_scenario, random_curve, args)
                append_row(args.out_csv, row)
                print(f"  -> delta={row['delta_vs_random_init']:+.4f} early={row['early_delta']:+.4f} "
                      f"heldout={row['final_eval_reward_mean']:.3f} "
                      f"wall-clock={row['meta_train_seconds']:.1f}s")

    summarize(args.out_csv, args.out_md, args, smoke=args.smoke)


if __name__ == "__main__":
    main()
