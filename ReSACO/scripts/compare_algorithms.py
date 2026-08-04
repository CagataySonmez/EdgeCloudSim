"""Reproduce the Section V-C style comparison: ReSACO vs. SAC (no
meta-init), DDPG, A2C, A3C as the number of mobile devices increases from
200 to 2,000 in steps of 200 (Fig. 6/7 and Table III of the paper).

Evaluation protocol (matching Section V-C):
- every device count is tested --iterations times (paper: 10) with
  distinct seeds and metrics averaged;
- with the default --scenario-source apps, each condition runs all four
  real application profiles and combines their metrics weighted by
  usage_percentage -- the paper's "four representative applications";
- before each measurement, the off-policy algorithms (ReSACO/SAC/DDPG)
  first run --adapt-steps online updates on the condition (Algorithm 4's
  Deployment Phase, the very mechanism the paper credits for ReSACO's
  advantage); A2C/A3C are evaluated frozen, exactly as the bridge serves
  them.

resaco/env.py models a single agent-controlled MD's task stream, but the
other (devices - 1) MDs' contention is real, not just approximated: every
step, env._inject_background_load admits a Poisson-sampled batch of
background tasks straight into the shared edge/cloud pools, scaled by
device count -- so bumping device_count here (scenario_for_device_count)
just updates number_of_mobile_devices and env.py's own background-load
injection does the rest, without requiring a full multi-agent simulator.

Usage:
    python scripts/compare_algorithms.py [--episode-steps N] [--seed S]
"""

import argparse
import csv
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
from mec_core.scenario import (APP_PROFILES, ENV_PROFILES, NEXTGEN_APP_PROFILES,
                              SDV_APP_PROFILES, Scenario, sample_table2_profile)
from mec_core.seeding import seed_everything


DEVICE_COUNTS = list(range(200, 2001, 200))

ALGORITHMS = {
    "ReSACO": ("theta_star.pt", lambda params: _load(SACAgent(), params)),
    "SAC": ("sac_no_meta.pt", lambda params: _load(SACAgent(), params)),
    "DDPG": ("ddpg.pt", lambda params: _load(DDPGAgent(), params)),
    "A2C": ("a2c.pt", lambda params: _load(A2CAgent(), params)),
    "A3C": ("a3c.pt", lambda params: _load(A2CAgent(), params)),
}


def _load(agent, params):
    agent.load_params(params)
    return agent


def evaluate(agent, scenario, episode_steps, seed):
    env = MECOffloadEnv(scenario, seed=seed)
    state = env.reset()

    service_times, process_times, network_delays = [], [], []
    energies, costs = [], []
    completed = failed_network = failed_vm = failed_mobility = sla_violations = 0

    for _ in range(episode_steps):
        action = agent.select_action(state, greedy=True)
        state, reward, done, info = env.step(action)
        if info["failed"]:
            if info.get("network_fail"):
                failed_network += 1
            if info.get("vm_fail"):
                failed_vm += 1
            if info.get("mobility_fail"):
                failed_mobility += 1
        else:
            completed += 1
            service_times.append(info["service_time"])
            network_delays.append(info["delay"])
            process_times.append(info["service_time"] - info["delay"])
            energies.append(info["energy_j"])
            costs.append(info["cost"])
            if info["sla_violated"]:
                sla_violations += 1

    total = completed + failed_network + failed_vm + failed_mobility
    avg = lambda xs: (sum(xs) / len(xs)) if xs else float("nan")
    return {
        "completion_rate": completed / total if total else float("nan"),
        "network_fail_rate": failed_network / total if total else float("nan"),
        "vm_fail_rate": failed_vm / total if total else float("nan"),
        "mobility_fail_rate": failed_mobility / total if total else float("nan"),
        "avg_service_time": avg(service_times),
        "avg_processing_time": avg(process_times),
        "avg_network_delay": avg(network_delays),
        # industry-direction metrics: per-completed-task energy and monetary
        # cost, and the SLA violation rate among *delivered* tasks (SLO
        # attainment view -- failed tasks are already counted above)
        "avg_energy_j": avg(energies),
        "avg_cost": avg(costs),
        "sla_violation_rate": sla_violations / completed if completed else float("nan"),
    }


