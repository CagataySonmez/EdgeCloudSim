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

/**
 * Factory interface for creating customizable EdgeCloudSim simulation components.
 * This interface is critical for extending EdgeCloudSim with custom models and must be
 * implemented by users who want to provide their own simulation components.
 * Each method creates a specific component of the edge computing simulation environment.
 */
public interface ScenarioFactory {
	/**
	 * Creates the load generator model for controlling task generation patterns.
	 * Defines how and when mobile devices generate computational tasks.
	 * @return LoadGeneratorModel instance for task generation simulation
	 */
	public LoadGeneratorModel getLoadGeneratorModel();

	/**
	 * Creates the edge orchestrator for making task offloading decisions.
	 * Determines where tasks should be executed (edge, cloud, or mobile).
	 * @return EdgeOrchestrator instance for intelligent task placement
	 */
	public EdgeOrchestrator getEdgeOrchestrator();

	/**
	 * Creates the mobility model for simulating device movement patterns.
	 * Defines how mobile devices move within the simulation environment.
	 * @return MobilityModel instance for device location tracking
	 */
	public MobilityModel getMobilityModel();

	/**
	 * Creates the network model for simulating communication delays and bandwidth.
	 * Models WLAN, WAN, and MAN network characteristics and performance.
	 * @return NetworkModel instance for network delay and capacity simulation
	 */
	public NetworkModel getNetworkModel();

	/**
	 * Creates the edge server manager for managing edge computing infrastructure.
	 * Handles edge datacenter operations, hosts, and VMs.
	 * @return EdgeServerManager instance for edge resource management
	 */
	public EdgeServerManager getEdgeServerManager();

	/**
	 * Creates the cloud server manager for managing cloud computing infrastructure.
	 * Handles cloud datacenter operations, hosts, and VMs for remote processing.
	 * @return CloudServerManager instance for cloud resource management
	 */
	public CloudServerManager getCloudServerManager();

	/**
	 * Creates the mobile server manager for managing mobile device processing units.
	 * Handles local computational capabilities on mobile devices (if enabled).
	 * @return MobileServerManager instance for mobile processing management
	 */
	public MobileServerManager getMobileServerManager();

	/**
	 * Creates the mobile device manager for handling mobile device operations.
	 * Manages task submission, orchestration, and communication for mobile devices.
	 * @return MobileDeviceManager instance for mobile device coordination
	 * @throws Exception if mobile device manager creation fails
	 */
	public MobileDeviceManager getMobileDeviceManager() throws Exception;
}
