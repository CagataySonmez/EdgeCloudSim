# ReSACO

A from-scratch implementation of **ReSACO** (Reptile-based Soft Actor-Critic
for Offloading) from *"ReSACO: A Meta Reinforcement Learning Method for Fast
Offloading in Mobile Edge Computing"* (Kim & Yu, IEEE CLOUD 2025) -- a
Reptile meta-learning loop wrapped around a discrete-action Soft
Actor-Critic agent that decides, per task, whether to run it on the
**device**, an **edge** server, or the **cloud**.

It has three parts:

1. **`resaco/`** -- the AI model itself: networks, replay buffer, SAC-Update
   (Algorithm 3), the Reptile Outer/Inner Loop meta-training (Algorithms
   1-2), and the Deployment Phase (Algorithm 4), plus the four baselines
   the paper compares against (SAC without meta-init, DDPG, A2C, A3C).
2. **`bridge/inference_server.py`** -- a small TCP server that serves all
   five trained policies (ReSACO + the four baselines) for live offloading
   decisions, keyed by name, and keeps the off-policy ones (ReSACO, SAC,
   DDPG) learning online from real outcomes (Algorithm 4 in production),
   periodically flushing that online adaptation back to disk so it survives
   a bridge restart (see "Online-learning persistence" below).
3. Integration with **EdgeCloudSim**: `../EdgeCloudSim/src/edu/boun/edgecloudsim/applications/resaco/`
   is a full EdgeCloudSim application (its own `ReSACOMainApp`,
   `ReSACOEdgeOrchestrator`, etc., mirroring the existing `three_tier` app)
   whose orchestrator calls into this bridge for every offloading decision.
   `three_tier` itself is untouched -- `resaco` only *reuses* its generic,
   unmodified `ThreeTierNetworkModel` / `ThreeTierMobileServerManager`.
   Listing all five algorithms as `orchestrator_policies` runs all five
   through the *real* CloudSim discrete-event simulation in the same
   EdgeCloudSim experiment -- a much more meaningful comparison than
   `compare_algorithms.py`'s lightweight toy environment (see below).

## Quick start

```
python -m venv venv
venv/bin/pip install -r requirements.txt        # Linux/Mac; use venv\Scripts\pip on Windows

venv/bin/python scripts/train_meta.py            # -> checkpoints/theta_star.pt (ReSACO)
venv/bin/python scripts/train_baselines.py       # -> checkpoints/{sac_no_meta,ddpg,a2c,a3c}.pt

venv/bin/python bridge/inference_server.py       # leave running in its own terminal

# in another terminal:
cd ../EdgeCloudSim/scripts/ReSACO
./compile.sh
./run_scenarios.sh 4 1                           # 4 parallel processes, 1 iteration
```

That's ReSACO and all four paper baselines trained, served, and run
through the real EdgeCloudSim/CloudSim simulation end to end. See
"Compare" and "Serve to EdgeCloudSim" below for what each piece is
actually doing, and the Known limitations section before treating the
numbers as validated results.

## Layout

This directory holds **only the ReSACO algorithm**. Everything it builds
on that isn't ReSACO-specific lives beside it at the repo root, so
another algorithm can be added or compared without touching ReSACO/:

```
../mec_core/            shared, algorithm-agnostic MEC infrastructure
  config.py             tier/network/reward constants + the paper's Table II ranges
  scenario.py           AppProfile x EnvProfile x device-count sampling (see "Scenarios" below)
  env.py                lightweight 3-tier offload environment (state/action/reward, Section III)
  networks.py           discrete Actor + twin Critic (Fig. 3)
  replay_buffer.py      replay buffer D (columnar numpy ring -- see "Performance" below)
  normalize.py          fixed per-feature state normalization (see "Convergence" below)
  seeding.py            seed_everything() -- pins every RNG (see "Reproducibility" below)

../baselines/           the non-ReSACO comparison learners (Section V-C)
  ddpg.py               DDPG, discrete-adapted (softmax-relaxed actor output fed to the critic)
  a2c.py                synchronous Advantage Actor-Critic
  a3c.py                asynchronous A3C (shared global net, threaded workers, Hogwild!-style updates)

resaco/                 ReSACO itself -- nothing else
  sac.py                SACAgent: SAC-Update (Algorithm 3, Eq. 9-13) + learned entropy temperature
  reptile.py            Outer Loop / Inner Loop meta-training (Algorithm 1-2, Eq. 8)
  deploy.py             DeploymentAgent: Deployment Phase (Algorithm 4)

bridge/
  inference_server.py    TCP ACT/OUTCOME server wrapping a DeploymentAgent

scripts/
  train_meta.py          run Algorithm 1 -> checkpoints/theta_star.pt
  train_baselines.py     train SAC(no meta-init)/DDPG/A2C/A3C with the same step budget as ReSACO
  compare_algorithms.py  Section V-C style comparison across MD counts -> checkpoints/comparison.csv
  plot_convergence.py    Section V-B / Fig. 5 style meta-init vs. random-init convergence plot

tests/                  pytest suite -- see "Tests" below

checkpoints/  (gitignored) trained weights + training/comparison/convergence logs and plots
              theta_star.pt/sac_no_meta.pt/ddpg.pt never change after training;
              online adaptation instead accumulates in a sibling
              "<name>_adapted.pt" file written by the bridge (see below)
```

## Setup

```
python -m venv venv
venv\Scripts\pip install -r requirements.txt      # Windows
./venv/bin/pip install -r requirements.txt        # Linux/Mac
```

`requirements.txt` is version-pinned to what's actually been tested here
(including `pytest`, for the test suite below) -- a plain `pip install -r
requirements.txt` reproduces the same dependency versions every time
instead of whatever happens to be latest on the day you install.

## Tests

```
python -m pytest tests/          # from inside ReSACO/, with venv active
```

Also runs automatically on every push/PR via
`../.github/workflows/tests.yml` (a separate CI job runs
`scripts/tests/` too), so a regression here shows up without anyone
having to remember to run `pytest` locally.

