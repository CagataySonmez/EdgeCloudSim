# Tutorial 4: Server Capacity Planning for Vehicular Edge Computing

## Overview

This tutorial demonstrates the **performance evaluation of different capacity planning approaches** in vehicular edge computing environments. The focus is on comparing various algorithms that determine optimal computational resource allocation across edge servers to efficiently serve dynamic vehicular workloads on smart highways.

## Scenario Description

### Vehicular Edge Computing Architecture

<p align="center">
  <img src="/doc/images/tutorials/tutorial4-scenario.png" width="100%">
  <p align="center">
    Figure 1: Tutorial 4 vehicular edge computing infrastructure.
  </p>
</p>

The simulation environment is designed with the following characteristics:

- **Vehicular Network Constraints**: Vehicles can only offload computational tasks to edge servers connected to their serving access point
- **Heterogeneous Edge Infrastructure**: Edge servers run host machines with varying computational capacity
- **Dynamic Workload**: Vehicle-generated tasks create time-varying computational demands
- **Capacity Optimization**: Different edge server capacity planning algorithms are evaluated to optimize resource allocation

### Smart Highway Environment

<p align="center">
  <img src="/doc/images/tutorials/tutorial4-road.png" width="90%">
  <p align="center">
    Figure 2: Smart highway simulation environment with circular road topology.
  </p>
</p>

The smart highway simulation includes:

- **Circular Road Topology**: Continuous traffic flow on a closed-loop highway system
- **Variable Vehicle Population**: 1000 to 2000 vehicles traveling simultaneously
- **Dynamic Velocity Modeling**: Realistic speed variations based on vehicle position and traffic conditions
- **Geographical Distribution**: Non-uniform vehicle density creating varying computational demands


## Capacity Planning Algorithms

This tutorial implements and compares three distinct approaches for edge server capacity planning:

### 1. Random Capacity Allocation
- **Strategy**: Randomly distributes total computational capacity across edge servers
- **Total System Capacity**: 220 GIPS (Giga Instructions Per Second)
- **Characteristics**: 
  - No optimization criteria or traffic awareness
  - Provides baseline performance metrics for comparison
  - Random distribution may lead to resource imbalances
  - Useful for evaluating the impact of intelligent capacity planning

### 2. Equal Capacity Distribution
- **Strategy**: Uniformly distributes computational capacity across all edge servers
- **Individual Server Capacity**: 20 GIPS per host machine
- **Total System Capacity**: 220 GIPS (11 hosts × 20 GIPS)
- **Characteristics**:
  - Simple and fair resource allocation approach
  - Ensures consistent computational capability at each location
  - May not adapt to traffic density variations
  - Provides stable performance baseline

### 3. Traffic Density Heuristic
- **Strategy**: Distributes capacity proportionally based on traffic intensity patterns
- **Adaptive Allocation**:
  - **High Density Areas**: 44 GIPS computing capacity per host
  - **Medium Density Areas**: 20 GIPS computing capacity per host
  - **Low Density Areas**: 14 GIPS computing capacity per host
- **Total System Capacity**: 220 GIPS (dynamically allocated)
- **Characteristics**:
  - Traffic-aware resource allocation strategy
  - Maximizes computational resources where demand is highest
  - Adapts to real-time vehicular density patterns
  - Optimizes overall system efficiency

---

**Note**: This tutorial provides fundamental insights into capacity planning for vehicular edge computing. Real-world deployments should consider additional factors such as vehicle mobility prediction, emergency service prioritization, network handover optimization, and energy efficiency constraints.
