# Tutorial 3: Workload Orchestration and Server Selection

## Overview

This tutorial demonstrates the **performance evaluation of different workload orchestration policies** in multi-tier edge-cloud computing environments. The focus is on comparing various algorithms that determine **where to offload** computational tasks - whether to edge servers or cloud servers based on system conditions and resource availability.

## Scenario Description

### Simulation Scenario

<p align="center">
  <img src="/doc/images/tutorials/tutorial3-scenario.png" width="100%">
  <p align="center">
    Figure 1: Tutorial 3 multi-tier workload orchestration scenario.
  </p>
</p>

The simulation environment is designed with the following characteristics:

- **Multi-Tier Architecture**: Mobile devices can offload tasks to either edge servers or cloud servers
- **VM Provisioning Strategy**: Worst-fit VM provisioning algorithm (least loaded first) is used across all server tiers
- **Inter-Edge Communication**: Tasks can be transmitted to remote edge servers via Metropolitan Area Network (MAN)
- **Independent Network Modeling**: WLAN and WAN delays are modeled independently, ensuring WLAN performance is not affected when tasks are sent to remote servers

### Key Features

- **Flexible Offloading Options**: Tasks can be directed to local edge servers, remote edge servers, or cloud servers
- **Network-Aware Routing**: MAN connectivity enables communication between geographically distributed edge servers
- **Isolated Network Performance**: WLAN operations remain unaffected by WAN traffic to cloud servers
- **Dynamic Resource Allocation**: Real-time monitoring of edge server utilization and network bandwidth

## Network Infrastructure

### WLAN (Wireless Local Area Network)
- **Purpose**: Connects mobile devices to local edge servers
- **Performance**: Independent of WAN operations
- **Characteristics**: Low latency, limited coverage area
- **Usage**: Primary interface for edge server access

### MAN (Metropolitan Area Network)
- **Purpose**: Interconnects distributed edge servers within metropolitan area
- **Performance**: Medium latency, high bandwidth
- **Characteristics**: Enables load balancing across edge infrastructure
- **Usage**: Inter-edge server communication and task forwarding

### WAN (Wide Area Network)
- **Purpose**: Connects edge infrastructure to cloud servers
- **Performance**: Higher latency, variable bandwidth
- **Characteristics**: Internet-based connectivity with bandwidth fluctuations
- **Usage**: Cloud server access for resource-intensive tasks

## Workload Orchestration Algorithms

This tutorial implements and compares three distinct approaches for server selection and workload orchestration:

### 1. Random Server Selection (RND)
- **Strategy**: Randomly selects a server (edge or cloud) to offload tasks
- **Characteristics**: 
  - No optimization criteria or intelligence
  - Provides baseline performance metrics for comparison
  - Equal probability for edge and cloud server selection
  - Useful for evaluating the impact of intelligent orchestration policies

### 2. Edge Server Utilization Heuristic (ESU)
- **Strategy**: Makes server selection decisions based on edge server CPU utilization
- **Decision Logic**:
  - **IF** average edge servers CPU utilization > 75%
  - **THEN** offload task to cloud server
  - **ELSE** offload task to edge servers
- **Characteristics**:
  - Edge-centric approach prioritizing local resource availability
  - Prevents edge server overload by redirecting to cloud when necessary
  - Balances edge processing capabilities with cloud scalability

### 3. Network Utilization Heuristic (NWU)
- **Strategy**: Makes server selection decisions based on WAN bandwidth availability
- **Decision Logic**:
  - **IF** WAN bandwidth > 5 Mbps
  - **THEN** offload task to cloud server
  - **ELSE** offload task to edge servers
- **Characteristics**:
  - Network-centric approach considering connectivity quality
  - Leverages high-bandwidth periods for cloud processing
  - Ensures edge processing during network congestion

---

**Note**: This tutorial provides fundamental insights into workload orchestration and server selection strategies. Real-world deployments should consider additional factors such as data locality, security policies, service level agreements, and cost optimization.
