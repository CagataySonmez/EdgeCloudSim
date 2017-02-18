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

import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.VmAllocationPolicy;

import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
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
	 * provides abstract CPU Utilization Model
	 */
	public UtilizationModel getCpuUtilizationModel(SimSettings.APP_TYPES _taskType);

	/**
	 * provides abstract Vm Allocation Policy
	 */
	public VmAllocationPolicy getVmAllocationPolicy(List<? extends Host> list, int dataCenterIndex);
}
