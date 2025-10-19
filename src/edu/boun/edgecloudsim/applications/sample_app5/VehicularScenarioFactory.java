/*
 * Title:        EdgeCloudSim - Scenario Factory
 * 
 * Description:  VehicularScenarioFactory provides the default
 *               instances of required abstract classes 
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app5;

import edu.boun.edgecloudsim.cloud_server.CloudServerManager;
import edu.boun.edgecloudsim.cloud_server.DefaultCloudServerManager;
import edu.boun.edgecloudsim.core.ScenarioFactory;
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.EdgeServerManager;
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileServerManager;
import edu.boun.edgecloudsim.mobility.MobilityModel;
import edu.boun.edgecloudsim.task_generator.LoadGeneratorModel;
import edu.boun.edgecloudsim.network.NetworkModel;

public class VehicularScenarioFactory implements ScenarioFactory {
	private int numOfMobileDevice;      // Number of vehicles in the simulation
	private double simulationTime;      // Total simulation duration in seconds
	private String orchestratorPolicy;  // Orchestration algorithm to use (e.g., AI_BASED, MAB, GAME_THEORY)
	private String simScenario;         // Simulation scenario identifier

	/**
	 * Constructor for vehicular scenario factory.
	 * 
	 * @param _numOfMobileDevice number of mobile devices (vehicles)
	 * @param _simulationTime simulation duration in seconds
	 * @param _orchestratorPolicy orchestration policy name
	 * @param _simScenario simulation scenario identifier
	 */
	VehicularScenarioFactory(int _numOfMobileDevice,
			double _simulationTime,
			String _orchestratorPolicy,
			String _simScenario){
		orchestratorPolicy = _orchestratorPolicy;
		numOfMobileDevice = _numOfMobileDevice;
		simulationTime = _simulationTime;
		simScenario = _simScenario;
	}

	/**
	 * Creates the load generator model for vehicular applications.
	 * 
	 * @return VehicularLoadGenerator instance for generating task workloads
	 */
	@Override
	public LoadGeneratorModel getLoadGeneratorModel() {
		return new VehicularLoadGenerator(numOfMobileDevice, simulationTime, simScenario);
	}

	/**
	 * Creates the edge orchestrator for task offloading decisions.
	 * 
	 * @return VehicularEdgeOrchestrator instance with specified policy
	 */
	@Override
	public EdgeOrchestrator getEdgeOrchestrator() {
		return new VehicularEdgeOrchestrator(numOfMobileDevice, orchestratorPolicy, simScenario);
	}

	/**
	 * Creates the mobility model for vehicle movement simulation.
	 * 
	 * @return VehicularMobilityModel instance for vehicular mobility patterns
	 */
	@Override
	public MobilityModel getMobilityModel() {
		return new VehicularMobilityModel(numOfMobileDevice,simulationTime);
	}

	/**
	 * Creates the network model for communication delays and protocols.
	 * 
	 * @return VehicularNetworkModel instance for vehicular network simulation
	 */
	@Override
	public NetworkModel getNetworkModel() {
		return new VehicularNetworkModel(numOfMobileDevice, simScenario, orchestratorPolicy);
	}

	/**
	 * Creates the edge server manager for edge infrastructure.
	 * 
	 * @return VehicularEdgeServerManager instance for managing edge resources
	 */
	@Override
	public EdgeServerManager getEdgeServerManager() {
		return new VehicularEdgeServerManager();
	}

	/**
	 * Creates the cloud server manager for cloud infrastructure.
	 * 
	 * @return DefaultCloudServerManager instance for managing cloud resources
	 */
	@Override
	public CloudServerManager getCloudServerManager() {
		return new DefaultCloudServerManager();
	}

	/**
	 * Creates the mobile device manager for handling vehicle-side operations.
	 * 
	 * @return VehicularMobileDeviceManager instance for managing mobile devices
	 * @throws Exception if device manager cannot be created
	 */
	@Override
	public MobileDeviceManager getMobileDeviceManager() throws Exception {
		return new VehicularMobileDeviceManager();
	}

	/**
	 * Creates the mobile server manager for local processing capabilities.
	 * 
	 * @return VehicularMobileServerManager instance for managing mobile processing units
	 */
	@Override
	public MobileServerManager getMobileServerManager() {
		return new VehicularMobileServerManager(numOfMobileDevice);
	}
}
