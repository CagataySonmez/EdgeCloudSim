"""One-factor-at-a-time ablation grid over the repo's feature switches
(P2-3): every axis is toggled ALONE against the base configuration, so
each row answers "what does this one switch buy?".

Axes (base value first):
    AUTO_ENTROPY_TUNING        on / off (fixed tau=0.2)
    DELAY_SENSITIVITY_REWARD   on / off
    ENERGY_AWARE_REWARD        on / off
    COST_AWARE_REWARD          on / off
    SLA_PENALTY_WEIGHT         1.0 / 0.0
    --scenario-source          mixed / table2 / apps / nextgen / sdv
    --persist-buffers          off / on
    per-host edge routing      Java-side toggle -- CANNOT be run from this
                               script; ablations.md carries an explicit
                               "manual" row so the axis is never silently
                               dropped.

Protocol per (variant, seed): meta-train theta* with the variant applied
(config flags via config.override_config, source/persist via outer_loop
arguments), then evaluate theta* greedily on a FIXED 20-scenario held-out
set **under the BASE reward definition** -- reward-shaping axes change
the reward function itself, so evaluating each variant under its own
reward would measure the definition change, not the performance change.
Training uses the variant's reward; the scoreboard is common to all.

delta_vs_base pairs by seed (variant eval - base eval, same seed), and
all aggregation is IQM + percentile-bootstrap 95% CI (stats.py).

Default stepsize is the H1 sweep's winner (--meta-lr 1.0
--meta-lr-schedule linear; see README "Convergence"): at the paper's
default meta_lr=0.001, theta* is ~74% random init and every ablation
delta would drown in that noise. `--meta-lr 0.001 --meta-lr-schedule
constant` reproduces the paper-default axis instead. The stepsize used is
recorded in every CSV row.

Outputs (rows appended crash-safely; the summary dedups (axis, setting,
seed) keep-last and aggregates only provenance-matching rows):
    checkpoints/ablations.csv   raw per-(variant, seed) rows
    checkpoints/ablations.md    paste-ready markdown table
--smoke shrinks to 2 seeds / K=15 / 5 eval scenarios and writes *_smoke.*
"""

import argparse
import csv
import os
import random
import sys

import numpy as np

_RESACO_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, _RESACO_DIR)                      # ReSACO/  -> `resaco`, `bridge`
sys.path.insert(0, os.path.dirname(_RESACO_DIR))     # repo root -> `mec_core`, `baselines`

from mec_core import config
from mec_core.scenario import sample_scenario_pool
from mec_core.seeding import seed_everything
from resaco.reptile import _evaluate, outer_loop, sample_heldout_scenarios
from resaco.sac import SACAgent
from stats import bootstrap_ci, iqm

# (axis, setting-label, config overrides, outer_loop kwargs, scenario source)
BASE = ("BASE", "all defaults", {}, {}, None)
VARIANTS = [
    ("AUTO_ENTROPY_TUNING", "off (fixed tau=0.2)", {"AUTO_ENTROPY_TUNING": False}, {}, None),
    ("DELAY_SENSITIVITY_REWARD", "off", {"DELAY_SENSITIVITY_REWARD": False}, {}, None),
    ("ENERGY_AWARE_REWARD", "off", {"ENERGY_AWARE_REWARD": False}, {}, None),
    ("COST_AWARE_REWARD", "off", {"COST_AWARE_REWARD": False}, {}, None),
    ("SLA_PENALTY_WEIGHT", "0.0", {"SLA_PENALTY_WEIGHT": 0.0}, {}, None),
    ("scenario_source", "table2", {}, {}, "table2"),
    ("scenario_source", "apps", {}, {}, "apps"),
    ("scenario_source", "nextgen", {}, {}, "nextgen"),
    ("scenario_source", "sdv", {}, {}, "sdv"),
    ("persist_buffers", "on", {}, {"persist_buffers": True}, None),
]

JAVA_MANUAL_AXIS = (
    "per-host edge routing", "on / off",
    "manual: Java-side toggle (route edge action e to host e-1 vs. generic "
    "least-loaded dispatch in ReSACOEdgeOrchestrator); run scripts/ReSACO "
    "with and without the preferred-host block to measure")

CSV_COLUMNS = [
    "axis", "setting", "seed",
    "eval_reward_mean", "eval_reward_std", "delta_vs_base",
    "final_entropy", "final_alpha", "action_entropy_of_greedy_counts",
    # provenance -- the summary aggregates only rows matching the invocation.
    # eval_every belongs here because outer_loop draws its periodic-eval seed
    # from the TRAINING rng, so a different eval cadence trains a different
    # theta*, not just a different log.
    "outer_iterations", "inner_updates", "meta_scenarios", "eval_scenarios",
    "eval_every", "meta_lr", "meta_lr_schedule", "base_source",
]

PROVENANCE = {
    "outer_iterations": "outer",
    "inner_updates": "inner",
    "meta_scenarios": "scenarios",
    "eval_scenarios": "eval_scenarios",
    "eval_every": "eval_every",
    "meta_lr": "meta_lr",
    "meta_lr_schedule": "meta_lr_schedule",
    "base_source": "base_source",
}