def adapt(agent, scenario, steps, seed):
    """Algorithm 4 (Deployment Phase) before measurement: a short online
    adaptation round on the evaluation condition. Off-policy agents expose
    a transition-collection+update loop (SACAgent.sac_update_loop,
    DDPGAgent.train_loop); on-policy A2C/A3C have no single-transition
    online rule and are served frozen -- same split the bridge applies."""
    if steps <= 0:
        return
    env = MECOffloadEnv(scenario, seed=seed)
    if hasattr(agent, "sac_update_loop"):
        agent.sac_update_loop(env, num_transitions=steps)
    elif hasattr(agent, "train_loop"):
        agent.train_loop(env, num_transitions=steps)


def combine(metric_dicts, weights):
    """Weighted mean of every metric across dicts, ignoring NaNs
    (a condition where nothing completed contributes no service time)."""
    out = {}
    for key in metric_dicts[0]:
        num = den = 0.0
        for m, w in zip(metric_dicts, weights):
            v = m[key]
            if v == v:  # not NaN
                num += w * v
                den += w
        out[key] = num / den if den else float("nan")
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--episode-steps", type=int, default=500)
    parser.add_argument("--iterations", type=int, default=10,
                         help="independent repetitions per device count (paper Section V-C: "
                              "'Each MD count is tested 10 times, and average values are "
                              "reported')")
    parser.add_argument("--adapt-steps", type=int, default=config.NUM_INNER_SAC_UPDATES,
                         help="Algorithm-4 online-adaptation updates each off-policy agent "
                              "(ReSACO/SAC/DDPG) runs on the condition before measurement; "
                              "0 evaluates everything frozen")
    parser.add_argument("--seed", type=int, default=123)
    parser.add_argument("--checkpoints-dir", type=str, default=os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "checkpoints"))
    parser.add_argument("--out-csv", type=str, default=os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "checkpoints", "comparison.csv"))
    parser.add_argument("--env-profile", type=str, default="DEFAULT",
                         choices=[e.name for e in ENV_PROFILES],
                         help="environment preset to sweep under (network quality, tier "
                              "hardware, device mobility -- see resaco/scenario.py's "
                              "ENV_PROFILES). Defaults to DEFAULT so runs stay comparable "
                              "across invocations instead of inheriting whichever profile "
                              "the seeded base-scenario sample happened to draw.")
    parser.add_argument("--scenario-source", type=str, default="apps",
                         choices=["apps", "table2", "nextgen", "sdv"],
                         help="workloads to sweep: 'apps' (default) = the four classic "
                              "applications.xml profiles combined by usage_percentage, "
                              "matching the real EdgeCloudSim comparison; 'nextgen' = the "
                              "GenAI/XR/V2X/smart-city trend workloads combined the same "
                              "way; 'sdv' = the ADAS/HD-map/DMS/OTA vehicle workloads; "
                              "'table2' = one seeded paper-style synthetic profile.")
    args = parser.parse_args()

    seed_everything(args.seed)  # see resaco/seeding.py

    loaders = {}  # name -> (loader, trained params): a FRESH agent per condition
    for name, (filename, loader) in ALGORITHMS.items():
        path = os.path.join(args.checkpoints_dir, filename)
        if not os.path.exists(path):
            print(f"WARNING: {path} not found, skipping {name}. "
                  f"Run train_meta.py / train_baselines.py first.")
            continue
        loaders[name] = (loader, torch.load(path, map_location="cpu"))

    if not loaders:
        print("No trained checkpoints found. Nothing to compare.")
        return

    env_profile = next(e for e in ENV_PROFILES if e.name == args.env_profile)
    print(f"Environment profile: {env_profile.name} "
          f"(wan={env_profile.wan_bandwidth_mbps}Mbps, edge={env_profile.edge_vm_mips}MIPS, "
          f"mobility dwell={env_profile.mobility_dwell_time}s)")

    # The paper's V-C setup: representative applications combined by
    # usage_percentage ('apps' = the classic four, 'nextgen' = the trend
    # workloads). table2 instead evaluates one seeded synthetic profile.
    if args.scenario_source == "apps":
        eval_profiles = [(p, p.usage_percentage) for p in APP_PROFILES]
    elif args.scenario_source == "nextgen":
        eval_profiles = [(p, p.usage_percentage) for p in NEXTGEN_APP_PROFILES]
    elif args.scenario_source == "sdv":
        eval_profiles = [(p, p.usage_percentage) for p in SDV_APP_PROFILES]
    else:
        profile = sample_table2_profile(__import__("random").Random(args.seed))
        eval_profiles = [(profile, 1.0)]
    print(f"Task profiles: {', '.join(p.name for p, _ in eval_profiles)} "
          f"x {args.iterations} iterations, adapt_steps={args.adapt_steps}")

    rows = []
    for device_count in DEVICE_COUNTS:
        for name, (loader, params) in loaders.items():
            profile_metrics, profile_weights = [], []
            for p_idx, (profile, usage_weight) in enumerate(eval_profiles):
                scenario = Scenario(app_profile=profile,
                                    number_of_mobile_devices=device_count,
                                    env_profile=env_profile)
                iteration_metrics = []
                for it in range(args.iterations):
                    # fresh copy of the trained parameters per condition so
                    # one condition's Algorithm-4 adaptation never leaks
                    # into the next (each condition is its own S_new)
                    agent = loader(params)
                    run_seed = args.seed + device_count + 7919 * it + 104729 * p_idx
                    adapt(agent, scenario, args.adapt_steps, seed=run_seed + 1)
                    iteration_metrics.append(
                        evaluate(agent, scenario, args.episode_steps, seed=run_seed))
                profile_metrics.append(combine(iteration_metrics, [1.0] * len(iteration_metrics)))
                profile_weights.append(usage_weight)
            metrics = combine(profile_metrics, profile_weights)
            row = {"algorithm": name, "devices": device_count, **metrics}
            rows.append(row)
            print(f"devices={device_count:5d}  {name:8s}  "
                  f"completion={metrics['completion_rate']*100:6.2f}%  "
                  f"service_time={metrics['avg_service_time']:.3f}s  "
                  f"net_fail={metrics['network_fail_rate']*100:5.2f}%  "
                  f"vm_fail={metrics['vm_fail_rate']*100:5.2f}%  "
                  f"mob_fail={metrics['mobility_fail_rate']*100:5.2f}%  "
                  f"energy={metrics['avg_energy_j']:6.2f}J  "
                  f"cost={metrics['avg_cost']:5.2f}  "
                  f"sla_viol={metrics['sla_violation_rate']*100:5.2f}%")

    os.makedirs(os.path.dirname(args.out_csv), exist_ok=True)
    with open(args.out_csv, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
    print(f"\nSaved comparison table -> {args.out_csv}")

    def pct_improve(base, resaco):
        """Relative percent improvement -- fine for service_time, a positive
        duration essentially never at/near zero."""
        if base in (0, None) or base != base:
            return float("nan")
        return (base - resaco) / base * 100

    def pct_point_diff(base, resaco):
        """Absolute percentage-point difference (base - resaco), for
        network_fail_rate/vm_fail_rate -- both already bounded in [0, 100].
        A relative percent-improvement formula blows up for these: it's NaN
        whenever a baseline's rate is exactly 0 (common here -- network
        failures never happen in this env) and explodes to absurd values
        (e.g. -13150%) whenever the baseline is merely close to 0."""
        if base is None or base != base or resaco is None or resaco != resaco:
            return float("nan")
        return base - resaco

    # Table III style: ReSACO's improvement over each baseline at the
    # highest device count. Failure-rate columns are shown BOTH as the
    # paper's relative reduction (Table III's own metric; n/a when the
    # baseline's rate is 0 -- the formula divides by it) and as absolute
    # percentage-point differences (robust when baselines sit at/near 0).
    if "ReSACO" in loaders:
        last = {r["algorithm"]: r for r in rows if r["devices"] == DEVICE_COUNTS[-1]}
        if "ReSACO" in last:
            def fmt_rel(v):
                return f"{v:.1f}%" if v == v else "n/a"

            print(f"\nReSACO improvement at {DEVICE_COUNTS[-1]} devices (Table III style):")
            print(f"{'Algorithm':10s} {'Service Time':>13s} {'Net Fail rel':>13s} {'Net Fail':>10s} "
                  f"{'VM Fail rel':>12s} {'VM Fail':>10s}")
            resaco_row = last["ReSACO"]
            for name, row in last.items():
                if name == "ReSACO":
                    continue
                st = pct_improve(row["avg_service_time"], resaco_row["avg_service_time"])
                nf_rel = pct_improve(row["network_fail_rate"], resaco_row["network_fail_rate"])
                vf_rel = pct_improve(row["vm_fail_rate"], resaco_row["vm_fail_rate"])
                nf = pct_point_diff(row["network_fail_rate"], resaco_row["network_fail_rate"])
                vf = pct_point_diff(row["vm_fail_rate"], resaco_row["vm_fail_rate"])
                print(f"{name:10s} {st:12.1f}% {fmt_rel(nf_rel):>13s} {nf:8.1f}pp "
                      f"{fmt_rel(vf_rel):>12s} {vf:8.1f}pp")


if __name__ == "__main__":
    main()
