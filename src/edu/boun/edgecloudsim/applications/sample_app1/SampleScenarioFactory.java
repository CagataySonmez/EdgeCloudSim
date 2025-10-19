/*
 * Title:        EdgeCloudSim - Scenario Factory
 * 
 * Description:  Sample scenario factory providing the default
 *               instances of required abstract classes
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app1;

import edu.boun.edgecloudsim.cloud_server.CloudServerManager;
import edu.boun.edgecloudsim.cloud_server.DefaultCloudServerManager;
import edu.boun.edgecloudsim.core.ScenarioFactory;
import edu.boun.edgecloudsim.edge_orchestrator.BasicEdgeOrchestrator;
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.DefaultEdgeServerManager;
import edu.boun.edgecloudsim.edge_server.EdgeServerManager;
import edu.boun.edgecloudsim.edge_client.DefaultMobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.DefaultMobileServerManager;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileServerManager;
import edu.boun.edgecloudsim.mobility.MobilityModel;
import edu.boun.edgecloudsim.mobility.NomadicMobility;
import edu.boun.edgecloudsim.task_generator.IdleActiveLoadGenerator;
import edu.boun.edgecloudsim.task_generator.LoadGeneratorModel;
import edu.boun.edgecloudsim.network.MM1Queue;
import edu.boun.edgecloudsim.network.NetworkModel;

/**
 * Sample scenario factory implementation providing default instances
 * of required EdgeCloudSim components for basic edge computing scenarios.
 * Uses standard MM1 queue network model and nomadic mobility patterns.
 */
public class SampleScenarioFactory implements ScenarioFactory {
	private int numOfMobileDevice;
	private double simulationTime;
	private String orchestratorPolicy;
	private String simScenario;
	
	/**
	 * Constructor for sample scenario factory.
	 * 
	 * @param _numOfMobileDevice Number of mobile devices in the simulation
	 * @param _simulationTime Total simulation time in seconds
	 * @param _orchestratorPolicy Orchestrator policy for task offloading decisions
	 * @param _simScenario Simulation scenario type (e.g., SINGLE_TIER, TWO_TIER)
	 */
	SampleScenarioFactory(int _numOfMobileDevice,
			double _simulationTime,
			String _orchestratorPolicy,
			String _simScenario){
		orchestratorPolicy = _orchestratorPolicy;
		numOfMobileDevice = _numOfMobileDevice;
		simulationTime = _simulationTime;
		simScenario = _simScenario;
	}
	
	/**
	 * Creates load generator model for task generation patterns.
	 * @return IdleActiveLoadGenerator with idle/active periods for realistic workload
	 */
	@Override
	public LoadGeneratorModel getLoadGeneratorModel() {
		return new IdleActiveLoadGenerator(numOfMobileDevice, simulationTime, simScenario);
	}

	/**
	 * Creates edge orchestrator for task offloading decisions.
	 * @return BasicEdgeOrchestrator with configured policy and scenario
	 */
	@Override
	public EdgeOrchestrator getEdgeOrchestrator() {
		return new BasicEdgeOrchestrator(orchestratorPolicy, simScenario);
	}

	/**
	 * Creates mobility model for device movement patterns.
	 * @return NomadicMobility model for nomadic movement behavior
	 */
	@Override
	public MobilityModel getMobilityModel() {
		return new NomadicMobility(numOfMobileDevice,simulationTime);
	}

	/**
	 * Creates network model for communication delay simulation.
	 * @return MM1Queue model for queueing theory-based network delays
	 */
	@Override
	public NetworkModel getNetworkModel() {
		return new MM1Queue(numOfMobileDevice, simScenario);
	}

	/**
	 * Creates edge server manager for managing edge computing resources.
	 * @return DefaultEdgeServerManager for standard edge server operations
	 */
	@Override
	public EdgeServerManager getEdgeServerManager() {
		return new DefaultEdgeServerManager();
	}

	/**
	 * Creates cloud server manager for managing cloud computing resources.
	 * @return DefaultCloudServerManager for standard cloud server operations
	 */
	@Override
	public CloudServerManager getCloudServerManager() {
		return new DefaultCloudServerManager();
	}
	
	/**
	 * Creates mobile device manager for handling mobile device operations.
	 * @return DefaultMobileDeviceManager for standard mobile device management
	 * @throws Exception if mobile device manager creation fails
	 */
	@Override
	public MobileDeviceManager getMobileDeviceManager() throws Exception {
		return new DefaultMobileDeviceManager();
	}

	/**
	 * Creates mobile server manager for mobile device processing units.
	 * @return DefaultMobileServerManager for standard mobile device operations
	 */
	@Override
	public MobileServerManager getMobileServerManager() {
		return new DefaultMobileServerManager();
	}
}