def _action_entropy(counts):
    p = np.asarray(counts, dtype=float)
    p = p / p.sum()
    p = p[p > 0]
    return float(-(p * np.log(p)).sum() + 0.0)


def _heldout_eval_under_base_reward(theta, heldout, heldout_seeds):
    """Greedy eval of theta on the fixed held-out set OUTSIDE any config
    override, i.e. under the base reward definition -- the common
    scoreboard that makes reward-shaping ablations comparable at all."""
    agent = SACAgent()
    agent.load_params(theta)
    rewards = [_evaluate(agent, s, seed=es)[0]
               for s, es in zip(heldout, heldout_seeds)]
    return float(np.mean(rewards)), float(np.std(rewards))


def run_variant(axis, setting, overrides, outer_kwargs, source, seed, args):
    """Trains one variant at one seed and evaluates it on the common
    held-out set. Returns the CSV row (without delta_vs_base)."""
    train_source = source or args.base_source

    seed_everything(seed)
    pool = sample_scenario_pool(args.scenarios, seed=seed, source=train_source)
    # The scoreboard: held-out scenarios drawn from the BASE source with a
    # seed-only stream, so every variant at this seed is judged on the same
    # conditions (the disjointness rejection is against this variant's own
    # pool; with the mixed source's continuous sampling, collisions -- and
    # therefore realized-set divergence between variants -- are negligible).
    heldout = sample_heldout_scenarios(args.eval_scenarios, pool, seed=seed,
                                       source=args.base_source)
    heldout_rng = random.Random(seed + 200_003)
    heldout_seeds = [heldout_rng.randint(0, 2**31) for _ in heldout]

    metrics = []
    with config.override_config(**overrides):
        theta = outer_loop(
            pool,
            num_outer_iterations=args.outer,
            num_inner_updates=args.inner,
            meta_lr=args.meta_lr,
            seed=seed,
            progress_every=0,
            metrics_log=metrics,
            eval_every=args.eval_every,
            meta_lr_schedule=args.meta_lr_schedule,
            **outer_kwargs,
        )
    eval_mean, eval_std = _heldout_eval_under_base_reward(theta, heldout, heldout_seeds)
    last = metrics[-1]
    return {
        "axis": axis,
        "setting": setting,
        "seed": seed,
        "eval_reward_mean": eval_mean,
        "eval_reward_std": eval_std,
        "final_entropy": last["policy_entropy"],
        "final_alpha": last["alpha"],
        "action_entropy_of_greedy_counts": _action_entropy(last["action_counts"]),
        "outer_iterations": args.outer,
        "inner_updates": args.inner,
        "meta_scenarios": args.scenarios,
        "eval_scenarios": args.eval_scenarios,
        "eval_every": args.eval_every,
        "meta_lr": args.meta_lr,
        "meta_lr_schedule": args.meta_lr_schedule,
        "base_source": args.base_source,
    }


def append_row(csv_path, row):
    write_header = not os.path.exists(csv_path)
    with open(csv_path, "a", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=CSV_COLUMNS)
        if write_header:
            writer.writeheader()
        writer.writerow(row)


def summarize(csv_path, md_path, args):
    with open(csv_path, newline="") as f:
        raw_rows = list(csv.DictReader(f))

    matching = [r for r in raw_rows
                if all(r.get(col) == str(getattr(args, attr))
                       for col, attr in PROVENANCE.items())]
    deduped = {}
    for r in matching:  # keep-last on (axis, setting, seed)
        deduped[(r["axis"], r["setting"], int(r["seed"]))] = r
    skipped = len(raw_rows) - len(deduped)
    if skipped:
        print(f"note: {skipped} row(s) excluded as parameter-different or duplicate; "
              f"aggregating {len(deduped)}.")

    # Deltas are RECOMPUTED here from the surviving rows -- pairing each
    # variant seed against the deduped BASE row of the same seed -- rather
    # than trusting the delta_vs_base column, which on a rerun could still
    # reference a BASE value the keep-last dedup has since discarded.
    base_by_seed = {}
    for (axis, _setting, seed), r in deduped.items():
        if axis == "BASE":
            base_by_seed[seed] = float(r["eval_reward_mean"])

    groups = {}
    for (axis, setting, _seed), r in sorted(deduped.items()):
        groups.setdefault((axis, setting), []).append(r)

    def agg(values):
        point = iqm(values)
        lo, hi = bootstrap_ci(values, statistic=iqm)
        return point, lo, hi

    lines = [
        "# One-factor-at-a-time ablations",
        "",
        f"Protocol: meta-train with the variant applied, evaluate greedily on a "
        f"fixed {args.eval_scenarios}-scenario held-out set **under the base "
        f"reward definition** (common scoreboard; reward-shaping variants train "
        f"on their own reward but are judged on the base one). "
        f"K={args.outer}, N={args.inner}, M={args.scenarios}, "
        f"meta_lr={args.meta_lr:g} ({args.meta_lr_schedule}), "
        f"base source={args.base_source}. delta pairs by seed against BASE; "
        f"IQM with percentile-bootstrap 95% CIs.",
        "",
        "| axis | setting | held-out eval IQM [95% CI] | delta vs base IQM [95% CI] | n |",
        "|---|---|---|---|---|",
    ]
    print("\n=== Ablations (held-out eval under base reward; IQM [95% CI]) ===")
    for (axis, setting), rs in sorted(groups.items(), key=lambda kv: (kv[0][0] != "BASE", kv[0])):
        evals = [float(r["eval_reward_mean"]) for r in rs]
        e_pt, e_lo, e_hi = agg(evals)
        deltas, unpaired = [], 0
        if axis != "BASE":
            for r in rs:
                s = int(r["seed"])
                if s in base_by_seed:
                    deltas.append(float(r["eval_reward_mean"]) - base_by_seed[s])
                else:
                    unpaired += 1
        if axis == "BASE" or not deltas:
            d_cell = "--"
            d_txt = ""
        else:
            d_pt, d_lo, d_hi = agg(deltas)
            d_cell = f"{d_pt:+.3f} [{d_lo:+.3f}, {d_hi:+.3f}]"
            d_txt = f"  delta={d_cell}"
        if unpaired:
            d_txt += f"  ({unpaired} seed(s) had no BASE row and were left out of delta)"
        e_cell = f"{e_pt:.3f} [{e_lo:.3f}, {e_hi:.3f}]"
        print(f"  {axis:26s} {setting:20s} eval={e_cell}{d_txt} (n={len(rs)})")
        lines.append(f"| {axis} | {setting} | {e_cell} | {d_cell} | {len(rs)} |")

    axis_name, values, note = JAVA_MANUAL_AXIS
    lines.append(f"| {axis_name} | {values} | not run from this script | {note} | -- |")
    print(f"  {axis_name}: NOT run here -- {note}")

    with open(md_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"\nSaved markdown table -> {md_path}")


