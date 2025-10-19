/*
 * Title:        EdgeCloudSim - Scenario Factory
 * 
 * Description:  Sample scenario factory providing the default
 *               instances of required abstract classes
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.tutorial5;

import edu.boun.edgecloudsim.cloud_server.CloudServerManager;
import edu.boun.edgecloudsim.cloud_server.DefaultCloudServerManager;
import edu.boun.edgecloudsim.core.ScenarioFactory;
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
import edu.boun.edgecloudsim.network.NetworkModel;

// Factory responsibilities:
// - Centralizes creation of pluggable components for tutorial5
// - Encapsulates scenario parameters (device count, duration, policy, scenario label)
// Extension points: replace returned implementations to customize behavior (mobility, network, orchestrator, etc.)
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
		// _simulationTime    : total simulated seconds
		// _orchestratorPolicy: offloading / placement policy id
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
		// Offloading & VM selection strategy
		return new SampleEdgeOrchestrator(orchestratorPolicy, simScenario);
	}

	@Override
	public MobilityModel getMobilityModel() {
		// Nomadic mobility pattern
		return new NomadicMobility(numOfMobileDevice,simulationTime);
	}

	@Override
	public NetworkModel getNetworkModel() {
		// Analytical WLAN delay model (M/M/1 or M/M/2 depending on scenario)
		return new SampleNetworkModel(numOfMobileDevice, simScenario);
	}

	@Override
	public EdgeServerManager getEdgeServerManager() {
		// Edge infrastructure provisioning (hosts/VMs)
		return new DefaultEdgeServerManager();
	}

	@Override
	public CloudServerManager getCloudServerManager() {
		// Optional remote cloud resources
		return new DefaultCloudServerManager();
	}
	
	@Override
	public MobileDeviceManager getMobileDeviceManager() throws Exception {
		// Default mobile device task lifecycle manager
		return new DefaultMobileDeviceManager();
	}

	@Override
	public MobileServerManager getMobileServerManager() {
		// Local (on-device) processing manager
		return new DefaultMobileServerManager();
	}
}