127 tests, ~8 seconds, no GPU/network/trained-checkpoint dependency
(agents are freshly constructed per test; `test_bridge.py` writes throwaway
fake checkpoints to `tmp_path` rather than touching `checkpoints/`).
Coverage is weighted toward regression protection for the bugs found and
fixed this session -- each is a real prior failure mode, not a
hypothetical:
- `test_sac.py` -- `sac_update_loop` must perform exactly N real gradient
  updates even when N < `BATCH_SIZE` (the exact condition that made the
  Reptile Inner Loop a silent no-op; see "Convergence" above); the learned
  entropy temperature actually receives gradient updates, round-trips
  through get/load_params, and pre-alpha checkpoints still load.
- `test_reptile.py` -- an end-to-end check that the Outer Loop actually
  moves `theta` away from its random initialization.
- `test_normalize.py`, `test_env.py` -- the state-normalization fix, the
  device-tier delay/background-contention fixes, and the
  delay-sensitivity reward weighting (both the success and the failure
  branch scale by exactly `DELAY_SENSITIVITY_BASE + sensitivity`).
- `test_baselines.py` -- DDPG/A2C/A3C each use their own tuned learning
  rate, not SAC's.
- `test_deploy.py`, `test_bridge.py` -- the online-learning persistence
  (autosave-every-N-updates, resume-from-adapted-checkpoint on restart).
- `test_replay_buffer.py` -- the columnar ring buffer keeps the deque's
  eviction order and draws bit-identical samples (checked against a
  `deque` reference), including after wrapping.
