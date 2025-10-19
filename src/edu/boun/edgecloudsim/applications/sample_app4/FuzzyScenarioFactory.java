/*
 * Title:        EdgeCloudSim - Sample Scenario Factory
 * 
 * Description:  Sample factory providing the default
 *               instances of required abstract classes 
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app4;

import edu.boun.edgecloudsim.cloud_server.CloudServerManager;
import edu.boun.edgecloudsim.cloud_server.DefaultCloudServerManager;
import edu.boun.edgecloudsim.core.ScenarioFactory;
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.DefaultEdgeServerManager;
import edu.boun.edgecloudsim.edge_server.EdgeServerManager;
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.DefaultMobileServerManager;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileServerManager;
import edu.boun.edgecloudsim.mobility.MobilityModel;
import edu.boun.edgecloudsim.mobility.NomadicMobility;
import edu.boun.edgecloudsim.task_generator.IdleActiveLoadGenerator;
import edu.boun.edgecloudsim.task_generator.LoadGeneratorModel;
import edu.boun.edgecloudsim.network.NetworkModel;

/**
 * Factory class for creating fuzzy logic-based EdgeCloudSim components.
 * Implements the abstract factory pattern to provide scenario-specific instances
 * of required simulation components with fuzzy logic integration.
 */
public class FuzzyScenarioFactory implements ScenarioFactory {
	/** Number of mobile devices to be simulated */
	private int numOfMobileDevice;
	/** Total simulation time in seconds */
	private double simulationTime;
	/** Orchestration policy (FUZZY_BASED, FUZZY_COMPETITOR, etc.) */
	private String orchestratorPolicy;
	/** Simulation scenario identifier for configuration */
	private String simScenario;
	
	/**
	 * Constructor for fuzzy scenario factory.
	 * @param _numOfMobileDevice Number of mobile devices in simulation
	 * @param _simulationTime Total simulation duration in seconds
	 * @param _orchestratorPolicy Orchestration policy for task offloading decisions
	 * @param _simScenario Scenario identifier for configuration selection
	 */
	FuzzyScenarioFactory(int _numOfMobileDevice,
			double _simulationTime,
			String _orchestratorPolicy,
			String _simScenario){
		// Initialize simulation parameters
		orchestratorPolicy = _orchestratorPolicy;
		numOfMobileDevice = _numOfMobileDevice;
		simulationTime = _simulationTime;
		simScenario = _simScenario;
	}
	
	/**
	 * Creates the load generator model for task generation patterns.
	 * @return IdleActiveLoadGenerator that simulates mobile app usage patterns
	 */
	@Override
	public LoadGeneratorModel getLoadGeneratorModel() {
		// Use idle-active pattern to simulate realistic mobile device usage
		return new IdleActiveLoadGenerator(numOfMobileDevice, simulationTime, simScenario);
	}

	/**
	 * Creates the fuzzy logic-based edge orchestrator for task offloading decisions.
	 * @return FuzzyEdgeOrchestrator configured with specified policy and scenario
	 */
	@Override
	public EdgeOrchestrator getEdgeOrchestrator() {
		// Create fuzzy orchestrator with two-stage FIS for intelligent offloading
		return new FuzzyEdgeOrchestrator(orchestratorPolicy, simScenario);
	}

	/**
	 * Creates the mobility model for simulating mobile device movement patterns.
	 * @return NomadicMobility model for realistic user movement simulation
	 */
	@Override
	public MobilityModel getMobilityModel() {
		// Use nomadic mobility pattern for realistic mobile device movement
		return new NomadicMobility(numOfMobileDevice,simulationTime);
	}

	/**
	 * Creates the experimental network model with empirical throughput data.
	 * @return FuzzyExperimentalNetworkModel with realistic WLAN/WAN characteristics
	 */
	@Override
	public NetworkModel getNetworkModel() {
		// Use experimental network model with empirical data for accurate delay simulation
		return new FuzzyExperimentalNetworkModel(numOfMobileDevice, simScenario);
	}

	/**
	 * Creates the edge server manager for managing edge computing resources.
	 * @return DefaultEdgeServerManager for standard edge server operations
	 */
	@Override
	public EdgeServerManager getEdgeServerManager() {
		return new DefaultEdgeServerManager();
	}
	
	/**
	 * Creates the cloud server manager for managing cloud computing resources.
	 * @return DefaultCloudServerManager for standard cloud server operations
	 */
	@Override
	public CloudServerManager getCloudServerManager() {
		return new DefaultCloudServerManager();
	}

	/**
	 * Creates the mobile server manager for local mobile device processing.
	 * @return DefaultMobileServerManager for mobile device computation handling
	 */
	@Override
	public MobileServerManager getMobileServerManager() {
		return new DefaultMobileServerManager();
	}
	
	/**
	 * Creates the fuzzy mobile device manager for handling task submissions.
	 * @return FuzzyMobileDeviceManager with integrated fuzzy logic capabilities
	 * @throws Exception if mobile device manager creation fails
	 */
	@Override
	public MobileDeviceManager getMobileDeviceManager() throws Exception {
		// Create fuzzy-enabled mobile device manager for intelligent task handling
		return new FuzzyMobileDeviceManager();
	}
}
