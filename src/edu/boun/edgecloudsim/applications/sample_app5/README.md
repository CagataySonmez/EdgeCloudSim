# Sample Application 5

This application includes the source code which is used in our paper submitted to IEEE Transactions on Intelligent Transportation Systems [[1]](https://ieeexplore.ieee.org/abstract/document/9208723/).

You can find the presentation of this work on our YouTube channel. Click [here](https://youtu.be/mlcLDpDcdw8) to watch!

## Vehicular Edge Computing

The concept of Internet of Vehicle (IoV), its pioneering applications, and the services for the future smart highways can benefit from the computation offloading concept over a multi-tier architecture consisting of the connected vehicles, road side units (RSUs), and cloud computing elements as shown in Figure 6.1. The vehicles are located in the first tier, which can be considered as the data generation layer. They also have computational resources which are provided by their on board units (OBUs). If required, some of the operations can be executed locally by the OBUs at this tier. The second tier consists of the RSUs that can provide fast access to limited resources. The edge servers are located in this tier. Finally, the third tier includes traditional cloud computing elements.

<p align="center">
  <img src="/doc/images/sample_app5/vec_architecture.png" width="75%">
  <p align="center">
    Figure 1: Multi-tier VEC architecture for vehicular networks.
  </p>
</p>


## Machine Learning-Based Workload Orchestrator

In this application we introduce a machine learning (ML) based workload orchestrator. the ML-based orchestrator performs a two-stage process as shown in Figure 2. In the first stage, a classifier model predicts whether the results of the offloading options are successful or not for each target device. In the second stage, a regression model estimates the service time of the related options. Finally, the target device which promises the lowest service time is selected.

<p align="center">
  <img src="/doc/images/sample_app5/ml_stages.png" width="100%">
  <p align="center">
    Figure 2: Two stage ML-based vehicular edge orchestrator.
  </p>
</p>

We experimented with multiple classifiers, including naive Bayes (NB), support vector machine (SVM) and multi layer perceptron (MLP) models. The MLP was chosen as the best classifier based on the performance comparison of these models. A demonstrative example of a two-stage ML-based vehicular edge orchestrator is illustrated in Fig. 4. As shown in Figure 3, offloading to the edge and cloud via cellular network (CN) options are predicted to be successful in the first stage; hence they are selected as the initial candidates. The cloud via CN option is determined as the best candidate in the second stage since it promises better service time value than the edge option.

<p align="center">
  <img src="/doc/images/sample_app5/ml_details.png" width="60%">
  <p align="center">
    Figure 3: Demonstrative example of the ML-based orchestrator stages.
  </p>
</p>


## Why Machine Learning?

Orchestrating the dynamic and heterogeneous resources in the VEC systems is a challenging task. The vehicular workload orchestrator promises to offload the incoming tasks (workload) to the optimal computing unit to improve the system performance. Since the service requests are created one after the other in a dynamic fashion, workload orchestration is an online problem and cannot be solved by the formal optimization tools like CPLEX and Gurobi. We propose an ML-based workload orchestrator for multi-access multi-tier VEC to maximize the percentage of satisfied services by dynamically changing network conditions and server utilization. It can handle the uncertain nonlinear systems efficiently by considering multiple criteria.

## Simulated Environment

The road and mobility model used in the simulations is implemented on EdgeCloudSim, as shown in Figure 6.9. To simulate the vehicular mobility more realistically, the road is divided into segments, and a dynamic velocity value for the vehicle position is used. Therefore, the speed of the vehicles varies at each segment to differentiate the traffic density on the road. The hotspot locations, which are shown with red color, occur due to the traffic jam. 100 to 1800 vehicles are distributed to random locations when the simulation is started. Then they move in a single direction with a predefined speed with respect to the crossed segment. The road is modeled as a circular route to keep the number of vehicles the same during the simulation.

<p align="center">
  <img src="/doc/images/sample_app5/road.png" width="45%">
  <p align="center">
    Figure 4: Vehicular mobility model in the simulation.
  </p>
</p>


## References
**[1]** C. Sonmez, A. Ozgovde and C. Ersoy, "[Machine Learning-Based Workload Orchestrator for Vehicular Edge Computing](https://ieeexplore.ieee.org/abstract/document/9208723/)," in *IEEE Transactions on Intelligent Transportation Systems*, doi: 10.1109/TITS.2020.3024233.
