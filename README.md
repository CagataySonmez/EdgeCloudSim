# EdgeCloudSim

EdgeCloudSim provides a simulation environment specific to Edge Computing scenarios where it is possible to conduct experiments that considers both computational and networking resources. EdgeCloudSim is based on CloudSim but adds considerable functionality so that it can be efficiently used for Edge Computing scenarios. EdgeCloudSim is an open source tool and any contributions are welcome. If you want to contribute EdgeCloudSim, please check below feature list and the [contributing guidelines](/CONTRIBUTING.md). If you want to use EdgeCloudSim in your research work, please cite our paper [[3]](https://onlinelibrary.wiley.com/doi/abs/10.1002/ett.3493).

## Environment
Ubuntu 20.0.4.6


## PREREQUISITE 
```
sudo apt-get update
```
```
sudo apt-get upgrade
```
```
sudo apt install default-jre default-jdk maven
```
## Discussion Forum

The discussion forum for EdgeCloudSim can be found [here](https://groups.google.com/forum/#!forum/edgecloudsim).
We hope to meet with all interested parties in this forum.
Please feel free to join and let us discuss issues, share ideas related to EdgeCloudSim all together.

## YouTube Channel

The YouTube channel of EdgeCloudSim can be found [here](https://www.youtube.com/channel/UC2gnXTWHHN6h4bk1D5gpcIA).
You can find some videos presenting our works and tutorials on this channel.
Click [here](https://youtu.be/SmQgRANWUts) to watch the video with brief information about EdgeCloudSim.

## Needed Features

* Task migration among the Edge or Cloud VMs
* Energy consumption model for the mobile and edge devices as well as the cloud datacenters
* Adding probabilistic network failure model by considering the congestion or other parameters such as the distance between mobile device and the WiFi access point.
* Visual tool for displaying the network topology

# EdgeCloudSim: An Environment for Performance Evaluation of Edge Computing Systems

EdgeCloudSim provides a modular architecture to provide support for a variety of crucial functionalities such as network modeling specific to WLAN and WAN, device mobility model, realistic and tunable load generator. As depicted in Figure 2, the current EdgeCloudSim version has five main modules available: Core Simulation, Networking, Load Generator, Mobility and Edge Orchestrator. To ease fast prototyping efforts, each module contains a default implementation that can be easily extended.

<p align="center">
  <img src="/doc/images/edgecloudsim_diagram.png" width="55%">
  <p align="center">
    Figure 1: Relationship between EdgeCloudSim modules.
  </p>
</p>

## Mobility Module
The mobility module manages the location of edge devices and clients. Since CloudSim focuses on the conventional cloud computing principles, the mobility is not considered in the framework. In our design, each mobile device has x and y coordinates which are updated according to the dynamically managed hash table. By default, we provide a nomadic mobility model, but different mobility models can be implemented by extending abstract MobilityModel class.

<p align="center">
  <img src="/doc/images/mobility_module.png" width="55%">
</p>

## Load Generator Module
The load generator module is responsible for generating tasks for the given configuration. By default, the tasks are generated according to a Poisson distribution via active/idle task generation pattern. If other task generation patterns are required, abstract LoadGeneratorModel class should be extended.

<p align="center">
  <img src="/doc/images/task_generator_module.png" width="50%">
</p>

## Networking Module
The networking module particularly handles the transmission delay in the WLAN and WAN by considering both upload and download data. The default implementation of the networking module is based on a single server queue model. Users of EdgeCloudSim can incorporate their own network behavior models by extending abstract NetworkModel class.

<p align="center">
  <img src="/doc/images/network_module.png" width="55%">
</p>

## Edge Orchestrator Module
The edge orchestrator module is the decision maker of the system. It uses the information collected from the other modules to decide how and where to handle incoming client requests. In the first version, we simply use a probabilistic approach to decide where to handle incoming tasks, but more realistic edge orchestrator can be added by extending abstract EdgeOrchestrator class.

<p align="center">
  <img src="/doc/images/edge_orchestrator_module.png" width="65%">
</p>

## Core Simulation Module
The core simulation module is responsible for loading and running the Edge Computing scenarios from the configuration files. In addition, it offers a logging mechanism to save the simulation results into the files. The results are saved in comma-separated value (CSV) data format by default, but it can be changed to any format.

## Extensibility
EdgeCloudSim uses a factory pattern making easier to integrate new models mentioned above. As shown in Figure 2, EdgeCloudsim requires a scenario factory class which knows the creation logic of the abstract modules. If you want to use different mobility, load generator, networking and edge orchestrator module, you can use your own scenario factory which provides the concrete implementation of your custom modules.

<p align="center">
  <img src="/doc/images/class_diagram.png" width="100%">
  <p align="center">
    Figure 2: Class Diagram of Important Modules
  </p>
</p>

## Ease of Use
At the beginning of our study, we observed that too many parameters are used in the simulations and managing these parameters programmatically is difficult.
As a solution, we propose to use configuration files to manage the parameters.
EdgeCloudSim reads parameters dynamically from the following files:
- **config.properties:** Simulation settings are managed in configuration file
- **applications.xml:** Application properties are stored in xml file
- **edge_devices.xml:** Edge devices (datacenters, hosts, VMs etc.) are defined in xml file

<p align="center">
  <img src="/doc/images/ease_of_use.png" width="60%">
</p>

## Compilation and Running
To compile sample application, *compile.sh* script which is located in *scripts/sample_application* folder can be used. You can rewrite similar script for your own application by modifying the arguments of javac command in way to declare the java file which includes your main method. Please note that this script can run on Linux based systems, including Mac OS. You can also use your favorite IDE (eclipse, netbeans etc.) to compile your project.

In order to run multiple sample_application scenarios in parallel, you can use *run_scenarios.sh* script which is located in *scripts/sample_application* folder. To run your own application, modify the java command in *runner.sh* script in a way to declare the java class which includes your main method. The details of using this script is explained in [this](/wiki/How-to-run-EdgeCloudSim-application-in-parallel) wiki page.

You can also monitor each process via the output files located under *scripts/sample_application/output/date* folder. For example:
```
./run_scenarios.sh {# of parallel Processes} {# of iteration}
tail -f output/date/ite_1.log
```
# Repository layout

Two rules keep this repo navigable as more algorithms and scenarios get
added:

**1. Every EdgeCloudSim application lives under
`src/edu/boun/edgecloudsim/applications/`** -- one package per
application, each with its own `MainApp`/`ScenarioFactory`, and a
matching `scripts/<application>/` folder holding its `compile.sh`,
`runner.sh`, `simulation.list` and `config/`. Currently: `fuzzy`,
`motivation2`, `resaco`, `sample_app1`-`sample_app5`,
`scenario1`-`scenario5`, `three_tier`. They all share the
single `src/edu/boun/edgecloudsim/` simulator core, so CI compiles every
one of them on each push (see `.github/workflows/tests.yml`) -- a change
to the core that breaks an application nobody happened to rebuild fails
the build instead of rotting silently.

**2. The Python side is split by concern, not by algorithm:**

| Folder | Holds |
|---|---|
| `mec_core/` | shared, algorithm-agnostic MEC infrastructure: the offloading environment, scenario/app profiles, networks, replay buffer, normalization, config, seeding |
| `baselines/` | the non-ReSACO learners (DDPG, A2C, A3C) |
| `ReSACO/` | **only** the ReSACO algorithm (SAC-Update, the Reptile loops, the Deployment Phase) plus its serving bridge, training scripts and tests |

So adding or comparing another offloading algorithm means adding a module
under `baselines/` (or a sibling folder) -- never editing `ReSACO/`. See
[ReSACO/README.md](ReSACO/README.md) for the ReSACO specifics.

# To make new scenario
## Change following files
**scripts/{scenario_name}** 
  1. compile.sh
  2. runner.sh
  3. matlab/getConfiguration.m

**src/edu/boun/edgecloudsim**
  1. Change all .java file's pakage to corresponding scenario_name
  2. In MainApp.java, change SCENARIO_NAME into corresponding scenario_name


# Scenario Descriptions

## Three-Tier
| Policy                | Description                                                                                                                                                           |
|-----------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ONLY_MOBILE           | - Tasks are processed only on the mobile device                                                                                                                        |
| ONLY_EDGE             | - Tasks are processed only on the edge server                                                                                                                          |
| ONLY_CLOUD            | - Tasks are processed only on the cloud                                                                                                                                |
| UTILIZATION_BASED      | - Considers only edge server utilization.<br>- If edge utilization > 80%:<br>&nbsp;&nbsp;&nbsp;- Offload to the cloud if bandwidth is high (wanBW > 2)<br>&nbsp;&nbsp;&nbsp;- Keep task on mobile if bandwidth is low.<br>- If edge utilization ≤ 80%: use the edge server. |
| NETWORK_BASED          | - Considers only network delay and bandwidth.<br>- If bandwidth > 5: offload to the cloud.<br>- If bandwidth > 2: offload to the edge server.<br>- If bandwidth ≤ 2: keep the task on mobile. |
| RANDOM                 | - Randomly assigns tasks to one of the following:<br>&nbsp;&nbsp;&nbsp;- Mobile device<br>&nbsp;&nbsp;&nbsp;- Edge server<br>&nbsp;&nbsp;&nbsp;- Cloud. |
| EDGE_PRIORITY          | - Prioritizes the edge server.<br>- If bandwidth > 6:<br>&nbsp;&nbsp;&nbsp;- Offload to edge server if utilization ≤ 90%<br>&nbsp;&nbsp;&nbsp;- Offload to cloud if edge utilization > 90%.<br>- If bandwidth > 3:<br>&nbsp;&nbsp;&nbsp;- Offload to cloud if edge utilization > 90%<br>&nbsp;&nbsp;&nbsp;- Offload to mobile if edge utilization < 20%<br>&nbsp;&nbsp;&nbsp;- Otherwise, offload to edge server.<br>- If bandwidth ≤ 3: keep task on mobile. |

## ReSACO

A separate application (`scripts/ReSACO`, `src/edu/boun/edgecloudsim/applications/resaco`)
that delegates offloading decisions to trained RL policies served by a
Python bridge process (see [ReSACO/README.md](ReSACO/README.md)),
instead of a hand-written heuristic. It reuses `three_tier`'s
`ThreeTierNetworkModel` and `ThreeTierMobileServerManager` unchanged;
`three_tier` itself is untouched.

The bridge serves five algorithms from the ReSACO paper's Section V-C
comparison at once, each selectable as its own `orchestrator_policies`
entry -- listing all five (the default in `scripts/ReSACO/config/default_config.properties`)
runs all five through the *same* real CloudSim simulation (topology,
network model, workload) for a like-for-like comparison, not just ReSACO
on its own.

| Policy         | Description                                                                                                                                                     |
|----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| RESACO         | Reptile-meta-trained SAC (Algorithm 4 of the paper). Off-policy: keeps adapting online from real reported outcomes. |
| SAC_BASELINE   | Plain SAC, no meta-initialization. Off-policy, also adapts online. |
| DDPG_BASELINE  | DDPG, adapted to the discrete offload action space. Off-policy, also adapts online. |
| A2C_BASELINE   | Synchronous Advantage Actor-Critic. On-policy: served frozen, exactly as trained by `train_baselines.py` (on-policy methods don't have a well-defined single-transition online update). |
| A3C_BASELINE   | Asynchronous A3C. On-policy: served frozen, same as A2C_BASELINE. |

For every task, `ReSACOEdgeOrchestrator` sends the current state (task
size, device utilization, *per-edge-host* utilization -- one state slot
per edge host, so the policy can tell edge servers apart -- cloud
utilization, network bandwidth) to whichever algorithm the active policy
names, over TCP, and offloads to whatever it returns: the device, the
cloud, or the *specific* edge host the chosen edge action names (falling
back to a global least-loaded edge dispatch only if that host has no VM
capacity left). The task's real outcome is reported back afterwards as a
delay-sensitivity-weighted reward (matching the training environment's
reward scale -- see ReSACO/README.md's "Delay-sensitivity-weighted
reward"). If the bridge or the requested algorithm's checkpoint is
unavailable -- or the bridge is up but hung (a 10s read timeout guards
against this too) -- it falls back to a static EDGE_PRIORITY-style
heuristic instead of crashing -- so the simulation is always safe to run
even if you forgot to start the bridge, it just won't be using RL for
that run.

### 1. Train (once) or reuse existing checkpoints

Skip this if `ReSACO/checkpoints/*.pt` already exist.

```
cd ReSACO
python -m venv venv && venv/Scripts/pip install -r requirements.txt   # first time only
venv/Scripts/python scripts/train_meta.py          # -> checkpoints/theta_star.pt (ReSACO)
venv/Scripts/python scripts/train_baselines.py     # -> checkpoints/{sac_no_meta,ddpg,a2c,a3c}.pt
```

### 2. Start the policy bridge (leave this running in its own terminal)

```
cd ReSACO
venv/Scripts/python bridge/inference_server.py
```

Missing checkpoints are served as untrained (random) policies with a
warning rather than refused, so it's fine to start this before every
baseline is trained -- just re-run `train_baselines.py` and restart the
bridge later to pick up the real weights.

RESACO/SAC_BASELINE/DDPG_BASELINE keep adapting online from every task's
real outcome (Algorithm 4) and periodically save that progress to
`checkpoints/<name>_adapted.pt` (default: every 50 updates, tunable via
`--autosave-every`, plus once more on a clean Ctrl+C shutdown) without
ever overwriting the original trained checkpoint. The next time the
bridge starts, it resumes from that adapted file automatically, so a long
EdgeCloudSim run's online learning survives a bridge restart. See
[ReSACO/README.md](ReSACO/README.md)'s "Online-learning persistence"
section for the full protocol and how to reset it.

### 3. Compile and run EdgeCloudSim

```
cd scripts/ReSACO
./compile.sh
```

For a quick single run (edit `min/max_number_of_mobile_devices` in
`config/default_config.properties` down to something small first, e.g.
50, or pass a scaled-down config file):
```
java -classpath '../../bin:../../lib/cloudsim-4.0.jar:../../lib/commons-math3-3.6.1.jar:../../lib/colt.jar' \
  edu.boun.edgecloudsim.applications.resaco.ReSACOMainApp \
  config/default_config.properties config/edge_devices.xml config/applications.xml output/test 1
```

For the full experiment sweep (all 5 policies x every device count in the
config, run in parallel, matching `three_tier`'s workflow):
```
./run_scenarios.sh {# of parallel processes} {# of iterations}
tail -f output/<date>/ite_1.log
```

`simulation.list` runs eleven *scenarios* per sweep, each its own config
preset under `config/` (delete lines from `simulation.list` to run
fewer). The first four run the classic `applications.xml` workload, the
next four run `applications_nextgen.xml` (GenAI inference, XR streaming,
V2X perception, smart-city analytics), and the last three run
`applications_sdv.xml` (software-defined-vehicle fleet: ADAS perception,
HD-map updates, driver monitoring, OTA) across the three canonical
driving environments:

| Scenario line   | Preset                                            | Environment                                  |
|-----------------|---------------------------------------------------|----------------------------------------------|
| `default_config`| baseline three_tier-style values                  | pedestrians, healthy network, standard edge  |
| `high_mobility` | attractiveness dwell times 480/300/120 -> 60/30/15s | fast-moving devices (vehicles/commuters): frequent WLAN handoffs, mobility failures |
| `congested_wan` | `wan_bandwidth` 15 -> 3 Mbps                      | rural/overloaded backhaul: cloud hard to reach |
| `weak_edge`     | `edge_devices_weak_edge.xml`: edge MIPS / 4       | cheap micro-datacenter edge tier             |
| `nextgen_apps`  | default environment + next-gen workloads          | trend workloads on today's infrastructure    |
| `mmwave_5g`     | WLAN 1000 / WAN 50 Mbps, dwell 30/20/10s          | 5G mmWave small cells: huge pipes, constant handoffs |
| `ntn_backhaul`  | `wan_propagation_delay` 0.1 -> 0.6s, WAN 30 Mbps  | LEO-satellite (3GPP NTN) backhaul            |
| `gpu_edge`      | `edge_devices_gpu_edge.xml`: edge MIPS x 4        | GPU/NPU-accelerated AI-first edge            |
| `sdv_highway`   | WLAN 300 / WAN 30 Mbps, dwell 10/8/5s             | vehicles at highway speed past sparse RSU corridors |
| `sdv_rural_road`| WLAN 100 / WAN 8 Mbps, `edge_devices_sdv_rural.xml`: edge MIPS / 2, dwell 60/45/30s | thin rural roadside infrastructure |
| `sdv_urban`     | WLAN 400 Mbps, `edge_devices_sdv_urban.xml`: edge MIPS x 2, dwell 35/25/15s | stop-and-go traffic under dense city MEC |

These mirror the Python training side's `resaco/scenario.py`
`ENV_PROFILES`/`NEXTGEN_APP_PROFILES` (same names, same knobs), which
meta-training now samples across -- so the served policies have actually
trained on these conditions. If you change a preset on one side, change
the other.

Note: the RL-backed policies (RESACO/SAC/DDPG/A2C/A3C) are noticeably
slower wall-clock than static heuristics like EDGE_PRIORITY, since every
single task decision is a real blocking TCP round-trip to the Python
bridge (plus a PyTorch inference, and for the off-policy algorithms a
training step). Budget accordingly for the full device-count sweep.

Results land under `scripts/ReSACO/output/...` in the same per-policy
log/CSV format as `three_tier`.

### Java tests

```
./test.sh          # from the EdgeCloudSim/ repo root
```

Compiles `src/` and `test/`, then runs the JUnit 5 suite via
`lib/junit-platform-console-standalone-*.jar` (no Maven/Gradle in this
project, so this is a plain `javac` + JUnit console launcher script,
mirroring `scripts/ReSACO/compile.sh`'s style). Currently covers
`ReSACOBridgeClient` (the TCP client side of the ReSACO bridge protocol)
against a real local `ServerSocket` standing in for the Python bridge --
connection failure, valid/error responses, wire format, and the 10s read
timeout (that last one takes ~10s to run, by design -- it's asserting the
timeout actually fires rather than mocking it away). ReSACO's own Python
side has its own, separate suite (`ReSACO/tests/`, see
[ReSACO/README.md](ReSACO/README.md)); `test/` here only covers this
repo's Java code, and isn't wired into CI yet (Python's is, via
`.github/workflows/tests.yml`).

# evaluate.py

## Overview
`scripts/evaluate.py` is **shared across every EdgeCloudSim application** in
this repo (`three_tier`, `resaco`, or any future one) -- there is only one
copy, not one per application. It processes simulation results
(`.tar.gz` and `.log` files) generated under `scripts/<app>/output/<DATE>/default_config/`,
organizes and extracts logs into a structured folder hierarchy, converts
them into a single-line format, aggregates the data into CSV files (raw,
sorted, mean), and produces plots automatically or manually.

Since `three_tier` and `resaco`/ReSACO use entirely different
`orchestrator_policies` values (`ONLY_MOBILE`, `EDGE_PRIORITY`, ... vs.
`RESACO`, `SAC_BASELINE`, ...), the plot legend/order is derived at
runtime from whichever policy names actually appear in the selected run's
data (natural-sorted) -- nothing is hardcoded per application, so the same
script works unmodified for both, and for any new application added later.

Requires `scripts/requirements.txt` (`pip install -r scripts/requirements.txt`
-- numpy/matplotlib/pandas/natsort/scienceplots; no dedicated venv, unlike
ReSACO, since this script never needs torch).

Run it with no arguments for an interactive menu, or pass the menu number
or application name to skip straight to it:
```
python scripts/evaluate.py                # interactive menu
python scripts/evaluate.py three_tier      # skip the prompt, evaluate three_tier's results
python scripts/evaluate.py ReSACO          # skip the prompt, evaluate ReSACO's results
python scripts/evaluate.py 1               # same as "ReSACO" -- menu number also works
```
```
실행할 평가 방식을 선택하세요:
1. ReSACO
2. three_tier
3. ReSACO_convergence
4. ReSACO_compare_algorithms
입력 (번호):
```

Options 3/4 run `ReSACO/scripts/plot_convergence.py` /
`compare_algorithms.py` directly (through ReSACO's own venv, since those
need torch) and copy their output artifacts
(`convergence.png`/`.csv`, `comparison.csv`) into this run's
`evaluation_result/` folder alongside the log-based evaluations from
options 1/2 -- see [ReSACO/README.md](ReSACO/README.md) for what those two
scripts actually do.

### Batch/CI runs: `--auto`

By default every step below (which simulation date, which ITEs/policies,
manual vs. automatic plotting) is an interactive prompt. Add `--auto` to
skip all of them and run unattended -- the latest simulation date, every
ITE, every policy, and automatic plotting:
```
python scripts/evaluate.py ReSACO --auto
```
`--auto` requires an explicit choice argument too (there's no sensible
default for *which* application/analysis to evaluate) -- `python
scripts/evaluate.py --auto` alone is a usage error, not "evaluate
everything".

The menu is a plain `EVALUATION_MENU = {"1": {...}, "2": {...}, ...}` dict
at the bottom of the file mapping a number to a display name and a handler
function `(results_dir, auto=False) -> None` -- adding another evaluation
option later means adding one more entry there, no branching logic
elsewhere needs to change.

### Tests

```
cd scripts && pip install -r requirements.txt pytest && pytest tests/
```
Covers the pure logic behind `--auto` (every selector returns "ALL"/latest
without prompting, `--auto` without a choice errors, the menu dict shape).
Doesn't cover `run_app_evaluation`'s file-processing path end-to-end (that
needs real `output/<date>/default_config/` simulation results to run
against); ReSACO's own test suite (`ReSACO/tests/`, see its README) covers
the RL package itself.

All results (CSVs, plots) are saved under
**`scripts/evaluation_result/<name>_<YYYYMMDD>/`** -- `<name>` is the
chosen menu entry's display name and `<YYYYMMDD>` is *today's* date (when
you ran the evaluation), independent of which simulation run's date you
pick in step 1 below. E.g. picking "ReSACO" on 2026-07-16 always writes to
`scripts/evaluation_result/ReSACO_20260716/`, no matter which of
ReSACO's past simulation runs you're evaluating -- re-running the same
choice again the same day overwrites that folder's `logs/`/`graph/`
contents rather than accumulating duplicates.

**Nothing generated by a simulation run or by this script is committed.**
Both the raw simulation output (`scripts/<app>/output/`, matched by the
root `.gitignore`'s `output*` pattern -- which, having no leading `/`,
applies at every directory depth, so one rule covers every application's
`output/` folder without needing a `.gitignore` in each `scripts/<app>/`
directory) and this script's own results
(`scripts/evaluation_result/`) are git-ignored. Only the *code* that
produces them is tracked.

---

## Workflow

0. **Select an Evaluation** -- interactive menu (or skip via CLI arg, see above).

1. **Select a Date Folder**  
   - Reads all subfolders in `scripts/<app>/output/` (the simulation's own
     output, not the evaluation results) that match the date format.
   - Prompts the user to choose one (or pick `0` for the latest date).

2. **Create Result Structure**  
   - Automatically creates `scripts/evaluation_result/<name>_<YYYYMMDD>/logs` and `.../graph`.
   - This is where logs are reorganized and final outputs (CSV, plots) are stored.

3. **Extract & Categorize Logs**  
   - Copies and extracts (`.tar.gz`) files into the logs folder.  
   - Looks for `.log` files and categorizes them under a folder structure based on **Policy** and **Category**.

4. **Load and Filter Logs**  
   - Reads each `.log` into a Pandas DataFrame.  
   - Offers menu selections to filter by ITE, Policy, Category, or select `ALL`.

5. **Save CSV Files**  
   - Creates **raw**, **sorted**, and **mean** CSV files under `scripts/evaluation_result/<name>_<YYYYMMDD>/logs/csv`.

6. **Plot Generation**  
   - **Automatic Mode**: Generates a predefined set of (x, y) plots (usually `x = devices` and various `y` metrics).  
   - **Manual Mode**: User picks columns for X and Y axes interactively.  
   - Saves `.png` plots and their respective data (`.csv`) under `scripts/evaluation_result/<name>_<YYYYMMDD>/graph`, split into `ALL`/`CLOUD`/`EDGE`/`MOBILE`/`OTHERS` subfolders. Policy colors/markers/legend order are whatever policies are present in that run, natural-sorted.

---

