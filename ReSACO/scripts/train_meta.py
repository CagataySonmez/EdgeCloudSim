"""Run the ReSACO Meta-Learning Phase (Algorithm 1) and save theta*.

Usage:
    python scripts/train_meta.py [--preset paper|fast] [--scenarios M] [--outer K]
                                 [--inner N] [--out PATH] ...

Defaults (--preset paper) reproduce the paper's Section V-B-1 setup
(M=10, K=300, N=50, meta_lr=0.001 constant), but these can be scaled down
for a quick smoke test. --preset fast switches to the measured
improved-training configuration (see PRESETS below and README "Environment
randomization & batched Reptile"); any flag given explicitly still wins.

The meta-training scenario pool (the paper's metadata set S) is saved
next to theta* as <out>_scenarios.json (mec_core/scenario_io.py), and
--load-scenarios replays any such file exactly.
"""

import argparse
import math
import os
import sys
import time

import torch

_RESACO_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, _RESACO_DIR)                      # ReSACO/  -> `resaco`, `bridge`
sys.path.insert(0, os.path.dirname(_RESACO_DIR))     # repo root -> `mec_core`, `baselines`

from mec_core import config
from resaco.reptile import outer_loop, residual_init_fraction, sample_heldout_scenarios
from mec_core.scenario import sample_scenario_pool
from mec_core.scenario_io import describe_scenarios, load_scenarios, save_scenarios
from mec_core.seeding import seed_everything

# Training-procedure presets. Every key is a CLI flag whose argparse default
# is None, so a flag given explicitly always overrides its preset value.
#   paper -- Table II / Section V-B-1 exactly (the repo-wide default).
#   fast  -- the improved configuration chosen by
#            scripts/compare_meta_configs.py's pre-registered rule
#            (2026-10 run, 5 seeds): Reptile's own linear-from-1.0 schedule,
#            the highest-IQM config whose adaptation advantage over random
#            init is CI-clear (+0.49 [+0.07, +1.05] vs the paper's -0.02).
#            Batched/env-randomized variants are opt-in flags -- see README
#            "Environment randomization & batched Reptile" for why.
PRESETS = {
    "paper": {
        "meta_lr": config.META_LR,
        "meta_lr_schedule": "constant",
        "meta_batch": 1,
        "workers": 1,
        "env_randomization": 0.0,
    },
    "fast": {
        "meta_lr": 1.0,
        "meta_lr_schedule": "linear",
        "meta_batch": 1,
        "workers": 1,
        "env_randomization": 0.0,
    },
}


def resolve_preset(args):
    """Fills every preset-controlled flag left at None from args.preset."""
    for key, value in PRESETS[args.preset].items():
        if getattr(args, key) is None:
            setattr(args, key, value)
    return args


def build_parser():
    parser = argparse.ArgumentParser()
    parser.add_argument("--preset", type=str, default="paper", choices=sorted(PRESETS),
                         help="training-procedure preset: 'paper' (default, Table II "
                              "exactly) or 'fast' (the measured improved configuration). "
                              "Any flag below given explicitly overrides its preset value.")
    parser.add_argument("--scenarios", type=int, default=config.NUM_META_SCENARIOS)
    parser.add_argument("--outer", type=int, default=config.NUM_OUTER_ITERATIONS)
    parser.add_argument("--inner", type=int, default=config.NUM_INNER_SAC_UPDATES)
    parser.add_argument("--meta-lr", type=float, default=None,
                         help="Reptile outer stepsize alpha (paper preset: Table II's "
                              f"{config.META_LR}). Note Eq. 8 is an EMA, so after K "
                              "iterations (1-alpha)^K of the random init survives in "
                              "theta* -- outer_loop prints that number at startup.")
    parser.add_argument("--meta-lr-schedule", type=str, default=None,
                         choices=["constant", "linear"],
                         help="'constant' (paper preset) or 'linear' (Reptile's own "
                              "annealing: alpha_k = meta_lr * (1 - (k-1)/K))")
    parser.add_argument("--meta-batch", type=int, default=None,
                         help="scenarios refined per outer iteration (paper preset: 1, "
                              "Algorithm 1). B > 1 is batched Reptile: theta moves toward "
                              "the average of B inner-loop results -- B times the env "
                              "interactions per iteration")
    parser.add_argument("--workers", type=int, default=None,
                         help="processes running each iteration's B inner loops in "
                              "parallel (theta* is bit-identical for any value; only "
                              "wall-clock changes). Incompatible with --persist-buffers.")
    parser.add_argument("--env-randomization", type=float, default=None,
                         help="probability in [0,1] that a scenario's physical environment "
                              "(bandwidths, edge/cloud MIPS, per-server heterogeneity, WAN "
                              "propagation, mobility) is drawn continuously from "
                              "config.ENV_RANDOMIZATION_RANGES instead of a fixed preset -- "
                              "Section IV-A-1's 'network bandwidths, or edge server "
                              "capabilities' axis (paper preset: 0)")
    parser.add_argument("--save-scenarios", type=str, default=None,
                         help="where to write the meta-training scenario pool as JSON "
                              "(default: <out stem>_scenarios.json next to theta*)")
    parser.add_argument("--load-scenarios", type=str, default=None,
                         help="meta-train on the exact scenario pool in this JSON file "
                              "(as written by --save-scenarios) instead of sampling one; "
                              "--scenarios/--scenario-source/--env-profiles/"
                              "--env-randomization then only shape the held-out eval set")
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
    parser.add_argument("--eval-scenarios", type=int, default=0,
                         help="N > 0 additionally evaluates theta on a FIXED held-out set of N "
                              "scenarios (sampled disjoint from the training pool, from the same "
                              "source/env restriction) at every logged iteration, adding "
                              "eval_reward_mean/eval_reward_std columns to the metrics CSV. "
                              "Separates learning progress from per-iteration scenario-difficulty "
                              "variance; 0 (default) keeps the current train-scenario-only eval. "
                              "Purely observational: theta* is bit-identical either way.")
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
    return parser


