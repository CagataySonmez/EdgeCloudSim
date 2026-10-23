# EdgeCloudSim Experiment and Plotting Guide

This directory contains the reproducible experiment workflow for EdgeCloudSim:
compile the Java sources, run one of the ten bundled applications, monitor or
stop parallel simulations, extract the results, and generate publication-ready
figures with Python or MATLAB.

## Contents

- [The ten bundled applications](#the-ten-bundled-applications)
- [Prerequisites](#prerequisites)
- [Compile EdgeCloudSim](#compile-edgecloudsim)
- [Run an experiment](#run-an-experiment)
- [Monitor and stop an experiment](#monitor-and-stop-an-experiment)
- [Understand and extract the results](#understand-and-extract-the-results)
- [Create figures with Python](#create-figures-with-python)
- [Python configuration reference](#python-configuration-reference)
- [Bundled Python plots](#bundled-python-plots)
- [`plot_generic_line` reference](#plot_generic_line-reference)
- [Generic result-file schema](#generic-result-file-schema)
- [Common plotting combinations](#common-plotting-combinations)
- [Specialized plots](#specialized-plots)
- [Adapt the plotter for a new experiment](#adapt-the-plotter-for-a-new-experiment)
- [Reproducible research checklist](#reproducible-research-checklist)
- [Troubleshooting](#troubleshooting)

## The ten bundled applications

EdgeCloudSim includes ten complete applications. Five reproduce or extend
experiments used in published research, while five are educational tutorials
that isolate common edge-computing design questions.

### Research applications

| Directory | Purpose |
| --- | --- |
| [`sample_app1`](sample_app1/) | Basic edge-computing environment evaluation used in the original conference study. |
| [`sample_app2`](sample_app2/) | Evaluation of single-tier and two-tier cloudlet-assisted applications. |
| [`sample_app3`](sample_app3/) | Extended edge-computing environment evaluation used in the journal study. |
| [`sample_app4`](sample_app4/) | Fuzzy-logic workload orchestration for edge computing. |
| [`sample_app5`](sample_app5/) | Machine-learning workload orchestration for vehicular edge computing. |

The associated publications are listed in the repository's main
[`README.md`](../README.md#publications).

### Tutorials

| Directory | Engineering question |
| --- | --- |
| [`tutorial1`](tutorial1/) | How do VM scheduling and VM allocation policies affect performance? |
| [`tutorial2`](tutorial2/) | What should be offloaded, and at what task granularity? |
| [`tutorial3`](tutorial3/) | Where should a task be offloaded, and which orchestration policy should decide? |
| [`tutorial4`](tutorial4/) | How much server capacity should be provisioned? |
| [`tutorial5`](tutorial5/) | How should network and server capacity be planned together? |

Every application directory contains the same experiment-control interface:

- `compile.sh` compiles the complete EdgeCloudSim source tree.
- `simulation.list` selects one or more configuration sets.
- `run_scenarios.sh` schedules iterations across parallel worker processes.
- `runner.sh` launches the application-specific Java entry point. It is an
  internal worker and normally should not be invoked directly.
- `stop_simulation.sh` safely stops the workers belonging to one simulation ID.
- `config/` contains the simulation properties and XML models.
- `output/` receives generated results and is not source input.

The tutorial directories additionally provide equivalent Python and MATLAB
analysis scripts. The research applications retain their original MATLAB
analysis scripts. All applications use the same generic result-file schema,
so the Python plotter can also be adapted to research-application filenames.

## Prerequisites

For compilation and simulation:

- a Java Development Kit (JDK) version 21 or newer;
- Bash, `tar`, `ps`, and `pgrep`;
- Linux, macOS, or Windows Subsystem for Linux (WSL).

For Python figures:

- Python 3;
- `venv` and `pip`;
- the packages declared in each tutorial's `python/requirements.txt`.

Confirm the principal tools before starting:

```bash
java -version
javac -version
python3 --version
```

The shell workflow is designed for Linux and macOS. On Windows, use WSL so
that process management, path syntax, and the supplied shell scripts behave
consistently.

## Compile EdgeCloudSim

From the repository root, compile every Java source file with:

```bash
./scripts/compile.sh
```

The shared compiler script recreates `bin/`, discovers every `.java` file
under `src/`, and invokes `javac` with all warnings enabled and treated as
errors. The JAR files under `lib/` are added to the classpath automatically.

For convenience, the same full compilation can be started from any application
directory:

```bash
cd scripts/tutorial1
./compile.sh
```

An application-level `compile.sh` delegates to the shared compiler. It does
not compile only that application; this deliberate design catches integration
problems anywhere in the repository before an experiment begins.

## Run an experiment

Enter one application directory and pass two positive integers to
`run_scenarios.sh`:

```bash
cd scripts/tutorial1
./run_scenarios.sh 4 10
```

The arguments are:

1. the maximum number of parallel worker processes;
2. the number of repetitions for every entry in `simulation.list`.

The example requests ten repetitions and distributes the resulting jobs over
at most four workers. The actual number of workers cannot exceed the number of
jobs. Each worker executes its assigned jobs sequentially.

For a short validation run:

```bash
./run_scenarios.sh 2 2
```

Each non-comment line in `simulation.list` has exactly three semicolon-separated
fields:

```text
scenario_name;edge_devices_file;applications_file
```

The files are resolved under the application's `config/` directory. For
example, tutorials 1--4 use one `default_config` entry. Tutorial 5 intentionally
uses three entries (`default_config1`, `default_config2`, and `default_config3`)
to compare three capacity-planning configurations.

Do not run `runner.sh` directly for ordinary experiments. `run_scenarios.sh`
creates collision-resistant output paths, divides work among workers, records
process IDs, and passes the correct application-specific classpath, Java entry
point, configuration files, and iteration number to `runner.sh`. In particular,
sample app 4 requires the fuzzy-logic JAR, while sample app 5 requires WEKA,
MTJ, and its native-library setting; their runners supply these automatically.

### Deterministic runs

Set an explicit seed when a study requires repeatability:

```bash
export JAVA_TOOL_OPTIONS='-Dedgecloudsim.seed=20261018'
./run_scenarios.sh 4 10
```

Use the same seed to reproduce a run exactly. For statistical experiments,
use and report a documented sequence of independent seeds rather than silently
reusing one seed as if the repetitions were independent samples.

## Monitor and stop an experiment

At startup, the script prints a simulation ID similar to:

```text
23-10-2026_14-30-45_12345
```

It also prints the exact paths for monitoring. From the application directory,
the overall progress for one configuration can be followed with:

```bash
tail -f output/<simulation-id>/default_config/progress.log
```

The console output of a particular repetition is stored separately:

```bash
tail -f output/<simulation-id>/default_config/ite1.log
```

If an application has several entries in `simulation.list`, replace
`default_config` with the corresponding first field, such as
`default_config1` in tutorial 5.

Stop all active workers for one run with the ID printed at startup:

```bash
./stop_simulation.sh '<simulation-id>'
```

The stop script checks the recorded process IDs and verifies that each process
belongs to the requested output directory before terminating its process tree.
It does not use a broad Java-process match and therefore avoids stopping an
unrelated simulation or application.

## Understand and extract the results

Each run creates this hierarchy:

```text
<application>/output/
└── <simulation-id>/
    ├── simulation_pids.txt
    ├── tmp_runner0.log
    └── <configuration-name>/
        ├── progress.log
        ├── ite1.log
        ├── ite1.tar.gz
        ├── ite2.log
        └── ite2.tar.gz
```

After an iteration finishes, its result directory is archived as
`iteN.tar.gz`, and the uncompressed `iteN/` directory is removed. The `iteN.log`
file is the Java console log; the archive contains the numeric result files
used by the plotters.

Python and MATLAB analysis scripts expect extracted directories named `ite1`,
`ite2`, and so on. Extract all completed iterations from the selected
configuration directory before plotting:

```bash
cd scripts/tutorial1/output/<simulation-id>/default_config
for archive in ite*.tar.gz; do
    tar -xzf "$archive"
done
```

To extract only one repetition:

```bash
tar -xzf ite1.tar.gz
```

Keep the archives as immutable experiment artifacts. Extracting them does not
alter their contents and makes it possible to regenerate figures later.

## Create figures with Python

The Python tools are self-contained within each tutorial. Create a virtual
environment for the tutorial whose results you are analyzing:

```bash
cd scripts/tutorial1/python
python3 -m venv .venv
. .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

The requirements install NumPy, pandas, Matplotlib, SciPy, and seaborn. A
separate environment per tutorial is optional; one environment can be reused
because the current requirement sets are equivalent.

Next, edit that directory's `config.py`. At minimum:

1. set `folder_path` to the extracted configuration directory;
2. set `num_iterations` to the number of extracted `iteN/` directories;
3. verify the scenario names, device range, and legend labels against the
   simulation configuration that produced the data.

For example, from `scripts/tutorial1/python`, a relative result path may be:

```python
'folder_path': '../output/23-10-2026_14-30-45_12345/default_config/',
'num_iterations': 10,
```

Run plot scripts from their own `python/` directory so relative paths resolve
as documented:

```bash
python plotAvgServiceTime.py
python plotAvgFailedTask.py
python plotTimeComplexity.py
```

When `save_figure_as_pdf` is `True`, figures are written to `folder_path`.
Line-plot filenames use `<row>_<column>_<application-type>.pdf`; specialized
plots use descriptive names such as `edge_delay_reason.pdf` or `position.pdf`.
The interactive Matplotlib window is also displayed. On a headless machine,
configure an appropriate Matplotlib backend or run the scripts in a desktop
session.

## Python configuration reference

Every tutorial's `config.py` returns the following values:

| Key | Meaning |
| --- | --- |
| `folder_path` | Extracted configuration directory containing `ite1/`, `ite2/`, and so on. |
| `num_iterations` | Number of repetitions to aggregate. It must match the extracted directories. |
| `scenario_types` | Scenario tokens embedded in result filenames. Order also determines plot order. |
| `legends` | Display labels corresponding one-to-one with `scenario_types`. |
| `min_devices`, `step_devices`, `max_devices` | Inclusive client/device range read from result filenames. |
| `x_tick_interval` | Show every nth device-count tick. Values below one are normalized to one. |
| `x_axis_label` | Horizontal-axis label. |
| `figure_position` | `[left, bottom, width, height]` in centimetres; width and height determine the Python figure size. |
| `font_sizes` | `[axis-label size, legend size, tick-label size]`. |
| `use_scientific_notation_x_axis` | Enable scientific notation for the horizontal axis. |
| `save_figure_as_pdf` | Save a PDF in addition to displaying the figure. |
| `plot_confidence_interval` | Show 95% Student's *t* confidence intervals when at least two valid repetitions exist. |
| `use_color` | Select color markers or black-and-white publication markers. |
| `colors` | RGB triples used in scenario order. |
| `bw_markers`, `color_markers` | Matplotlib line-and-marker specifications used in scenario order. |

The supplied experiment dimensions are:

| Tutorial | Scenario tokens | Device range | Common application tokens |
| --- | --- | --- | --- |
| 1 | `RANDOM_FIT`, `WORST_FIT`, `BEST_FIT`, `FIRST_FIT`, `NEXT_FIT` | 200--2000, step 200 | `ALL_APPS`, `AUGMENTED_REALITY`, `HEALTH_APP`, `INFOTAINMENT_APP` |
| 2 | `RANDOM`, `MOBILE_UTIL_HEURISTIC`, `EDGE_UTIL_HEURISTIC` | 200--2000, step 200 | `ALL_APPS`, `AUGMENTED_REALITY`, `HEALTH_APP`, `INFOTAINMENT_APP` |
| 3 | `RANDOM`, `NETWORK_BASED`, `UTILIZATION_BASED` | 200--2000, step 200 | `ALL_APPS`, `AUGMENTED_REALITY`, `HEALTH_APP`, `INFOTAINMENT_APP` |
| 4 | `RANDOM_CAPACITY`, `EQUAL_CAPACITY`, `TRAFFIC_HEURISTIC` | 1000--2000, step 100 | `ALL_APPS`, `TRAFFIC_MANAGEMENT`, `DANGER_ASSESSMENT`, `INFOTAINMENT` |
| 5 | `SCENARIO1`, `SCENARIO2`, `SCENARIO3` | 1000--2000, step 100 | `ALL_APPS`; `GENERIC_APPLICATION` when that per-application log is enabled |

These are defaults for the supplied configurations, not restrictions in the
plotting library. If a configuration changes, update `config.py` to match the
result filenames exactly.

## Bundled Python plots

Tutorials 1--5 provide these entry points:

| Script | Output represented |
| --- | --- |
| `plotAvgFailedTask.py` | Overall and location-specific task failure percentages. |
| `plotAvgNetworkDelay.py` | Aggregate and network-segment delays. |
| `plotAvgProcessingTime.py` | Overall, edge, cloud, and where applicable mobile processing time. |
| `plotAvgServiceTime.py` | Overall and execution-location service time. |
| `plotAvgVmUtilization.py` | Mean VM utilization by execution tier. |
| `plotTaskFailureReason.py` | Failure causes, normally normalized by all failed tasks. |
| `plotTimeComplexity.py` | Wall-clock simulation time and orchestration overhead. |
| `plotDelayReasonAsBar.py` | Stacked processing and network-delay components for a selected scenario. |
| `plotLocation.py` | Heat map of the mean number of clients per place. |
| `plotApDelay.py` | Tutorial 4 only: access-point upload and download delay. |

The small entry-point files intentionally contain explicit calls such as:

```python
plot_generic_line(1, 6, 'Processing Time (sec)', 'ALL_APPS', '', 'lower right')
```

This format makes a presentation or paper's figure definition immediately
visible. Keep these calls explicit; reusable parsing, aggregation, confidence
interval, and rendering behavior belongs in `plotGenericLine.py`.

## `plot_generic_line` reference

The helper signature is:

```python
plot_generic_line(
    row_offset,
    column_offset,
    y_label,
    app_type='ALL_APPS',
    calculate_percentage=None,
    legend_pos='best',
    divisor=1,
    ignore_zero_values=False,
    hide_lower_values=None,
    hide_x_axis=None,
)
```

### Required arguments

- `row_offset` is the one-based data-row number after the generated comment
  line. It is **not** a zero-based pandas index. Rows 1--6 are defined below.
- `column_offset` is the one-based column within that row. The helper converts
  it to a zero-based pandas column internally.
- `y_label` is displayed verbatim on the vertical axis.

### Optional arguments

- `app_type` selects the filename token. Use `ALL_APPS` for aggregate data or
  an application token generated by the simulation.
- `calculate_percentage` controls normalization:
  - `None` or `''`: retain the raw value;
  - `'percentage_of_all'`: divide by row 1 column 1 plus row 1 column 2;
  - `'percentage_of_completed'`: divide by row 1 column 1;
  - `'percentage_of_failed'`: divide by row 1 column 2.
  A zero denominator produces `0.0` instead of an infinite value.
- `legend_pos` accepts Matplotlib legend locations: `best`, `upper right`,
  `upper left`, `lower left`, `lower right`, `right`, `center left`,
  `center right`, `lower center`, `upper center`, or `center`.
- `divisor` scales raw values before plotting and must be non-zero. For example,
  the supplied time-complexity plots use `60` for elapsed time and `1000` for
  orchestration overhead.
- `ignore_zero_values=True` treats zero observations as missing during repeated-
  run aggregation. Use it only when zero means "not observed," not when zero is
  a legitimate scientific result.
- `hide_lower_values=<threshold>` replaces aggregated values below the threshold
  with missing values so those points are not drawn.
- `hide_x_axis=(scenario_index, device_threshold)` hides points above a device
  threshold for one scenario. `scenario_index` is one-based and follows the
  order in `scenario_types`. For example, `(2, 1200)` hides the second scenario
  after 1,200 devices.

For every iteration, scenario, and device count, the helper reads exactly one
value. Missing or malformed files are reported and excluded with `NaN` rather
than silently converted to zero. It then computes a `NaN`-aware mean. When
enabled, a two-sided 95% Student's *t* confidence interval is computed from the
valid repetitions at each point; at least two valid observations are required.

## Generic result-file schema

Generic logs are semicolon-separated text files whose first line is the comment
`#auto generated file!`. The following data rows have different meanings and
column counts. Choose only a row/column combination defined in these tables.

### Row 1: overall results

| Column | Metric |
| ---: | --- |
| 1 | Completed tasks |
| 2 | Failed tasks |
| 3 | Uncompleted tasks when the simulation stopped |
| 4 | Failures due to aggregate network bandwidth |
| 5 | Mean service time |
| 6 | Mean processing time |
| 7 | Mean network delay for completed non-mobile tasks |
| 8 | Reserved; currently zero |
| 9 | Mean task cost |
| 10 | Failures due to VM capacity |
| 11 | Failures due to mobility |
| 12 | Mean QoE over completed tasks |
| 13 | Mean QoE over completed plus failed tasks |
| 14 | Tasks rejected because of WLAN coverage limits |

### Rows 2--4: execution tiers

Row 2 represents edge execution, row 3 cloud execution, and row 4 mobile
execution. All three use the same columns:

| Column | Metric |
| ---: | --- |
| 1 | Completed tasks on the tier |
| 2 | Failed tasks on the tier |
| 3 | Uncompleted tasks on the tier |
| 4 | Reserved; currently zero |
| 5 | Mean service time on the tier |
| 6 | Mean processing time on the tier |
| 7 | Reserved; currently zero |
| 8 | Mean VM utilization on the tier |
| 9 | Failures due to VM capacity on the tier |

### Row 5: network results

| Column | Metric |
| ---: | --- |
| 1 | Mean LAN/WLAN delay |
| 2 | Mean MAN delay |
| 3 | Mean WAN delay |
| 4 | Mean GSM/cellular delay |
| 5 | Failures due to LAN/WLAN bandwidth |
| 6 | Failures due to MAN bandwidth |
| 7 | Failures due to WAN bandwidth |
| 8 | Failures due to GSM/cellular bandwidth |

### Row 6: simulator performance

| Column | Metric |
| ---: | --- |
| 1 | Elapsed simulation execution-time value |
| 2 | Mean orchestration overhead |

Row 6 is written only to `ALL_APPS_GENERIC.log`. Consequently, time-complexity
plots must use `app_type='ALL_APPS'`.

## Common plotting combinations

The following combinations cover the metrics most often used in experiments.
The percentage mode shown is a recommendation for that interpretation; raw
counts can be plotted by passing `None` or `''` instead.

| Figure | Row | Column | Recommended percentage mode |
| --- | ---: | ---: | --- |
| Overall failed tasks | 1 | 2 | `percentage_of_all` |
| Failed edge tasks | 2 | 2 | `percentage_of_all` |
| Failed cloud tasks | 3 | 2 | `percentage_of_all` |
| Failed mobile tasks | 4 | 2 | `percentage_of_all` |
| Overall / edge / cloud / mobile service time | 1 / 2 / 3 / 4 | 5 | none |
| Overall / edge / cloud / mobile processing time | 1 / 2 / 3 / 4 | 6 | none |
| Aggregate network delay | 1 | 7 | none |
| LAN/WLAN / MAN / WAN / GSM delay | 5 | 1 / 2 / 3 / 4 | none |
| Edge / cloud / mobile VM utilization | 2 / 3 / 4 | 8 | none |
| Mean cost | 1 | 9 | none |
| VM-capacity failures | 1 | 10 | `percentage_of_failed` |
| Mobility failures | 1 | 11 | `percentage_of_failed` |
| QoE over completed tasks | 1 | 12 | none |
| QoE over completed and failed tasks | 1 | 13 | none |
| WLAN-coverage rejections | 1 | 14 | raw or `percentage_of_failed`, according to the research question |
| LAN/WLAN / MAN / WAN / GSM bandwidth failures | 5 | 5 / 6 / 7 / 8 | `percentage_of_failed` |
| Simulation execution time | 6 | 1 | none; commonly `divisor=60` |
| Orchestration overhead | 6 | 2 | none; commonly `divisor=1000` |

Examples:

```python
from plotGenericLine import plot_generic_line

# Mean processing time for all application types.
plot_generic_line(1, 6, 'Processing Time (sec)', 'ALL_APPS',
                  None, 'lower right')

# Failed-task percentage among completed plus failed tasks.
plot_generic_line(1, 2, 'Failed Tasks (%)', 'ALL_APPS',
                  'percentage_of_all', 'upper left')

# Share of all failed tasks caused by WAN bandwidth.
plot_generic_line(5, 7, 'WAN Failure Share (%)', 'ALL_APPS',
                  'percentage_of_failed', 'upper left')

# Edge VM utilization, omitting zero observations from aggregation.
plot_generic_line(2, 8, 'Edge VM Utilization (%)', 'ALL_APPS',
                  None, 'best', ignore_zero_values=True)

# Execution time converted with the established figure convention.
plot_generic_line(6, 1, 'Simulation Time (min)', 'ALL_APPS',
                  None, 'upper left', divisor=60)

# Hide the third scenario after 1,600 devices.
plot_generic_line(1, 5, 'Service Time (sec)', 'ALL_APPS',
                  None, 'best', hide_x_axis=(3, 1600))
```

## Specialized plots

Some outputs are matrices or stacked components rather than one scalar per
generic log, so they do not call `plot_generic_line`:

- `plotDelayReasonAsBar.py` combines tier processing time with the corresponding
  WLAN or WAN delay. Its selected scenario is intentionally fixed in the script
  to reproduce a particular comparison.
- `plotLocation.py` reads `*_LOCATION.log` and creates a heat map. Place counts
  differ by tutorial, so retain the tutorial-specific implementation.
- `plotApDelay.py` reads `*_AP_UPLOAD_DELAY.log` and
  `*_AP_DOWNLOAD_DELAY.log`; it is supplied only for tutorial 4, where that
  analysis is part of the experiment.

The fixed scenarios used by the current specialized scripts are:

| Tutorial | Delay-reason bars | Location heat map | AP delay |
| --- | --- | --- | --- |
| 1 | `WORST_FIT` | `WORST_FIT` | not supplied |
| 2 | `EDGE_UTIL_HEURISTIC` | `EDGE_UTIL_HEURISTIC` | not supplied |
| 3 | `RANDOM` | `UTILIZATION_BASED` | not supplied |
| 4 | `TRAFFIC_HEURISTIC` | `TRAFFIC_HEURISTIC` | `TRAFFIC_HEURISTIC` |
| 5 | `SCENARIO1` | `SCENARIO3` | not supplied |

Change a fixed scenario only when the corresponding result files exist and the
new comparison is scientifically intended.

## Adapt the plotter for a new experiment

To define a new scalar figure without changing the helper, add a small driver
beside `plotGenericLine.py`:

```python
from plotGenericLine import plot_generic_line


if __name__ == '__main__':
    plot_generic_line(
        1,
        5,
        'Service Time (sec)',
        'ALL_APPS',
        None,
        'upper left',
    )
```

Then verify all of the following:

1. `config.py` describes the scenarios and device counts in the generated
   filenames.
2. `folder_path` contains all expected extracted `iteN/` directories.
3. The selected application token exists in every required iteration.
4. The row and column represent the intended scientific metric.
5. Percentage normalization uses a denominator appropriate to the claim made
   in the axis label or paper.
6. Missing-file warnings are resolved before a figure is published.

For a sample application or a newly added application, inspect its generated
filename prefix and adjust the `file_name` expression in a local copy of the
helper. Do not rename raw result files: preserving them supports provenance and
reproducibility. The row/column schema above remains applicable to generic logs.

## Reproducible research checklist

Before using results in a paper, release, or benchmark report:

- record the Git revision and EdgeCloudSim version;
- record the operating system and Java and Python versions;
- archive the exact `.properties`, edge-device XML, and application XML files;
- record the seed or seed sequence and explain how repetitions are treated;
- record the number of iterations and maximum parallel workers;
- retain the original `iteN.tar.gz` archives and console logs;
- document any changes to `config.py`, fixed scenarios, filters, divisors, or
  percentage denominators;
- investigate every missing-file or malformed-file warning;
- include confidence intervals or justify why they are not appropriate;
- verify that axis labels, units, legends, and application populations match
  the underlying metric;
- run the repository quality gate from the repository root:

```bash
./scripts/quality_check.sh
```

## Troubleshooting

### `python: can't open file`

Run the command from the tutorial's `python/` directory and confirm the exact
case-sensitive filename, for example `plotAvgFailedTask.py`.

### Many `File not found` warnings

Check, in order:

1. the archives were extracted into `ite1/`, `ite2/`, ...;
2. `folder_path` points to the configuration directory, not the simulation-ID
   directory above it;
3. `num_iterations` does not exceed the number of extracted iterations;
4. scenario tokens and device bounds match the generated filenames;
5. `app_type` exists in the application XML and was active in the experiment.

### An empty plot or missing curve

Review missing-file warnings first. Then check whether `ignore_zero_values`,
`hide_lower_values`, or `hide_x_axis` filtered the data. A legitimate value of
zero should not be ignored merely to make a curve look smoother.

### No PDF is created

Set `save_figure_as_pdf` to `True` and ensure `folder_path` exists and is
writable. The PDF is saved in the result directory, not in the Python source
directory.

### Confidence intervals are absent

Set `plot_confidence_interval` to `True` and provide at least two valid values
for each plotted point. A point with fewer than two valid repetitions cannot
have a sample-based Student's *t* interval.

### The simulation starts but produces no plot-ready directory

Wait until the iteration finishes and creates `iteN.tar.gz`, then extract the
archive. While an iteration is running, its temporary directory may be in use
and should not be treated as a completed research artifact.