- `test_seeding.py` -- the same seed reproduces identical meta-parameters
  (and a different seed doesn't), guarding the reproducibility fix.
- `test_config_mirror.py` -- every value duplicated between Python and
  Java/XML actually matches: each `<application>` vs its `AppProfile`,
  each `ENV_PROFILE` vs its `.properties` preset *and* the
  `edge_devices*.xml` its simulation.list row pairs it with, every
  mirrored constant in `ReSACOStateBuilder.java` vs `config.py`, and the
  task-lookup column indices vs the order `SimSettings.java` parses its
  attributes in. These pairs are kept in sync by hand and used to be
  guarded only by "if you change one, change both" comments; comments
  don't fail a build.
- `test_scenario.py` -- the four real app profiles are present and match
  `applications.xml`, and `sample_scenario_pool`'s app-type mix roughly
  tracks each profile's `usage_percentage` (see "Scenarios" below).

Not covered: the Java side, the TCP wire protocol end-to-end (covered
manually -- see this file's protocol section -- not by an automated
socket test), and anything requiring a trained checkpoint (convergence
quality, actual tier-selection behavior) -- those are evaluated by
actually running `train_meta.py`/`plot_convergence.py`/
`compare_algorithms.py`, not asserted on in a fast unit test.

## Scenarios

Every meta-training scenario samples its task profile from one of two
**scenario sources** (`train_meta.py --scenario-source`, default `mixed`):

- **`table2`** -- the paper's own metadata construction (Section V-A-1):
  every Table II parameter is randomized within its published interval
  (`config.TABLE_II_RANGES`), including the stated coupling
  `vm_utilization_on_edge = 10 x vm_utilization_on_cloud` and
  `required_core = 1`. This gives continuous per-scenario diversity over
  usage percentage, interarrival, delay sensitivity, active/idle periods,
  data sizes, task length, and VM utilizations -- the paper's actual
  meta-training distribution.
- **`apps`** -- one of the four *real* application types
  (`scripts/{ReSACO,three_tier}/config/applications.xml`'s
  `AUGMENTED_REALITY`, `HEALTH_APP`, `HEAVY_COMP_APP`,
  `INFOTAINMENT_APP`, mirrored exactly in `scenario.py`'s
  `APP_PROFILES`), weighted by that app's real `usage_percentage` --
  matching how EdgeCloudSim's own `IdleActiveLoadGenerator` assigns each
  mobile device exactly one app type for the whole simulation. This
  covers the exact conditions the deployment simulation generates,
  including `HEAVY_COMP_APP`'s task_length=45000, which Table II's own
  range (max 10000) never reaches.
- **`mixed`** (default) -- 50/50 per scenario
  (`scenario.py`'s `SCENARIO_SOURCE_WEIGHTS`): paper-faithful continuous
  diversity plus guaranteed real-workload coverage.

`env.py`'s `_sample_task()` draws task length / upload / download size
from an exponential distribution around the profile's mean, exactly like
`IdleActiveLoadGenerator`'s `ExponentialDistribution`, not a bounded
uniform jitter. Two Table II parameters that previously had **no effect
at all** in the training env are now honored the way EdgeCloudSim honors
them: `active_period`/`idle_period` drive an active/idle duty cycle (the
agent's own device fast-forwards through idle windows, and background
devices' arrival rate is scaled by `active/(active+idle)`), and a
Table II scenario's `usage_percentage` scales how many of the other
devices participate at all ("how broadly tasks are utilized by MDs" --
for the four real apps it's instead the population mix weight, which
`sample_scenario` already applies).

### Environment profiles

Orthogonally to the app mix, every scenario now also samples an
**environment profile** (`scenario.py`'s `ENV_PROFILES`) -- the physical
deployment conditions the paper treats as fixed:

| Profile        | What changes vs. DEFAULT                          | Real-world reading                       |
|----------------|---------------------------------------------------|------------------------------------------|
| DEFAULT        | nothing (three_tier config values, no mobility)   | pedestrians in a well-provisioned city   |
| HIGH_MOBILITY  | mean WLAN dwell time -> 30s                       | vehicles/commuters; edge/cloud round trips risk mobility failure |
| CONGESTED_WAN  | WAN bandwidth 15 -> 3 Mbps                        | rural/overloaded backhaul; cloud compute-rich but hard to reach |
| WEAK_EDGE      | edge VM MIPS 10000 -> 2500                        | cheap micro-datacenter edge tier         |
| MMWAVE_5G      | WLAN 200 -> 1000 Mbps, WAN 15 -> 50 Mbps, dwell -> 10s | 5G mmWave small cells: huge pipes, constant handoffs |
| NTN_BACKHAUL   | WAN propagation 0.1 -> 0.6s, WAN 15 -> 30 Mbps    | LEO-satellite (3GPP NTN) backhaul: cloud latency structurally bad |
| GPU_EDGE       | edge VM MIPS 10000 -> 40000                       | GPU/NPU-accelerated AI-first edge micro-datacenters |
| SDV_HIGHWAY    | WLAN 300, WAN 30 Mbps, dwell -> 8s                | 100+ km/h past sparse RSU corridors: fiber backhaul, brutal handoffs |
| SDV_RURAL_ROAD | WLAN 100, WAN 8 Mbps, edge MIPS / 2, dwell 45s    | thin rural roadside infrastructure: nothing is comfortably fast |
| SDV_URBAN      | WLAN 400 Mbps, edge MIPS x 2, dwell 25s           | stop-and-go under dense small cells + well-provisioned city MEC |

Mobility is modeled the same way EdgeCloudSim fails tasks "due to
mobility": a device leaves its serving WLAN cell (exponential dwell time)
before an offloaded task's result comes back -> the task fails, though
the chosen VM still burns its capacity (the work happened; the delivery
didn't). Local (device) execution is immune -- which is exactly what
makes HIGH_MOBILITY strategically interesting: the tier trade-off
inverts for long tasks. The env profile is deliberately **not** in the
agent's state vector: it's a scenario-level latent condition, i.e.
precisely the kind of environmental variation Reptile is supposed to
meta-learn across (and Algorithm 4's online adaptation to specialize to).

Each profile mirrors a runnable real-simulation preset under
`../scripts/ReSACO/config/` (`high_mobility/congested_wan/weak_edge/
mmwave_5g/ntn_backhaul/gpu_edge.properties` plus the
`edge_devices_weak_edge/gpu_edge.xml` variants), all wired into
`simulation.list` -- so the same environments can be run through the real
CloudSim engine, and `scripts/compare_algorithms.py --env-profile
MMWAVE_5G` (etc.) runs the fast toy-env sweep under a chosen profile. If
you change a preset on one side, change the matching side too.

### Next-generation workloads

Alongside the classic four applications, `scenario.py`'s
`NEXTGEN_APP_PROFILES` (mirroring
`../scripts/ReSACO/config/applications_nextgen.xml`) model current edge
trends, available as scenario source `nextgen` (part of the default
`mixed` training diet at 20%, and runnable in the real simulator via the
`nextgen_apps`/`mmwave_5g`/`ntn_backhaul`/`gpu_edge` simulation.list
rows):

| Workload             | Shape                                              | Trend                              |
|----------------------|----------------------------------------------------|------------------------------------|
| GENAI_INFERENCE      | 60000 MI (heaviest of any profile), small I/O, sensitivity 0.6 | LLM/diffusion inference offload -- barely runnable on-device |
| XR_STREAMING         | bidirectionally heavy (1800 up / 2500 down KB), sensitivity 1.0 | AR/VR/metaverse motion-to-photon budgets |
| V2X_PERCEPTION       | 1s interarrival, 1500 KB sensor frames up, sensitivity 1.0 | autonomous-driving cooperative perception |
| SMART_CITY_ANALYTICS | batched 20000 MI, sensitivity 0.2                  | city-scale video/IoT analytics     |

### SDV workloads

`SDV_APP_PROFILES` (mirroring `applications_sdv.xml`, scenario source
`sdv`, 15% of the default mixed diet) model a software-defined vehicle's
fleet mix; they pair naturally with the three SDV_* driving environments
above (`sdv_highway`/`sdv_rural_road`/`sdv_urban` simulation.list rows
run each combination through the real CloudSim engine). Every SDV
workload carries an SLA deadline:

| Workload            | Shape                                             | SLA    |
|---------------------|---------------------------------------------------|--------|
| ADAS_PERCEPTION     | 1s interarrival sensor-fusion stream, 1200 KB up, sensitivity 1.0 | 1.0s |
| HD_MAP_UPDATE       | download-dominated tile refresh (2000 KB down)    | 3.0s   |
| DRIVER_MONITORING   | small in-cabin frames, light compute -- the on-vehicle candidate | 2.0s |
| OTA_SOFTWARE_UPDATE | rare, download-massive (2500 KB), sensitivity 0.05 -- the defining SDV background job | 120s |

**Fixed this pass:** the previous version sampled task_length,
data_upload/download, poisson_interarrival, delay_sensitivity,
active/idle period, and vm_utilization_on_* from a single abstract
"Table II range" -- a homogeneous task profile no real simulation run
ever actually uses. It also meaningfully understated task variety: the
old task_length range topped out at 10000, while `HEAVY_COMP_APP`'s real
mean is 45000 -- so the training distribution never included the kind of
long, expensive task the real deployment generates roughly a fifth of the
time. `number_of_mobile_devices` is still randomized per scenario (Table
II's range, 200-2000) since it's a genuinely free environmental
condition no config file fixes.

## Train

```
python scripts/train_meta.py          # ReSACO meta-training (Algorithm 1); ~1-2 min at paper defaults (M=10, K=300, N=50)
python scripts/train_baselines.py     # SAC(no meta-init)/DDPG/A2C/A3C, same total env-step budget (K*N)
```

Both scripts accept `--help` for scaled-down smoke-test runs (fewer
scenarios/iterations).

`train_meta.py` training-speed/fidelity flags:

- `--scenario-source {mixed,table2,apps}` -- see "Scenarios" above.
- `--eval-every N` (default 5) -- the per-iteration metrics eval is pure
  logging overhead (~a third of all env interactions at 1); thinning it
  speeds up wall-clock training without touching the training math.
- `--persist-buffers` -- keeps one replay buffer per scenario across
  outer iterations instead of Algorithm 2's fresh-D-per-inner-loop:
  skips the 64-step warm-up every single outer iteration and gives every
  minibatch more diverse experience. A deliberate, documented deviation
  from the paper's procedure (off by default).

Warm-up transitions are collected with uniform-random actions -- the
paper's own "collects initial transitions by allowing exploratory
actions" -- which both explores better than sampling an untrained policy
and skips the actor forward pass on every warm-up step.

### Industry-direction objectives (energy / cost / SLA)

Beyond raw latency, the offloading decision now optimizes the three
objectives the cloud/edge industry actually operates on. On success the
reward is

```
r = -( w * T                                        delay (Eq. 9, weighted)
       + ENERGY_REWARD_WEIGHT  * E                  joules
       + COST_REWARD_WEIGHT    * C                  cost units
       + SLA_PENALTY_WEIGHT    * max(0, T - D) )    deadline overshoot
```

- **Energy (green computing):** local execution burns device CPU power
  (4W); an offload burns radio TX (1.3W) for the WLAN leg plus the chosen
  tier's active per-VM power -- edge micro-DCs at 30W/VM vs. hyperscaler
  cloud at 90W/VM but far faster per MI, and cloud tasks add WAN
  transport energy per Mbit. The trade-off this creates is the industry's
  real one: heavy tasks are energy-cheapest on efficient cloud silicon,
  *if* the network is worth it.
- **Cost (FinOps):** the device is the user's own hardware (free); edge
  VM-seconds are priced per edge_devices.xml's `costPerSec` (3.0/s),
  cloud VM-seconds cheaper (1.0/s, economies of scale) -- but cloud
  results pay the classic per-MB WAN **egress fee**, the canonical cloud
  bill line item.
- **SLA/SLO:** every app profile now carries a `max_delay_requirement`
  deadline (applications*.xml's optional field: AR 2s, XR 1.5s, V2X 1s,
  GenAI 10s, ...). A task that succeeds past its deadline is still
  delivered but counts as an **SLA violation** and pays a penalty
  proportional to the overshoot.

`compare_algorithms.py` reports all three: `avg_energy_j`, `avg_cost`,
and `sla_violation_rate` (violations among delivered tasks) per
algorithm/device count, alongside the existing latency/failure columns.
The Java side mirrors the reward terms in
`ReSACOStateBuilder.industryPenalty()` (constants must match config.py;
process time is re-derived from cloudlet length / live tier MIPS, and the
serviceTime remainder is billed as the radio window -- documented
approximations).

### Paper-exact mode

The documented improvements deviate from the paper's equations; all have
switches that recover the paper exactly (flip them and retrain -- served
policies must match the reward scale they were trained on):

- `config.AUTO_ENTROPY_TUNING = False` -- fixed tau=0.2 (Table II) instead
  of the learned entropy temperature.
- `config.DELAY_SENSITIVITY_REWARD = False` -- Eq. (9)'s unweighted
  reward (-T / -(Tmax+1)); also flip Java's
  `ReSACOStateBuilder.RESACO_DELAY_SENSITIVITY_REWARD` so the bridge's
  online-learning rewards stay on the same scale.
- `config.ENERGY_AWARE_REWARD = False`, `config.COST_AWARE_REWARD =
  False`, `config.SLA_PENALTY_WEIGHT = 0.0` -- pure-latency reward with
  no industry objectives; flip the matching `ReSACOStateBuilder`
  constants too.

With both off and `--scenario-source table2`, the training pipeline is a
line-by-line reproduction of the paper's Algorithms 1-3 with Table II's
metadata and Section V-B-1's hyperparameters. (`--env-profiles DEFAULT`
additionally pins the physical environment, whose DEFAULT profile
disables mobility failures so the failure model is exactly Eq. 2-4.)

One accounting note: with the paper's own N=50 < batch 64, each inner
loop necessarily runs 64 uncounted exploratory warm-up transitions
before its 50 counted update steps -- 114 env interactions per outer
iteration. This is the paper's own "collects initial transitions by
allowing exploratory actions" escape hatch, not an extra deviation.

### Paper-fidelity audit fixes

A systematic paper-vs-code audit (31 confirmed discrepancies across 6
dimensions) drove these corrections -- each aligns the implementation
with something the paper states explicitly:

- **Tier capacity (Section V-A-2 / Eq. 3):** each edge server now has its
  full 8-VM aggregate budget and the cloud its 4-VM budget (utilization
  tracked as the node's average VM utilization, admission approximating
  least-loaded placement). Previously every edge server was modeled as a
  single VM -- 1/8 of the paper's edge capacity, 1/4 of its cloud
  capacity -- wildly inflating VM failures.
- **Load-dependent bandwidth (Fig. 6(c)):** available bandwidth now falls
  with the number of concurrently active devices sharing each link
  (`config.BANDWIDTH_CONTENTION_PER_CLIENT`), so 2,000-device scenarios
  genuinely see longer network delays than 200-device ones. Previously
  bandwidth was sampled independently of load.
- **Propagation constants (Section III-A/B):** the 0.1s WAN propagation
  delay applies to the WAN leg only; MAN relays pay the 5ms internal LAN
  delay and WLAN none. Previously every link (including WLAN) paid the
  WAN constant, and the cloud path paid it twice.
- **Serving edge server:** each episode the device sits in one edge
  server's WLAN cell; offloading elsewhere pays a full MAN relay leg
  (EdgeCloudSim's neighbor-edge path) instead of a flat 0.1x MAN charge
  on every edge action. The serving index is deliberately not in the
  state (the deployment state doesn't carry it either).
- **Background offloading follows the policy:** the other M-1 devices'
  edge/cloud split now mirrors the empirical mix of the agent's own
  recent decisions (the paper's orchestrator decides for every MD)
  instead of an invented fixed 80/20, and injected in-flight load is
  Little's-law-consistent (window capped by process time).
- **Online learning actually bootstraps (Eq. 10):** the Java side
  reported every outcome with done=1, zeroing the gamma*V(s') bootstrap
  term on every single online update -- Algorithm 4 was silently a
  one-step bandit. Outcomes now report done=0, matching training.
- **Live bandwidth at deployment:** `ReSACOStateBuilder` probes the
  network model's MM1 state (dummy 1-Mbit-task delay, the same trick the
  fallback heuristic already used) for effective WLAN/MAN/WAN bandwidth
  instead of sending the static config capacities, so the three bandwidth
  state features finally vary at deployment the way they do in training.
- **In-distribution next states:** outcome reports fill s_{t+1}'s L/U/D
  with the app type's mean task size from applications.xml instead of
  zeros (which training, clamping at >= 1, never produces).
- **Per-run adaptation reset (Algorithm 4 line 1):** a new bridge RESET
  command re-copies theta* into theta_adapt (clearing the replay buffer
  and in-flight state); the Java orchestrator issues it at the start of
  every simulation run, so each run adapts from theta* instead of
  inheriting the previous run's drift across the whole device-count x
  policy sweep.
- **Comparison protocol (Section V-C):** `compare_algorithms.py` now
  tests each device count `--iterations` times (default 10) with metrics
  averaged, evaluates all four real app profiles combined by
  usage_percentage, and gives off-policy algorithms `--adapt-steps` of
  Algorithm-4 adaptation before measuring (the very mechanism the paper
  credits for ReSACO's advantage); its Table III summary prints both the
  paper's relative failure reductions and absolute percentage points.
- Assorted leak fixes surfaced by the audit: the Java submission-clock
  map now drains on failures too, the bridge's pending-decision maps are
  size-capped, and zero-delta background occupies are skipped.

Known deployment-side simplifications the audit flagged but this pass
deliberately kept (documented rather than changed): a task that
*succeeds* with network delay > Tmax is still reported as a success at
deployment (Eq. 2 would call it a network failure), and A2C/A3C are
served frozen and greedy rather than with stochastic on-policy updates.

`train_meta.py` also writes `checkpoints/train_meta_metrics.csv`: one row
per outer iteration with the greedy-eval average reward, the stochastic
policy's mean entropy over the eval states, the current entropy
temperature alpha, and the greedy action counts per action -- enough to
watch a collapse-onto-one-action forming *during* training (entropy
sliding toward 0, one action column absorbing every step) instead of
discovering it afterwards from the served policy's behavior. The script
prints a summary of the first/last row and warns explicitly if the final
greedy policy used only a single action over its eval rollout.

### Automatic entropy-temperature tuning

The paper fixes the SAC entropy temperature at tau=0.2 (Table II); this
implementation instead *learns* it online (config.py's
`AUTO_ENTROPY_TUNING`, on by default; log-alpha initialized at the paper's
0.2 and updated with the standard learned-temperature rule -- Haarnoja et
al. 2018, discrete form per Christodoulou 2019) to hold the policy's
entropy near `TARGET_ENTROPY_SCALE * ln|A|`. Motivation: with the fixed
tau, README's "Convergence" section documents the policy fully collapsing
onto the device action at larger training budgets -- once collapsed, the
fixed entropy bonus is a constant the actor gradient can't recover from,
while a learned alpha grows exactly when entropy dips below target and
actively re-flattens the policy. The learned log-alpha is carried in the
same parameter dict as the network weights, so Reptile meta-learns the
temperature alongside them and it round-trips through checkpoints;
checkpoints saved before this feature existed (no "alpha" entry) still
load, keeping alpha at its init. Set `AUTO_ENTROPY_TUNING = False` to
recover the paper's fixed-tau behavior exactly.

### Delay-sensitivity-weighted reward

The four real app profiles carry a `delay_sensitivity` value
(applications.xml: AR 0.9, HEALTH 0.7, INFOTAINMENT 0.3, HEAVY_COMP 0.1)
that the paper's reward (-T on success, -(Tmax+1) on failure) ignored --
an AR frame and a batch job were penalized identically per second of
service time. Rewards (both branches, so each app's internal
success-vs-failure ordering is preserved) are now scaled by
`w = DELAY_SENSITIVITY_BASE + delay_sensitivity` (0.5 + s, i.e. x0.6 to
x1.4): the same slow tier costs a delay-sensitive app ~2.3x more reward
than an insensitive one, giving the agent a gradient toward app-aware
tier choices instead of one-size-fits-all. The Java side mirrors the same
weighting in `ReSACOMobileDeviceManager.reportReSACOOutcome()` (via
`ReSACOStateBuilder.delaySensitivityWeight()`), so the bridge's
online-learning rewards (Algorithm 4) stay on the training scale --
change `DELAY_SENSITIVITY_BASE` in both `resaco/config.py` and
`ReSACOStateBuilder.java` or neither.

## Reproducibility

Every script's `--seed` now actually pins the run: `resaco/seeding.py`'s
`seed_everything()` seeds Python's global `random`, numpy, and torch at
startup.

This was a real bug, not a nicety. The seed was threaded into scenario
sampling and each environment's `random.Random(seed)`, which made runs
*look* seeded -- but `ReplayBuffer.sample()` draws its minibatch indices
from the **global** `random` module (seeded from OS entropy at import),
and torch's global RNG drives network initialization and the actor's
action sampling. Neither was pinned, so `python scripts/train_meta.py
--seed 42` produced a different `theta_star.pt` on every invocation.
Verified before/after by running the same 30x50 meta-training workload in
three separate processes: three different results before, byte-identical
results after. `tests/test_seeding.py` guards it.

## Performance

The training/serving hot paths were profiled (cProfile over 30 outer x 50
inner = 1500 SAC updates) and optimized. Measured with interleaved
min-of-7 timings, since this machine drifts ~30% over a run:

| Hot path | Before | After | Speedup |
|---|---|---|---|
| `ReplayBuffer.sample()` at 100k transitions, x1000 | 142.6ms | 21.9ms | **6.5x** |
| target soft update (Eq. 13), x5000 | 372.5ms | 217.2ms | **1.7x** |
| `Actor.sample()` per env step, x20000 | 1330ms | 1203ms | 1.1x |

- **`ReplayBuffer` is now columnar numpy instead of a `deque` of tuples.**
  A deque is a linked structure, so each of the 64 random index lookups
  walked it (O(n) each) and the sampled tuples were then re-assembled
  into arrays with `map(np.array, zip(*batch))` on *every* update. The
  ring buffer makes sampling a flat fancy-index, independent of fill
  level -- which is what the deployment bridge, whose buffer reaches the
  full 100k, actually runs. Sampling stays **bit-identical**:
  `random.sample`'s index selection depends only on population size and
  k, so the same logical positions are drawn, and `_physical()` maps them
  onto the ring preserving deque eviction order (tested directly against
  a `deque` reference in `tests/test_replay_buffer.py`).
- **The target soft update is two batched `torch._foreach_*` kernels**
  instead of a `mul_`/`add_` pair per parameter tensor (24 kernel
  launches per update). Bit-identical -- verified against a pre-change
  snapshot. Note `_foreach_lerp_` would *not* be (it rounds differently).
- **`Actor.sample()` no longer computes a log-probability every caller
  threw away.** All three call sites used `action, _, _`; the update
  paths that need log-probs recompute them on the full batch anyway.
- **The temperature update reuses the actor update's forward pass**
  instead of running the actor over the same batch a third time (27
  instead of 30 linear layers per update). This is also the reference
  formulation (Haarnoja et al. 2018 computes log pi once per update and
  shares it between the policy and temperature losses). It is the one
  change here that is *not* bit-identical -- the temperature now sees the
  policy its own loss was measured against, rather than the
  post-gradient-step policy; measured drift after 1500 updates is
  ~3e-6 per weight.
- **Java, per simulated task:** the 128 KB link-delay probe task is
  allocated once and shared (it was 4 fresh objects per probe, 3 probes
  per state build, 2 state builds per task -- 24 throwaway objects per
  task), manager/clock lookups are hoisted out of the 10-slot edge
  utilization loop, and the network-wide `getAvgUtilization()` -- which
  walks every host's every VM and is only the *filler* for edge slots
  beyond the configured host count -- is now computed lazily, so with the
  paper's ten hosts it never runs at all.

Two things were measured and deliberately **not** adopted:
`torch.optim.Adam(foreach=True)` is slower on CPU for these small MLPs
(7.5s vs 4.6s on the training benchmark) *and* numerically different, and
skipping the softmax in `act_greedy` (argmax is invariant to it) risks a
different tie-break in the deployment path for ~1us.

Also fixed: `test.sh` and every `scripts/*/compile.sh` **and
`runner.sh`** hard-coded `:` as the classpath separator, so none of them
worked on Windows -- `javac` reported "package does not exist" for every
jar, and `runner.sh` would have failed at run time with
`NoClassDefFoundError` even after a successful compile. They now pick
`;` under MSYS/Git Bash (verified by running a real short simulation
end to end).

## Convergence (Fig. 5 reproduction)

```
python scripts/plot_convergence.py
```

Reproduces Section V-B's convergence comparison: starting from a new,
held-out test scenario (not in `train_meta.py`'s training pool), it
continually adapts for 300 episodes (each episode = 50 SAC-Update steps,
matching "each episode represents one Outer Loop iteration") from two
initializations -- `theta_star.pt` (meta-init) vs. a fresh random SAC
agent (random-init) -- tracking the greedy-evaluation reward after every
episode, min-max normalized onto the same 0-1 scale as the paper's Fig. 5.
Saves `checkpoints/convergence.png` + `.csv`.

**Result, run locally:** the two curves still do **not** clearly reproduce
the paper's shape (meta-init starting higher and converging within a
handful of episodes) -- at the paper's own Table II defaults (K=300, N=50,
M=10), meta-init and random-init end up statistically close on a fresh
held-out scenario (overall mean reward across 300 episodes: meta-init
-1.95 vs. random-init -1.92 in one local run). This is no longer because
training silently does nothing (see "Fixed" below) -- it now measurably
learns -- but M=10 meta-training scenarios refined by Reptile's
deliberately small per-iteration step (`META_LR`) just isn't much signal
to generalize from onto a *new* scenario at this budget. A local test with
an 8x larger budget (`python scripts/train_meta.py --outer 1200 --inner
100`) did produce a policy with a much lower failure rate against a fixed
evaluation set (0.6% vs. 19.7% at the K=300/N=50 default) -- but it also
fully collapsed onto always picking the device tier regardless of state,
which avoids contention-driven failures but forfeits the throughput edge
and cloud offer when they're *not* contended. Getting a genuinely
scenario-adaptive policy (not collapsed to one dominant action either way)
most likely needs some combination of a larger/more diverse meta-training
scenario pool, entropy-coefficient tuning, or reward shaping -- none of
which this pass attempted. The script itself is a correct, working
reproduction of the paper's *procedure*.

**Fixed this pass** (previously the training pipeline was silently
producing an untrained network no matter how long you ran it -- see
`resaco/sac.py`, `resaco/env.py`, `resaco/normalize.py`,
`resaco/config.py`):
- `sac_update_loop`'s inner-loop transition count (N=50, Algorithm 2) was
  smaller than `BATCH_SIZE` (64), so `update()` silently never fired
  during Reptile's Inner Loop -- every outer iteration's `theta_k` came
  back byte-identical to `theta`, so `theta_star.pt` was just its random
  initialization after all 300 outer iterations, no matter what. Fixed by
  warming the replay buffer up to `batch_size` *before* starting the N
  counted (and now guaranteed-real) update steps.
- The raw state vector mixes wildly different physical scales (task
  length ~1500-10000, utilization 0-150%, bandwidth 0-200 Mbps) with no
  normalization, feeding straight into a plain MLP -- in practice this
  caused the actor to collapse onto one or two fixed actions almost
  independent of the actual state. Fixed via `resaco/normalize.py`, a
  small fixed (not learned) per-feature scale applied identically at
  train and serve time. Isolated before/after test on one fixed
  high-contention scenario: 34.2% task failure rate before
  normalization, 0.2% after, with the agent correctly learning to prefer
  the device tier once state scale stopped drowning it out.
- The device (mobile) tier incorrectly incurred the same WLAN transfer
  delay as the edge tier, even though local execution never sends data
  anywhere -- this alone made device strictly dominated by edge in nearly
  every case. Fixed: local execution now has zero network delay.
- `number_of_mobile_devices` was sampled into every scenario but never
  actually consumed anywhere in `env.py` -- a 200-device and a
  2,000-device scenario looked identical to the agent. Fixed by injecting
  a Poisson-sampled batch of background tasks from the scenario's other
  devices into the shared edge/cloud pools every step (see
  `env._inject_background_load`), so `mu_edge`/`mu_cloud` -- which the
  agent does observe -- now actually rise with device count.
- DDPG/A2C/A3C blindly reused SAC's `ACTOR_LR`/`CRITIC_LR`. Fixed: DDPG
  now gets its own (faster-critic) learning rates typical for that
  algorithm family (`DDPG_ACTOR_LR`/`DDPG_CRITIC_LR`), and A2C/A3C share
  their own on-policy learning rate (`A2C_LR`) instead.

## Compare (fast, toy environment)

```
python scripts/compare_algorithms.py
```

Sweeps mobile-device count 200-2,000 (step 200) and reports completion
rate, average service/processing time, network delay, and failure
breakdown per algorithm, mirroring the paper's Fig. 6/7 and Table III.
Writes `checkpoints/comparison.csv`. Runs in seconds, but see the caveat
below -- for a real comparison, use the EdgeCloudSim route instead.

The printed "Table III style" summary reports ReSACO's service-time
improvement as a relative percentage (a duration, essentially never at/near
zero, so relative-percent is meaningful there), but its network/VM failure
rate improvement as an absolute **percentage-point** difference (`pp`) --
those are already bounded in [0, 100], and a relative-percent formula blows
up whenever a baseline's rate is at or near zero (division by ~0), which is
common here since network failures never happen in this env.

**Caveat:** `env.py` is a compact, self-contained reimplementation of the
paper's state/action/reward formulas (Section III) used to train and
compare agents quickly -- it is *not* the full CloudSim discrete-event
simulation. The device-count sweep now injects real background contention
into the shared edge/cloud pools proportional to device count (not just an
interarrival-time scaling factor -- see the "Fixed this pass" list above),
and the resulting trend is qualitatively correct: completion rate falls
smoothly as device count rises (ReSACO/SAC/DDPG go from ~90% completion at
200 devices to ~47% at 2,000; A2C/A3C, served frozen/on-policy, degrade
much more gracefully, ~100% down to ~80-99%).

That said, the comparison numbers still do **not** reproduce the paper's
specific finding that ReSACO wins across the board -- at the paper's
Table II default training budget, ReSACO/SAC/DDPG actually show the
*highest* VM-failure rates of the five (e.g. 53% vs. A3C's 0.4% at 2,000
devices), consistent with the same under-trained-at-default-budget
limitation described in "Convergence" above. This pipeline is a correct,
runnable reproduction of the algorithms and evaluation *structure*, with a
now-meaningful device-count contention signal; matching the paper's
specific quantitative claims needs more meta-training budget/tuning than
the fast, paper-matched defaults spend by design. Scaling up
`--steps`/`--outer`/`--inner` would be the next step toward a truer
reproduction.

## Compare (real CloudSim simulation, via EdgeCloudSim)

```
python bridge/inference_server.py          # loads all 5 checkpoints from checkpoints/
```

Then, from `EdgeCloudSim/scripts/ReSACO/`, run the ReSACO application
(see `EdgeCloudSim/README.md`'s "ReSACO" section) with
`orchestrator_policies=RESACO,SAC_BASELINE,DDPG_BASELINE,A2C_BASELINE,A3C_BASELINE`
(the default). EdgeCloudSim runs each policy as its own scenario over the
real CloudSim discrete-event engine, so this drives all five algorithms
through identical device/edge/cloud topologies, network models and task
workloads -- a far more faithful comparison than the toy `env.py` sweep
above. Results land as regular EdgeCloudSim per-policy log/CSV files
(`scripts/ReSACO/output/...`), one directory per policy.

**Fixed this pass:** `ReSACOStateBuilder.build()` (Java side) was sending
`b_wlan`/`b_man`/`b_wan` straight from `SimSettings.getWlanBandwidth()` and
friends -- whose JavaDoc claims "Mbps unit" but which actually return the
config file's Mbps value pre-multiplied by 1000 for internal Kbps-based
delay math. `resaco/config.py` and everything trained against it
(including `normalize.py`'s fixed scale factors) are in Mbps, so this real
EdgeCloudSim-integrated path -- the one this whole project exists to
enable -- was feeding the served policy bandwidth values ~1000x anything
it ever saw during training, on 3 of its 18 state dimensions, on every
single decision. Fixed by dividing by 1000 in `ReSACOStateBuilder.java`.
Verified both that it compiles and, by running a real scenario against a
logging stand-in bridge, that the wire values now read `200.0 0.0 15.0`
(Mbps) instead of `200000.0 0.0 15000.0` (Kbps).

**Per-edge-host state + routing:** `ReSACOStateBuilder` previously filled
all `RESACO_NUM_EDGE_SLOTS` edge slots with the *network-wide average*
edge utilization, and `ReSACOEdgeOrchestrator` collapsed every edge
action (1..N) to a generic least-loaded dispatch -- so in real deployment
the model couldn't distinguish edge servers at all, and which edge action
it picked changed nothing (half the action space was decorative). Slot e
now carries edge host e's own average VM utilization (the per-host
analogue of `getAvgUtilization()`, matching `env.py`'s per-server `mu_e`
training state), and edge action e routes to host e-1 specifically:
`getVmToOffload()` first searches only that host's VMs (least-loaded
within the host) and widens to the old global least-loaded search only if
the chosen host has no VM with capacity -- graceful degradation instead
of failing a task the edge tier as a whole could still serve. Since host
choice determines the WLAN-vs-MAN network path (a host on another WLAN
goes through the neighbor-edge relay), the policy's per-server choice now
has real delay consequences, not just load-balancing ones.
`edge_devices.xml` now defines exactly **ten** edge datacenters -- the
paper's Section V-A-2 device configuration (it previously had 14, which
both deviated from the paper and left 4 hosts invisible to the policy's
10 state slots/actions) -- so slots, actions, and hosts map 1:1. The code
still handles a mismatched config gracefully: extra hosts beyond the slot
count stay reachable through the widening fallback, and fewer hosts than
slots pads the leftover slots with the network average.

Separately (not fixed this pass): even with the unit corrected, these
values are still *static* -- read once from the config file, never
reflecting live network congestion -- whereas `env.py`'s training-time
`_bandwidth()` randomly degrades bandwidth every step, so the model
learned to treat bandwidth *variation* as informative. In real deployment
these 3 dimensions are constant, so that signal is simply unavailable
there. `ThreeTierNetworkModel` does track live per-access-point client
counts (`wlanClients[]`/`wanClients[]`) that feed its own MM1-queue delay
model -- surfacing a congestion proxy derived from those instead of the
static config value would be the natural next step.

## Serve to EdgeCloudSim (Deployment Phase / Algorithm 4)

`bridge/inference_server.py` loads whichever of the five checkpoints exist
under `checkpoints/` (`theta_star.pt`, `sac_no_meta.pt`, `ddpg.pt`,
`a2c.pt`, `a3c.pt`) and serves them all simultaneously, selected per
request by an `<algo>` name (`RESACO`, `SAC_BASELINE`, `DDPG_BASELINE`,
`A2C_BASELINE`, `A3C_BASELINE`) that EdgeCloudSim's `ReSACOEdgeOrchestrator`
fills in from its own `orchestrator_policies` config value -- so one bridge
process backs every policy in a single EdgeCloudSim run. Missing
checkpoints are served as randomly-initialized (untrained) policies rather
than refused, so the simulation stays runnable even before every baseline
is trained.

Every task's offloading decision is sent to the bridge over TCP (`ACT`),
and every task's real outcome is reported back (`OUTCOME`). For the
off-policy algorithms (ReSACO, SAC, DDPG) this triggers an incremental
online update (Algorithm 4); the on-policy baselines (A2C, A3C) don't have
a well-defined single-transition update rule, so they're served frozen --
exactly the policy `train_baselines.py` produced. If the bridge or the
requested algorithm's checkpoint is unavailable, EdgeCloudSim falls back to
a static EDGE_PRIORITY-style heuristic instead of crashing the simulation.
`ReSACOBridgeClient` also applies a 10s read timeout on every request, so a
bridge that's up but hung (as opposed to down/refusing connections, which
was already handled) can't block the simulation forever either -- a
timed-out request is treated the same as any other connection failure.

The bridge server itself locks per-algo, not with one lock shared across
all five -- RESACO/SAC_BASELINE/DDPG_BASELINE/A2C_BASELINE/A3C_BASELINE
each have completely independent state (own networks, own replay buffer),
so there was never a reason for e.g. RESACO's training step or its
autosave's blocking `torch.save()` to hold up a concurrent `ACT`/`OUTCOME`
for a different algo. A single global lock did exactly that; each algo
now only ever waits on its own lock.

Protocol (newline-delimited, one request per line):

```
ACT <algo> <request_id> <L> <U> <D> <mu_d> <mu_e1> ... <mu_eN> <mu_c> <bwlan> <bman> <bwan>
    -> "<action_int>"        0=device, 1..N=edge server, N+1=cloud
                              <algo> in {RESACO, SAC_BASELINE, DDPG_BASELINE, A2C_BASELINE, A3C_BASELINE}

OUTCOME <algo> <request_id> <reward> <done:0|1> <next_state...>
    -> "OK" | "IGNORED"      IGNORED means request_id was never seen by ACT for this algo
                              (e.g. the bridge was down/restarted at decision time)

SAVE [<algo> [<path>]]
    -> "OK" | "ERROR ..."    no args: flush every persist-capable algo's live params to its
                              own "<checkpoint>_adapted.pt" (same thing autosave does)
                              algo only: flush just that algo's live params to its own adapted
                              path (ERROR if that algo has nothing to persist, e.g. A2C/A3C)
                              algo + path: dump that algo's current params to an arbitrary path

RESET <algo>
    -> "OK" | "ERROR ..."    Algorithm 4 line 1 for a new scenario: re-copy the originally
                              loaded checkpoint into theta_adapt, clear the replay buffer and
                              in-flight state. Sent automatically by ReSACOEdgeOrchestrator at
                              the start of every simulation run.
```

## Online-learning persistence

`DeploymentAgent` (ReSACO, SAC_BASELINE, DDPG_BASELINE -- the three
off-policy algorithms) keeps adapting `theta_adapt` in memory from every
`OUTCOME` the bridge receives. Without saving that anywhere, all of it
would be lost the moment the bridge process restarts, which would make
Algorithm 4's "online" adaptation pointless in practice. Instead:

- Every `--autosave-every` successful updates (default 50), the adapting
  agent flushes its current params to `checkpoints/<name>_adapted.pt` --
  e.g. `theta_star.pt` -> `theta_star_adapted.pt`. The original
  meta-trained/baseline-trained checkpoint is **never** overwritten, so
  it always stays available as a known-good fallback.
- On startup, `load_agents()` prefers the `*_adapted.pt` file over the
  original if one exists, so online adaptation accumulates across
  restarts instead of resetting to `theta_star.pt` every time. The
  startup log distinguishes `Resumed online-adapted checkpoints for: ...`
  from `Loaded trained checkpoints for: ...` so it's obvious which each
  algo did.
- On a clean shutdown (Ctrl+C, or `SIGTERM` on Linux/Mac), the bridge
  saves every agent one more time before exiting, so at most
  `autosave_every - 1` updates' worth of progress can ever be lost.
- A2C/A3C are served frozen (`FrozenPolicyAgent`) and never adapt, so
  they have nothing to persist -- `SAVE A2C_BASELINE`/`A3C_BASELINE`
  (with no explicit path) returns an error by design.

To reset online learning and go back to the original trained policy,
just delete the relevant `checkpoints/*_adapted.pt` file(s) and restart
the bridge.

```
python bridge/inference_server.py --autosave-every 50   # default; lower it for faster testing
```

## Known limitations

- `env.py` models a single agent-controlled device's task stream against
  shared edge/cloud capacity pools; other devices are represented as
  injected background load (see "Fixed this pass" above), not as literally
  simulated independent agents. It is a research/training aid, not a
  literal multi-agent discrete-event simulation.
- At the paper's Table II default training budget (M=10, K=300, N=50),
  the resulting policies (ReSACO and, to a lesser extent, SAC/DDPG) don't
  yet reliably show either a clear meta-init-vs-random-init advantage or a
  clear win over the on-policy baselines -- see "Convergence" and "Compare"
  above for the concrete numbers and what a larger budget does to them.
  The training pipeline itself is verified correct (gradients genuinely
  flow, and an isolated test showed the agent correctly learning to prefer
  the device tier when contention makes it the better choice); reaching
  paper-competitive numbers needs more meta-training budget/tuning than
  the fast, paper-matched defaults spend by design.
- `a3c.py` uses Python threads sharing one process (not
  `torch.multiprocessing`), so it's algorithmically faithful to A3C's
  shared-model/async-gradient structure but doesn't get true multi-core
  parallelism (the GIL serializes it).
- The EdgeCloudSim bridge (`ReSACOBridgeClient`) and the Python
  `DeploymentAgent._pending` dict accumulate a handful of orphaned entries
  for tasks still in-flight when a scenario's simulation clock is cut off
  before they complete; negligible over a normal experiment run, but if
  the bridge process is kept alive across many long-running experiments,
  consider restarting it periodically.