def main():
    parser = build_parser()
    args = resolve_preset(parser.parse_args())
    if args.meta_batch < 1:
        parser.error("--meta-batch must be >= 1")
    if args.workers < 1:
        parser.error("--workers must be >= 1")
    if not 0.0 <= args.env_randomization <= 1.0:
        parser.error("--env-randomization must be in [0, 1]")
    if args.persist_buffers and args.workers > 1:
        parser.error("--persist-buffers cannot be combined with --workers > 1 "
                     "(per-scenario buffers live in the main process)")

    # without this the replay buffer's global-RNG sampling and torch's
    # network init stay unseeded, so --seed alone never made a run
    # reproducible (see mec_core/seeding.py)
    seed_everything(args.seed)

    os.makedirs(os.path.dirname(args.out), exist_ok=True)

    env_names = args.env_profiles.split(",") if args.env_profiles else None
    if args.load_scenarios:
        scenarios, loaded_meta = load_scenarios(args.load_scenarios)
        origin = {k: loaded_meta.get(k) for k in ("seed", "preset", "scenario_source",
                                                   "env_randomization") if k in loaded_meta}
        print(f"Loaded {len(scenarios)} meta-training scenarios from {args.load_scenarios} "
              f"(originally sampled with {origin or 'unknown settings'})")
    else:
        print(f"Sampling {args.scenarios} meta-training scenarios "
              f"(source={args.scenario_source} x env profile x device count range"
              f"{f', env randomization p={args.env_randomization:g}' if args.env_randomization else ''})...")
        scenarios = sample_scenario_pool(args.scenarios, seed=args.seed,
                                         source=args.scenario_source, env_names=env_names,
                                         env_randomization=args.env_randomization)
    for i, s in enumerate(scenarios):
        p, e = s.app_profile, s.env_profile
        env_detail = ""
        if e.edge_mips_factors:
            env_detail = (f" (wlan={e.wlan_bandwidth_mbps:.0f} wan={e.wan_bandwidth_mbps:.1f}Mbps "
                          f"edge={e.edge_vm_mips:.0f}MIPS x[{min(e.edge_mips_factors):.2f}.."
                          f"{max(e.edge_mips_factors):.2f}] dwell="
                          f"{'static' if math.isinf(e.mobility_dwell_time) else f'{e.mobility_dwell_time:.0f}s'})")
        print(f"  S{i}: devices={s.number_of_mobile_devices} app={p.name} "
              f"env={e.name}{env_detail} "
              f"interarrival={p.poisson_interarrival:.1f}s "
              f"vm_util(edge/cloud/mobile)={p.vm_utilization_on_edge:.1f}/"
              f"{p.vm_utilization_on_cloud:.2f}/{p.vm_utilization_on_mobile:.1f}")

    scenarios_path = args.save_scenarios or (os.path.splitext(args.out)[0] + "_scenarios.json")
    summary = describe_scenarios(scenarios)
    save_scenarios(scenarios_path, scenarios, metadata={
        "seed": args.seed,
        "preset": args.preset,
        "scenario_source": args.scenario_source,
        "env_profiles": args.env_profiles,
        "env_randomization": args.env_randomization,
        "loaded_from": args.load_scenarios,
        "summary": summary,
    })
    print(f"Pool coverage: apps={summary['app_profiles']} envs={summary['env_profiles']}")
    print(f"Saved the meta-training scenario pool -> {scenarios_path}")

    eval_scenarios = None
    if args.eval_scenarios > 0:
        eval_scenarios = sample_heldout_scenarios(
            args.eval_scenarios, scenarios, seed=args.seed,
            source=args.scenario_source, env_names=env_names,
            env_randomization=args.env_randomization)
        print(f"Sampled {len(eval_scenarios)} held-out evaluation scenarios "
              f"(disjoint from the training pool).")

    print(f"\nRunning Outer Loop (preset={args.preset}): K={args.outer} outer iterations x "
          f"B={args.meta_batch} scenario(s), N={args.inner} inner SAC updates each, "
          f"meta_lr={args.meta_lr:g} {args.meta_lr_schedule}, workers={args.workers}...")
    metrics_log = []
    started = time.perf_counter()
    theta_star = outer_loop(
        scenarios,
        num_outer_iterations=args.outer,
        num_inner_updates=args.inner,
        meta_lr=args.meta_lr,
        seed=args.seed,
        progress_every=max(1, args.outer // 15),
        metrics_log=metrics_log,
        eval_every=max(1, args.eval_every),
        persist_buffers=args.persist_buffers,
        eval_scenarios=eval_scenarios,
        meta_lr_schedule=args.meta_lr_schedule,
        meta_batch_size=args.meta_batch,
        num_workers=args.workers,
    )
    elapsed = time.perf_counter() - started

    torch.save(theta_star, args.out)
    print(f"\nSaved meta-learned parameter theta* -> {args.out} "
          f"(meta-training wall-clock {elapsed:.1f}s; "
          f"{residual_init_fraction(args.meta_lr, args.outer, args.meta_lr_schedule):.3g} "
          f"of the random init survives in theta*)")

    if metrics_log:
        # the default theta_star.pt keeps its historical metrics filename; any
        # other --out gets its own, so side runs never clobber the main log
        metrics_name = ("train_meta_metrics.csv"
                        if os.path.basename(args.out) == "theta_star.pt"
                        else os.path.splitext(os.path.basename(args.out))[0] + "_metrics.csv")
        metrics_path = os.path.join(os.path.dirname(args.out), metrics_name)
        action_dim = len(metrics_log[0]["action_counts"])
        # eval columns go last so the CSV without --eval-scenarios stays
        # byte-identical to the pre-held-out-eval schema
        has_eval = "eval_reward_mean" in metrics_log[0]
        with open(metrics_path, "w") as f:
            headers = ["iteration", "avg_reward", "policy_entropy", "alpha"]
            headers += [f"greedy_action_{a}_count" for a in range(action_dim)]
            if has_eval:
                headers += ["eval_reward_mean", "eval_reward_std"]
            f.write(",".join(headers) + "\n")
            for m in metrics_log:
                row = [m["iteration"], f"{m['avg_reward']:.6f}",
                       f"{m['policy_entropy']:.6f}", f"{m['alpha']:.6f}"]
                row += m["action_counts"]
                if has_eval:
                    row += [f"{m['eval_reward_mean']:.6f}", f"{m['eval_reward_std']:.6f}"]
                f.write(",".join(str(v) for v in row) + "\n")
        print(f"Saved per-iteration training metrics -> {metrics_path}")

        first, last = metrics_log[0], metrics_log[-1]
        print(f"Reward trend (avg greedy reward per logged iter): "
              f"first={first['avg_reward']:.3f} last={last['avg_reward']:.3f}")
        if has_eval:
            print(f"Held-out eval trend ({args.eval_scenarios} fixed scenarios): "
                  f"first={first['eval_reward_mean']:.3f}+-{first['eval_reward_std']:.3f} "
                  f"last={last['eval_reward_mean']:.3f}+-{last['eval_reward_std']:.3f}")
        print(f"Policy entropy: first={first['policy_entropy']:.3f} "
              f"last={last['policy_entropy']:.3f} (uniform = ln({action_dim}) = "
              f"{math.log(action_dim):.3f}); final alpha={last['alpha']:.3f}")
        distinct = sum(1 for c in last["action_counts"] if c > 0)
        if distinct <= 1:
            print("WARNING: final greedy policy used only one action across the eval rollout "
                  "-- likely collapsed; see action count columns in the metrics CSV.")


if __name__ == "__main__":
    main()
