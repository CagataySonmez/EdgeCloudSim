/*
 * Title:        EdgeCloudSim - Scenario Factory
 * 
 * Description:  Sample scenario factory providing the default
 *               instances of required abstract classes
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.tutorial3;

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

// Factory responsibilities:
// - Centralizes creation of all pluggable simulation components for tutorial3
// - Encapsulates scenario parameters (device count, duration, orchestrator policy, scenario label)
// Extension points: replace returned concrete classes with custom implementations to change behavior.

public class SampleScenarioFactory implements ScenarioFactory {
	private int numOfMobileDevice;
	private double simulationTime;
	private String orchestratorPolicy;
	private String simScenario;
	
	SampleScenarioFactory(int _numOfMobileDevice,
			double _simulationTime,
			String _orchestratorPolicy,
			String _simScenario){
		// _numOfMobileDevice : total mobile devices
		// _simulationTime    : total simulated time (seconds)
		// _orchestratorPolicy: offloading / placement policy identifier
		// _simScenario       : scenario name used for conditional logic
		orchestratorPolicy = _orchestratorPolicy;
		numOfMobileDevice = _numOfMobileDevice;
		simulationTime = _simulationTime;
		simScenario = _simScenario;
	}
	
	@Override
	public LoadGeneratorModel getLoadGeneratorModel() {
		// Provides per-device workload (idle/active cycles)
		return new IdleActiveLoadGenerator(numOfMobileDevice, simulationTime, simScenario);
	}

	@Override
	public EdgeOrchestrator getEdgeOrchestrator() {
		// Offloading target & VM selection logic
		return new SampleEdgeOrchestrator(orchestratorPolicy, simScenario);
	}

	@Override
	public MobilityModel getMobilityModel() {
		// Supplies mobility pattern (Nomadic relocation)
		return new NomadicMobility(numOfMobileDevice,simulationTime);
	}

	@Override
	public NetworkModel getNetworkModel() {
		// Network latency/throughput + MAN queue model
		return new SampleNetworkModel(numOfMobileDevice, simScenario);
	}

	@Override
	public EdgeServerManager getEdgeServerManager() {
		// Edge infrastructure (datacenters/hosts/VMs)
		return new DefaultEdgeServerManager();
	}
	
	@Override
	public CloudServerManager getCloudServerManager() {
		// Remote cloud resources (default implementation)
		return new DefaultCloudServerManager();
	}

	@Override
	public MobileDeviceManager getMobileDeviceManager() throws Exception {
		// Manages task submission, network events, logging
		return new SampleMobileDeviceManager();
	}

	@Override
	public MobileServerManager getMobileServerManager() {
		// Local (on-device) processing support
		return new DefaultMobileServerManager();
	}
}
