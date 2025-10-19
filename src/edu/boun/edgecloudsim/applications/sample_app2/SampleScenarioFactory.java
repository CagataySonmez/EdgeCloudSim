/*
 * Title:        EdgeCloudSim - Scenario Factory
 * 
 * Description:  Sample scenario factory providing the default
 *               instances of required abstract classes
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app2;

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
 * Sample scenario factory for multi-tier edge-cloud computing scenarios.
 * Creates EdgeCloudSim components configured for complex network topologies
 * with empirical WLAN/WAN models and MM1 queue-based MAN simulation.
 */
public class SampleScenarioFactory implements ScenarioFactory {
	/** Number of mobile devices in the simulation */
	private int numOfMobileDevice;
	/** Total simulation time in seconds */
	private double simulationTime;
	/** Orchestration policy (NETWORK_BASED, UTILIZATION_BASED, HYBRID) */
	private String orchestratorPolicy;
	/** Simulation scenario (SINGLE_TIER, TWO_TIER_WITH_EO) */
	private String simScenario;
	
	/**
	 * Constructor for sample scenario factory.
	 * @param _numOfMobileDevice Number of mobile devices in simulation
	 * @param _simulationTime Total simulation duration in seconds
	 * @param _orchestratorPolicy Orchestration policy for task placement decisions
	 * @param _simScenario Scenario type defining network topology complexity
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
	 * Creates load generator model for realistic mobile app usage patterns.
	 * @return IdleActiveLoadGenerator that simulates periods of activity and idleness
	 */
	@Override
	public LoadGeneratorModel getLoadGeneratorModel() {
		return new IdleActiveLoadGenerator(numOfMobileDevice, simulationTime, simScenario);
	}

	/**
	 * Creates sample edge orchestrator supporting multi-tier edge-cloud decisions.
	 * @return SampleEdgeOrchestrator with configured orchestration policy and scenario
	 */
	@Override
	public EdgeOrchestrator getEdgeOrchestrator() {
		return new SampleEdgeOrchestrator(orchestratorPolicy, simScenario);
	}

	/**
	 * Creates mobility model for mobile device movement simulation.
	 * @return NomadicMobility model for realistic user mobility patterns
	 */
	@Override
	public MobilityModel getMobilityModel() {
		return new NomadicMobility(numOfMobileDevice,simulationTime);
	}

	/**
	 * Creates network model with empirical WLAN/WAN data and MM1 MAN modeling.
	 * @return SampleNetworkModel with real-world network characteristics
	 */
	@Override
	public NetworkModel getNetworkModel() {
		return new SampleNetworkModel(numOfMobileDevice, simScenario);
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
	 * Creates mobile device manager for multi-tier task handling.
	 * @return SampleMobileDeviceManager supporting complex network routing
	 * @throws Exception if mobile device manager creation fails
	 */
	@Override
	public MobileDeviceManager getMobileDeviceManager() throws Exception {
		return new SampleMobileDeviceManager();
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
