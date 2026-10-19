# EdgeCloudSim

[![GitHub stars](https://img.shields.io/github/stars/CagataySonmez/EdgeCloudSim?style=for-the-badge)](https://github.com/CagataySonmez/EdgeCloudSim/stargazers)
[![GitHub forks](https://img.shields.io/github/forks/CagataySonmez/EdgeCloudSim?style=for-the-badge)](https://github.com/CagataySonmez/EdgeCloudSim/network)
[![License](https://img.shields.io/github/license/CagataySonmez/EdgeCloudSim?style=for-the-badge)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21+-orange?style=for-the-badge&logo=java)](https://openjdk.org/)
[![CloudSim](https://img.shields.io/badge/CloudSim-7.0.0--alpha-blue?style=for-the-badge)](https://cloudsim.org/)

EdgeCloudSim is an open-source simulation environment for edge-computing scenarios that models both computational and networking resources. It builds on CloudSim and adds the components required to evaluate edge architectures, mobility, networking, workload generation, and orchestration policies. Contributions are welcome; see the [contributing guidelines](/CONTRIBUTING.md). If you use EdgeCloudSim in research, please cite our paper [[3]](https://onlinelibrary.wiley.com/doi/abs/10.1002/ett.3493).

## Table of Contents
- [🚀 What's New](#-whats-new-october-2025)
- [Sample Applications and Tutorials](#sample-applications-and-tutorials)
- [Quick Start Guide](#quick-start-guide)
- [Architecture Overview](#edgecloudsim-an-environment-for-performance-evaluation-of-edge-computing-systems)
- [Compilation and Running](#compilation-and-running)
- [Analyzing the Results](#analyzing-the-results)
- [Example Output](#example-output-of-edgecloudsim)
- [Publications](#publications)

## 🚀 What's New (October 2025)

### Major Updates
- **📚 CloudSim Library Upgrade**: Updated to CloudSim 7.0.0-alpha for improved performance and stability
  - **Note**: We specifically use 7.0.0-alpha as newer CloudSim versions have breaking API changes and class modifications
  - Maintains compatibility while providing enhanced simulation capabilities

- **🎓 New Tutorial Series**: Added comprehensive tutorial applications (`tutorial1-5`) covering **5 different engineering problems**:
  - **📋 Complete Guide**: See detailed [EdgeCloudSim_ModellingGuide.pdf](/doc/EdgeCloudSim_ModellingGuide.pdf) for comprehensive explanation of edge computing simulation and all 5 tutorials
  - **Important**: Each tutorial includes both MATLAB and **Python analysis scripts** for result visualization

- **🤖 AI-Enhanced Code Quality**:
  - Source code restructured and optimized with AI assistance
  - Enhanced code comments and documentation for better readability

### Benefits for Researchers
- **Easier Learning Curve**: New tutorials provide step-by-step guidance
- **Better Code Understanding**: AI-enhanced comments explain complex algorithms
- **Improved Stability**: CloudSim 7.0.0-alpha provides more reliable simulations

## Quick Start Guide

### Prerequisites
- **Java 21+** (OpenJDK or Oracle JDK - recommended for optimal performance)
- **Eclipse IDE** (recommended) or any Java IDE
- **Git** for cloning the repository
- **MATLAB** or **Python** for result analysis

### 🚀 Get Started in 5 Minutes
1. **Clone the repository**:
   ```bash
   git clone https://github.com/CagataySonmez/EdgeCloudSim.git
   cd EdgeCloudSim
   ```

2. **Import to IDE**:
   - Open Eclipse → File → Import → Existing Projects into Workspace
   - Select EdgeCloudSim folder → Import

3. **Run your first simulation**:
   - Navigate to `src/edu/boun/edgecloudsim/applications/tutorial1`
   - Run `MainApp.java` to see EdgeCloudSim in action!

4. **Explore examples**:
   - Check [Sample Applications](#sample-applications-sample_app1---sample_app5) for research scenarios
   - Follow [Tutorials](#tutorials-tutorial1---tutorial5) for step-by-step learning

### 📚 Learning Path
1. **Start with Tutorial 1** - Basic simulation setup
2. **Read the [EdgeCloudSim_ModellingGuide.pdf](/doc/EdgeCloudSim_ModellingGuide.pdf)** - Comprehensive guide
3. **Explore Sample Applications** - Real research scenarios
4. **Customize your own scenario** - Extend for your research

## Sample Applications and Tutorials

EdgeCloudSim comes with comprehensive examples to help you get started and understand different edge computing scenarios:

### Sample Applications (`sample_app1` - `sample_app5`)
These applications represent **real-world scenarios published in our research papers**:
- **[sample_app1](/src/edu/boun/edgecloudsim/applications/sample_app1)**: Basic edge computing environment evaluation (conference version) [[1]](http://ieeexplore.ieee.org/document/7946405/)
- **[sample_app2](/src/edu/boun/edgecloudsim/applications/sample_app2)**: Single-tier and two-tier cloudlet assisted applications [[2]](http://ieeexplore.ieee.org/document/7962674)
- **[sample_app3](/src/edu/boun/edgecloudsim/applications/sample_app3)**: Basic edge computing environment evaluation (journal version) [[3]](https://onlinelibrary.wiley.com/doi/abs/10.1002/ett.3493)
- **[sample_app4](/src/edu/boun/edgecloudsim/applications/sample_app4)**: Fuzzy logic-based workload orchestration for edge computing [[4]](https://ieeexplore.ieee.org/abstract/document/8651335/)
- **[sample_app5](/src/edu/boun/edgecloudsim/applications/sample_app5)**: Machine learning-based workload orchestrator for vehicular edge computing [[5]](https://ieeexplore.ieee.org/abstract/document/9208723/)

Each sample application includes complete configuration files, simulation scripts, and MATLAB analysis tools for result visualization.

### Tutorials (`tutorial1` - `tutorial5`)
These tutorials demonstrate **5 different engineering problems** commonly encountered in edge computing:
- **[Tutorial 1](/src/edu/boun/edgecloudsim/applications/tutorial1)**: VM Scheduling: Performance Evaluation of Different VM Allocation Policies
- **[Tutorial 2](/src/edu/boun/edgecloudsim/applications/tutorial2)**: What to Offload: Performance Evaluation of Different Approaches that Decide Granularity of Task Offloading
- **[Tutorial 3](/src/edu/boun/edgecloudsim/applications/tutorial3)**: Where to Offload: Performance Evaluation of Different Workload Orchestration Policies
- **[Tutorial 4](/src/edu/boun/edgecloudsim/applications/tutorial4)**: Server Capacity Planning: Performance Evaluation of Different Capacity Planning Approaches
- **[Tutorial 5](/src/edu/boun/edgecloudsim/applications/tutorial5)**: Network & Server Capacity Planning: Performance Evaluation of Different Capacity Planning Approaches

📋 **For detailed explanations**: Check out our comprehensive [EdgeCloudSim_ModellingGuide.pdf](/doc/EdgeCloudSim_ModellingGuide.pdf) that covers edge computing simulation fundamentals, EdgeCloudSim architecture, and step-by-step tutorial guides.

The tutorials are designed as step-by-step guides for learning EdgeCloudSim's core functionalities and extending them for your specific research needs.

## Discussion Forum

The discussion forum for EdgeCloudSim can be found [here](https://groups.google.com/forum/#!forum/edgecloudsim).
We hope to meet with all interested parties in this forum.
Please feel free to join and let us discuss issues, share ideas related to EdgeCloudSim all together.

## YouTube Channel

The YouTube channel of EdgeCloudSim can be found [here](https://www.youtube.com/channel/UC2gnXTWHHN6h4bk1D5gpcIA).
You can find some videos presenting our works and tutorials on this channel.
Click [here](https://youtu.be/SmQgRANWUts) to watch the video with brief information about EdgeCloudSim.

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
EdgeCloudSim uses the factory pattern to simplify the integration of new models. As shown in Figure 2, a scenario factory provides concrete implementations of the mobility, load-generation, networking, and orchestration abstractions. Custom scenarios can supply their own factory without modifying the simulation core.

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

### Using IDE (Recommended)
1. **Import project** into Eclipse or IntelliJ IDEA
2. **Run directly** from your IDE:
   - For tutorials: Navigate to `src/edu/boun/edgecloudsim/applications/tutorial1-5/MainApp.java`
   - For sample apps: Navigate to `src/edu/boun/edgecloudsim/applications/sample_app1-5/MainApp.java`
3. **No compilation needed** - IDE handles everything!

### Using Command Line
For each sample application or tutorial, use the corresponding script:
- **Sample App 1**: Use `scripts/sample_app1/compile.sh` and `scripts/sample_app1/run_scenarios.sh`
- **Sample App 2**: Use `scripts/sample_app2/compile.sh` and `scripts/sample_app2/run_scenarios.sh`
- And so on...

### Running Parallel Simulations
To run multiple scenarios in parallel:
```bash
cd scripts/sample_app1
./run_scenarios.sh 8 10  # 8 parallel processes, 10 iterations each
```

Monitor progress:
```bash
tail -f output/date/scenario_name/progress.log
```

**Note**: Scripts are compatible with Linux, macOS, and Windows (with WSL).

### Reproducible Experiments

EdgeCloudSim uses a shared deterministic random stream for task generation,
mobility, and the built-in stochastic models. Set `edgecloudsim.seed` to repeat
an experiment with the same pseudo-random sequence:

```bash
export JAVA_TOOL_OPTIONS="-Dedgecloudsim.seed=20261018"
cd scripts/sample_app1
./run_scenarios.sh 1 1
```

The selected seed is written to the simulation log. If the property is omitted,
EdgeCloudSim retains the historical behavior and creates a seed from the system
clock. Publications should report the seed or seed set, configuration files,
EdgeCloudSim revision, Java version, and number of independent repetitions.

### Quality Checks

Run the repository-wide verification before publishing results or submitting a
pull request:

```bash
./scripts/quality_check.sh
```

The command compiles every source file with all compiler warnings treated as
errors, runs deterministic and mathematical regression tests, validates every
bundled configuration and WEKA model set, and checks Javadoc structure.


## Analyzing the Results
At the end of each iteration, simulation results will be compressed in the *output/date/ite_n.tgz* files. When you extract these tgz files, you will see lots of log file in csv format. You can find matlab files which can plot graphics by using these files under *scripts/sample_application/matlab* folder. You can also write other scripts (e.g. python scripts) with the same manner of matlab plotter files.

## Example Output of EdgeCloudSim
You can plot lots of graphics by using the result of EdgeCloudSim. Some examples are given below:

![Alt text](/doc/images/result1.png?raw=true) ![Alt text](/doc/images/result2.png?raw=true)

![Alt text](/doc/images/result4.png?raw=true) ![Alt text](/doc/images/result5.png?raw=true)

![Alt text](/doc/images/result6.png?raw=true) ![Alt text](/doc/images/result3.png?raw=true)

![Alt text](/doc/images/result7.png?raw=true) ![Alt text](/doc/images/result8.png?raw=true)

## Publications
**[1]** C. Sonmez, A. Ozgovde and C. Ersoy, "[EdgeCloudSim: An environment for performance evaluation of Edge Computing systems](http://ieeexplore.ieee.org/document/7946405/)," *2017 Second International Conference on Fog and Mobile Edge Computing (FMEC)*, Valencia, 2017, pp. 39-44.

**[2]** C. Sonmez, A. Ozgovde and C. Ersoy, "[Performance evaluation of single-tier and two-tier cloudlet assisted applications](http://ieeexplore.ieee.org/document/7962674/)," *2017 IEEE International Conference on Communications Workshops (ICC Workshops)*, Paris, 2017, pp. 302-307.

**[3]** Sonmez C, Ozgovde A, Ersoy C. "[EdgeCloudSim: An environment for performance evaluation of Edge Computing systems](https://onlinelibrary.wiley.com/doi/abs/10.1002/ett.3493)," *Transactions on Emerging Telecommunications Technologies*, 2018;e3493.

**[4]** C. Sonmez, A. Ozgovde and C. Ersoy, "[Fuzzy Workload Orchestration for Edge Computing](https://ieeexplore.ieee.org/abstract/document/8651335/)," in *IEEE Transactions on Network and Service Management*, vol. 16, no. 2, pp. 769-782, June 2019.

**[5]** C. Sonmez, A. Ozgovde and C. Ersoy, "[Machine Learning-Based Workload Orchestrator for Vehicular Edge Computing](https://ieeexplore.ieee.org/abstract/document/9208723/)," in *IEEE Transactions on Intelligent Transportation Systems*, doi: 10.1109/TITS.2020.3024233.
