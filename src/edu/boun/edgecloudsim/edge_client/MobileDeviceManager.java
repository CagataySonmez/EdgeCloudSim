package edu.boun.edgecloudsim.edge_client;

import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.UtilizationModel;

import edu.boun.edgecloudsim.utils.TaskProperty;

/**
 * Abstract base class for managing mobile device operations in EdgeCloudSim.
 * Extends CloudSim's DatacenterBroker to handle task submission and orchestration
 * for mobile devices in edge computing scenarios.
 */
public abstract class MobileDeviceManager  extends DatacenterBroker {

	/**
	 * Constructor for MobileDeviceManager.
	 * Initializes the global broker for managing mobile device tasks.
	 * @throws Exception if broker initialization fails
	 */
	public MobileDeviceManager() throws Exception {
		super("Global_Broker");
	}
	
	/**
	 * Initializes the mobile device manager with required configurations.
	 * Called before starting task submission and management operations.
	 */
	public abstract void initialize();
	
	/**
	 * Provides the CPU utilization model for tasks.
	 * Defines how CPU resources are utilized during task execution.
	 * @return UtilizationModel instance for CPU resource modeling
	 */
	public abstract UtilizationModel getCpuUtilizationModel();
	
	/**
	 * Submits a task from a mobile device for processing.
	 * Handles task orchestration and offloading decisions.
	 * @param edgeTask The task properties and requirements to be processed
	 */
	public abstract void submitTask(TaskProperty edgeTask);
}
