# Tutorial 1: VM Scheduling and Performance Evaluation

## Overview

This tutorial demonstrates the **performance evaluation of different VM allocation policies** in edge computing environments. The focus is on comparing various VM scheduling algorithms and their impact on system performance when mobile devices offload computational tasks to edge servers.

## Scenario Description

### Simulation Scenario

<p align="center">
  <img src="/doc/images/tutorials/tutorial1-scenario.png" width="100%">
  <p align="center">
    Figure 1: Tutorial 1 simulation scenario architecture.
  </p>
</p>

The simulation environment is designed with the following characteristics:

- **Mobile Device Constraints**: Mobile devices can only offload tasks to edge servers connected to their serving access point (AP)
- **Edge Infrastructure**: Edge servers operate a variable number of Virtual Machines (VMs) to handle computational workloads
- **Localized Processing**: Tasks are processed locally at the edge tier, reducing latency compared to cloud-based processing
- **Resource Management**: Different VM provisioning algorithms are evaluated to optimize resource allocation

### Key Features

- **Geographically Distributed Edge Servers**: Multiple edge servers strategically placed across the network
- **Dynamic VM Allocation**: Variable number of VMs per edge server based on demand and allocation policy
- **Access Point Association**: Strict binding between mobile devices and their serving access points
- **Performance Metrics Collection**: Comprehensive evaluation of different scheduling approaches

## VM Scheduling Algorithms

<p align="center">
  <img src="/doc/images/tutorials/tutorial1-algorithms.png" width="100%">
  <p align="center">
    Figure 2: VM scheduling algorithms comparison.
  </p>
</p>

This tutorial implements and compares five distinct VM scheduling algorithms:

### 1. Random (RND)
- **Strategy**: Randomly selects an available VM from the pool
- **Characteristics**: 
  - No optimization criteria
  - Provides baseline performance metrics
  - Useful for comparison against optimized algorithms

### 2. First-Fit (FF)
- **Strategy**: Selects the first available VM that can accommodate the task
- **Characteristics**:
  - Simple and fast allocation
  - Minimizes search overhead
  - May lead to suboptimal resource utilization

### 3. Next-Fit (NF)
- **Strategy**: Visits hosts in sequential order and selects the first suitable VM found
- **Characteristics**:
  - Maintains ordering across allocation decisions
  - Balances simplicity with systematic allocation
  - Provides more predictable allocation patterns than random

### 4. Best-Fit (BF)
- **Strategy**: Selects the VM with the highest current CPU utilization that can still accommodate the task
- **Characteristics**:
  - Maximizes resource consolidation
  - Minimizes resource fragmentation
  - May increase task queuing times on heavily loaded VMs

### 5. Worst-Fit (WF)
- **Strategy**: Selects the VM with the lowest current CPU utilization
- **Characteristics**:
  - Distributes load across available resources
  - Minimizes individual VM overload
  - May lead to resource underutilization

---

**Note**: This tutorial provides fundamental insights into task offloading decision making. Real-world deployments should consider additional factors such as network conditions, task dependencies, security requirements, and user mobility patterns.