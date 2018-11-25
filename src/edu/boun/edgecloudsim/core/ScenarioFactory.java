/*
 * Title:        EdgeCloudSim - Scenarion Factory interface
 * 
 * Description: 
 * ScenarioFactory responsible for providing customizable components
 * such as  Network Model, Mobility Model, Edge Orchestrator.
 * This interface is very critical for using custom models on EdgeCloudSim
 * This interface should be implemented by EdgeCloudSim users
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.core;

import edu.boun.edgecloudsim.cloud_server.CloudServerManager;
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileServerManager;
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.EdgeServerManager;
import edu.boun.edgecloudsim.mobility.MobilityModel;
import edu.boun.edgecloudsim.task_generator.LoadGeneratorModel;
import edu.boun.edgecloudsim.network.NetworkModel;

public interface ScenarioFactory {
	/**
	 * provides abstract Load Generator Model
	 */
	public LoadGeneratorModel getLoadGeneratorModel();

	/**
	 * provides abstract Edge Orchestrator
	 */
	public EdgeOrchestrator getEdgeOrchestrator();

	/**
	 * provides abstract Mobility Model
	 */
	public MobilityModel getMobilityModel();

	/**
	 * provides abstract Network Model
	 */
	public NetworkModel getNetworkModel();

	/**
	 * provides abstract Edge Server Model
	 */
	public EdgeServerManager getEdgeServerManager();

	/**
	 * provides abstract Cloud Server Model
	 */
	public CloudServerManager getCloudServerManager();

	/**
	 * provides abstract Mobile Server Model
	 */
	public MobileServerManager getMobileServerManager();

	/**
	 * provides abstract Mobile Device Manager Model
	 */
	public MobileDeviceManager getMobileDeviceManager() throws Exception;
}
