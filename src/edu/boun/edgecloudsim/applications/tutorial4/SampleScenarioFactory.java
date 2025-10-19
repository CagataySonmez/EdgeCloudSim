/*
 * Title:        EdgeCloudSim - Scenario Factory
 * 
 * Description:  VehicularScenarioFactory provides the default
 *               instances of required abstract classes 
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.tutorial4;

import edu.boun.edgecloudsim.cloud_server.CloudServerManager;
import edu.boun.edgecloudsim.cloud_server.DefaultCloudServerManager;
import edu.boun.edgecloudsim.core.ScenarioFactory;
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.EdgeServerManager;
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.DefaultMobileServerManager;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileServerManager;
import edu.boun.edgecloudsim.mobility.MobilityModel;
import edu.boun.edgecloudsim.task_generator.LoadGeneratorModel;
import edu.boun.edgecloudsim.network.NetworkModel;

public class SampleScenarioFactory implements ScenarioFactory {
	// total number of mobile (edge) devices to simulate
	private int numOfMobileDevice;
	// total simulation duration in seconds
	private double simulationTime;
	// orchestration policy identifier (e.g., "RANDOM", "LOAD_AWARE", etc.)
	private String orchestratorPolicy;
	// scenario identifier used to switch parameter sets / behavior
	private String simScenario;

	/**
	 * Constructs a scenario factory with all core simulation parameters.
	 * @param _numOfMobileDevice number of mobile devices
	 * @param _simulationTime total simulation time (seconds)
	 * @param _orchestratorPolicy selection key for edge orchestrator strategy
	 * @param _simScenario scenario key influencing load, mobility, network, etc.
	 */
	SampleScenarioFactory(int _numOfMobileDevice,
			double _simulationTime,
			String _orchestratorPolicy,
			String _simScenario){
		// store provided configuration for later object creation
		orchestratorPolicy = _orchestratorPolicy;
		numOfMobileDevice = _numOfMobileDevice;
		simulationTime = _simulationTime;
		simScenario = _simScenario;
	}

	@Override
	public LoadGeneratorModel getLoadGeneratorModel() {
		// Provides workload generator matching device count, duration and scenario specifics
		return new SampleLoadGenerator(numOfMobileDevice, simulationTime, simScenario);
	}

	@Override
	public EdgeOrchestrator getEdgeOrchestrator() {
		// Returns orchestrator instance configured with chosen policy
		return new SampleEdgeOrchestrator(orchestratorPolicy, simScenario);
	}

	@Override
	public MobilityModel getMobilityModel() {
		// Mobility model uses number of devices and simulation time
		return new SampleMobilityModel(numOfMobileDevice,simulationTime);
	}

	@Override
	public NetworkModel getNetworkModel() {
		// Network model may vary by scenario (e.g., different latency patterns)
		return new SampleNetworkModel(numOfMobileDevice, simScenario);
	}

	@Override
	public EdgeServerManager getEdgeServerManager() {
		// Edge server manager instantiated per scenario configuration
		return new SampleEdgeServerManager(simScenario);
	}
	@Override
	public CloudServerManager getCloudServerManager() {
		// Default cloud manager (central cloud)
		return new DefaultCloudServerManager();
	}

	@Override
	public MobileDeviceManager getMobileDeviceManager() throws Exception {
		// Creates and maintains mobile devices and their tasks
		return new SampleMobileDeviceManager();
	}

	@Override
	public MobileServerManager getMobileServerManager() {
		// Provides local (on-device) processing capability abstraction
		return new DefaultMobileServerManager();
	}
}
