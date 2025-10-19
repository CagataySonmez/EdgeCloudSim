# Tutorial 2: Task Offloading Granularity and Decision Making

## Overview

This tutorial demonstrates the **performance evaluation of different approaches that decide the granularity of task offloading** in edge computing environments. The focus is on comparing various decision-making algorithms that determine **what to offload** - whether computational tasks should be executed locally on mobile devices or offloaded to edge servers.

## Scenario Description

### Simulation Scenario

<p align="center">
  <img src="/doc/images/tutorials/tutorial2-scenario.png" width="100%">
  <p align="center">
    Figure 1: Tutorial 2 task offloading decision scenario.
  </p>
</p>

The simulation environment is designed with the following characteristics:

- **Hybrid Execution Model**: Mobile devices can operate tasks locally or offload them to edge servers connected to the serving access point (AP)
- **Edge Infrastructure**: Edge servers operate a variable number of Virtual Machines (VMs) to handle offloaded computational workloads
- **VM Provisioning Strategy**: Worst-fit VM provisioning algorithm (least loaded first) is used for resource allocation
- **Decision Granularity**: System evaluates task-by-task decisions for optimal execution placement

### Key Features

- **Dual Execution Options**: Each task can be processed either locally on the mobile device or remotely on edge servers
- **Dynamic Load Balancing**: Real-time monitoring of both mobile device and edge server utilization
- **Intelligent Decision Making**: Utilization-based heuristics for optimal task placement
- **Performance Trade-offs**: Evaluation of latency, energy consumption, and resource utilization

## Task Offloading Decision Algorithms

This tutorial implements and compares three distinct approaches for task offloading decisions:

### 1. Random Decision
- **Strategy**: Makes random decisions for task placement
- **Characteristics**: 
  - No optimization criteria or intelligence
  - Provides baseline performance metrics for comparison
  - Equal probability for local execution and edge offloading
  - Useful for evaluating the impact of intelligent decision-making

### 2. Mobile Device Utilization Heuristic
- **Strategy**: Makes offloading decisions based on mobile device CPU utilization
- **Decision Logic**:
  - **IF** average mobile device CPU utilization < 75%
  - **THEN** execute task locally on mobile device
  - **ELSE** offload task to edge server
- **Characteristics**:
  - Mobile-centric approach prioritizing local resource utilization
  - Conserves mobile device resources when heavily loaded
  - May lead to edge server underutilization in some scenarios

### 3. Edge Utilization Heuristic
- **Strategy**: Makes offloading decisions based on edge server CPU utilization
- **Decision Logic**:
  - **IF** average edge server CPU utilization < 90%
  - **THEN** offload task to edge server
  - **ELSE** execute task locally on mobile device
- **Characteristics**:
  - Edge-centric approach maximizing edge resource utilization
  - Leverages superior computational capabilities of edge servers
  - Provides fallback to local execution when edge resources are saturated

---

**Note**: This tutorial provides fundamental insights into task offloading decision making. Real-world deployments should consider additional factors such as network conditions, task dependencies, security requirements, and user mobility patterns.