def main():
    checkpoints = os.path.join(_RESACO_DIR, "checkpoints")
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--seeds", type=int, nargs="+", default=[0, 1, 2, 3, 4])
    parser.add_argument("--scenarios", type=int, default=config.NUM_META_SCENARIOS)
    parser.add_argument("--outer", type=int, default=config.NUM_OUTER_ITERATIONS)
    parser.add_argument("--inner", type=int, default=config.NUM_INNER_SAC_UPDATES)
    parser.add_argument("--eval-scenarios", type=int, default=20)
    parser.add_argument("--eval-every", type=int, default=5)
    parser.add_argument("--meta-lr", type=float, default=1.0,
                        help="default 1.0 with the linear schedule = the H1 sweep's "
                             "winning stepsize (see README Convergence); 0.001 constant "
                             "reproduces the paper-default axis")
    parser.add_argument("--meta-lr-schedule", type=str, default="linear",
                        choices=["constant", "linear"])
    parser.add_argument("--base-source", type=str, default="mixed",
                        choices=["mixed", "table2", "apps", "nextgen", "sdv"],
                        help="the BASE row's training source and every variant's "
                             "held-out evaluation source")
    parser.add_argument("--out-csv", type=str, default=os.path.join(checkpoints, "ablations.csv"))
    parser.add_argument("--out-md", type=str, default=os.path.join(checkpoints, "ablations.md"))
    parser.add_argument("--smoke", action="store_true",
                        help="2 seeds / K=15 / 5 eval scenarios, writing *_smoke.* outputs")
    args = parser.parse_args()

    if args.smoke:
        args.seeds = args.seeds[:2]
        args.outer = min(args.outer, 15)
        args.eval_scenarios = min(args.eval_scenarios, 5)
        for attr in ("out_csv", "out_md"):
            root, ext = os.path.splitext(getattr(args, attr))
            if not root.endswith("_smoke"):
                setattr(args, attr, f"{root}_smoke{ext}")
    if args.outer % args.eval_every != 0:
        print(f"note: --eval-every {args.eval_every} does not divide K={args.outer}; using 1")
        args.eval_every = 1

    os.makedirs(os.path.dirname(args.out_csv), exist_ok=True)
    total = (1 + len(VARIANTS)) * len(args.seeds)
    done = 0
    for seed in args.seeds:
        base_row = None
        for axis, setting, overrides, outer_kwargs, source in [BASE] + VARIANTS:
            done += 1
            print(f"\n[{done}/{total}] {axis} = {setting} (seed {seed})")
            row = run_variant(axis, setting, overrides, outer_kwargs, source, seed, args)
            if axis == "BASE":
                base_row = row
                row["delta_vs_base"] = ""
            else:
                row["delta_vs_base"] = row["eval_reward_mean"] - base_row["eval_reward_mean"]
            append_row(args.out_csv, row)
            delta_txt = ("" if row["delta_vs_base"] == "" else
                         f"  delta_vs_base={row['delta_vs_base']:+.4f}")
            print(f"  -> heldout_eval={row['eval_reward_mean']:.4f}"
                  f"+-{row['eval_reward_std']:.4f}{delta_txt}")

    summarize(args.out_csv, args.out_md, args)


if __name__ == "__main__":
    main()
