# Tutorial 5: Network & Server Capacity Planning with Queuing Models

## Overview

This tutorial demonstrates the **performance evaluation of different capacity planning approaches** using various queuing theory models in edge computing environments. The focus is on comparing different queuing models at the network layer and analyzing their impact on system performance through mathematical modeling and simulation.

## Scenario Description

### Queuing Models Architecture

<p align="center">
  <img src="/doc/images/tutorials/tutorial5-scenario.png" width="95%">
  <p align="center">
    Figure 1: Tutorial 5 queuing models comparison scenario.
  </p>
</p>

The simulation environment is designed to evaluate three fundamental queuing models:

- **Mathematical Foundation**: Implementation of classical queuing theory models in EdgeCloudSim
- **Network Layer Focus**: Queuing models applied at the network communication layer
- **Performance Comparison**: Systematic evaluation of different queuing approaches
- **Capacity Planning**: Analysis of how different models affect resource allocation and system efficiency

## Queuing Models Implementation

This tutorial implements and compares three distinct queuing models, each representing different approaches to network and server capacity planning:

### 1. M/M/1 Queue Model

<p align="center">
  <img src="/doc/images/tutorials/tutorial5-case1.png" width="100%">
  <p align="center">
    Figure 2: M/M/1 Queue implementation in EdgeCloudSim.
  </p>
</p>

**Mathematical Characteristics**:
- **Arrival Process**: Markovian (Poisson) arrivals with rate λ
- **Service Process**: Markovian (Exponential) service times with rate μ
- **Servers**: Single server system
- **Queue Discipline**: First-Come-First-Served (FCFS)

**Implementation Features**:
- Single processing unit handling all incoming requests
- Exponential inter-arrival and service time distributions
- Unlimited queue capacity for waiting requests
- Simple but foundational queuing model

**Performance Metrics**:
- **Utilization**: ρ = λ/μ (system must have ρ < 1 for stability)
- **Average Response Time**: E[T] = 1/(μ - λ)
- **Average Queue Length**: E[N] = ρ/(1 - ρ)
- **Waiting Time**: E[W] = ρ/(μ - λ)

### 2. M/M/2 Queue Model

<p align="center">
  <img src="/doc/images/tutorials/tutorial5-case2.png" width="100%">
  <p align="center">
    Figure 3: M/M/2 Queue implementation in EdgeCloudSim.
  </p>
</p>

**Mathematical Characteristics**:
- **Arrival Process**: Markovian (Poisson) arrivals with rate λ
- **Service Process**: Markovian (Exponential) service times with rate μ per server
- **Servers**: Two parallel servers
- **Queue Discipline**: Shared queue with load balancing

**Implementation Features**:
- Dual processing units sharing the incoming workload
- Load balancing between two identical servers
- Improved throughput compared to single server systems
- Enhanced fault tolerance with redundant processing capacity

**Performance Metrics**:
- **System Utilization**: ρ = λ/(2μ) (stability requires ρ < 1)
- **Average Response Time**: Improved compared to M/M/1 for same arrival rate
- **Queue Length Reduction**: Significant improvement in waiting times
- **Throughput Enhancement**: Better handling of peak loads

### 3. Parallel M/M/1 Queues Model

<p align="center">
  <img src="/doc/images/tutorials/tutorial5-case3.png" width="100%">
  <p align="center">
    Figure 4: Parallel M/M/1 Queues implementation in EdgeCloudSim.
  </p>
</p>

**Mathematical Characteristics**:
- **Arrival Process**: Split Markovian arrivals across multiple queues
- **Service Process**: Independent Exponential service times per queue
- **Servers**: Multiple independent M/M/1 systems
- **Queue Discipline**: Separate queues with independent processing

**Implementation Features**:
- Multiple independent processing channels
- Traffic splitting across parallel queues
- Independent queue management and processing
- Distributed load handling architecture

**Performance Metrics**:
- **Individual Queue Utilization**: ρᵢ = λᵢ/μᵢ for each queue i
- **System-wide Performance**: Aggregate of individual queue performances
- **Load Distribution**: Traffic splitting strategies affect overall efficiency
- **Scalability**: Horizontal scaling through queue replication

---

**Note**: This tutorial provides fundamental insights into queuing theory applications in edge computing. Real-world deployments should consider additional factors such as non-exponential service times, finite queue capacities, priority scheduling, and dynamic load balancing strategies.
