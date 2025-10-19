/*
 * Title:        EdgeCloudSim - Edge Orchestrator
 * 
 * Description: 
 * EdgeOrchestrator is an abstract class which is used for selecting VM
 * for each client requests. For those who wants to add a custom 
 * Edge Orchestrator to EdgeCloudSim should extend this class and provide
 * a concrete instance via ScenarioFactory
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_orchestrator;

import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.SimEntity;

import edu.boun.edgecloudsim.edge_client.Task;

/**
 * Abstract base class for edge orchestration strategies in EdgeCloudSim.
 * 
 * This class defines the interface for task placement and VM selection decisions
 * in edge computing environments. Edge orchestrators implement the logic for
 * determining where tasks should be executed (edge, cloud, or mobile devices)
 * and which specific VM should handle each task.
 * 
 * Custom orchestration policies can be implemented by extending this class
 * and providing concrete implementations of the abstract methods. The
 * orchestrator instance should be created via the ScenarioFactory pattern.
 */
public abstract class EdgeOrchestrator extends SimEntity{
	protected String policy;        // Orchestration policy name/identifier
	protected String simScenario;   // Current simulation scenario name
	
	/**
	 * Constructs an EdgeOrchestrator with specified policy and scenario.
	 * 
	 * @param _policy Name of the orchestration policy to use
	 * @param _simScenario Name of the simulation scenario being executed
	 */
	public EdgeOrchestrator(String _policy, String _simScenario){
		super("EdgeOrchestrator");
		policy = _policy;
		simScenario = _simScenario;
	}

	/**
	 * Default constructor creating an EdgeOrchestrator without initial configuration.
	 * Policy and scenario can be set later through initialization methods.
	 */
	public EdgeOrchestrator() {
        	super("EdgeOrchestrator");
	}

	/**
	 * Initializes the edge orchestrator with scenario-specific configurations.
	 * This method should set up any required data structures, load balancing
	 * parameters, or other orchestration-specific initialization logic.
	 */
	public abstract void initialize();
	
	/**
	 * Determines the target device type for task offloading.
	 * Makes the high-level decision about where a task should be executed
	 * based on orchestration policy, task characteristics, and system state.
	 * 
	 * @param task The task requiring placement decision
	 * @return Device type identifier (e.g., edge server, cloud, mobile device)
	 */
	public abstract int getDeviceToOffload(Task task);
	
	/**
	 * Selects the specific VM to handle a task on the chosen device type.
	 * Implements the fine-grained VM selection logic within the target
	 * device category determined by getDeviceToOffload().
	 * 
	 * @param task The task to be assigned to a VM
	 * @param deviceId The device type/category where the task should run
	 * @return The selected VM instance for task execution
	 */
	public abstract Vm getVmToOffload(Task task, int deviceId);
}
