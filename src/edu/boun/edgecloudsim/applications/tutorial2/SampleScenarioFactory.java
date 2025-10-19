/*
 * Title:        EdgeCloudSim - Scenario Factory
 * 
 * Description:  Sample scenario factory providing the default
 *               instances of required abstract classes
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.tutorial2;

import edu.boun.edgecloudsim.cloud_server.CloudServerManager;
import edu.boun.edgecloudsim.cloud_server.DefaultCloudServerManager;
import edu.boun.edgecloudsim.core.ScenarioFactory;
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.EdgeServerManager;
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileServerManager;
import edu.boun.edgecloudsim.mobility.MobilityModel;
import edu.boun.edgecloudsim.mobility.NomadicMobility;
import edu.boun.edgecloudsim.task_generator.IdleActiveLoadGenerator;
import edu.boun.edgecloudsim.task_generator.LoadGeneratorModel;
import edu.boun.edgecloudsim.network.NetworkModel;

// Factory responsibilities:
// - Central point to supply concrete implementations for pluggable simulation components
// - Encapsulates scenario parameters (population, duration, policy, scenario label)
// Extension: replace returned instances with custom subclasses to change behavior.

public class SampleScenarioFactory implements ScenarioFactory {
	private int numOfMobileDevice;
	private double simulationTime;
	private String orchestratorPolicy;
	private String simScenario;
	
	SampleScenarioFactory(int _numOfMobileDevice,
			double _simulationTime,
			String _orchestratorPolicy,
			String _simScenario){
		// _numOfMobileDevice : total mobile devices to instantiate
		// _simulationTime    : total simulated seconds
		// _orchestratorPolicy: offloading / VM selection policy identifier
		// _simScenario       : scenario name used for conditional logic in components
		orchestratorPolicy = _orchestratorPolicy;
		numOfMobileDevice = _numOfMobileDevice;
		simulationTime = _simulationTime;
		simScenario = _simScenario;
	}
	
	@Override
	public LoadGeneratorModel getLoadGeneratorModel() {
		// Generates task arrival pattern (idle/active cycles) per device
		return new IdleActiveLoadGenerator(numOfMobileDevice, simulationTime, simScenario);
	}

	@Override
	public EdgeOrchestrator getEdgeOrchestrator() {
		// Offloading decision + VM selection logic
		return new SampleEdgeOrchestrator(orchestratorPolicy, simScenario);
	}

	@Override
	public MobilityModel getMobilityModel() {
		// Provides device movement (Nomadic relocation between zones)
		return new NomadicMobility(numOfMobileDevice,simulationTime);
	}

	@Override
	public NetworkModel getNetworkModel() {
		// Supplies network latency/throughput model (empirical tables + MAN delay)
		return new SampleNetworkModel(numOfMobileDevice, simScenario);
	}

	@Override
	public EdgeServerManager getEdgeServerManager() {
		// Builds edge datacenters, hosts, VMs and exposes utilization metrics
		return new SampleEdgeServerManager();
	}
	
	@Override
	public CloudServerManager getCloudServerManager() {
		// Optional remote cloud resources (default implementation)
		return new DefaultCloudServerManager();
	}

	@Override
	public MobileDeviceManager getMobileDeviceManager() throws Exception {
		// Manages task lifecycle, offloading pipeline, logging
		return new SampleMobileDeviceManager();
	}

	@Override
	public MobileServerManager getMobileServerManager() {
		// Provides per-device local processing (one MobileVM per device)
		return new SampleMobileServerManager(numOfMobileDevice);
	}
}
