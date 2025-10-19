/*
 * Title:        EdgeCloudSim - Load Generator Model
 * 
 * Description: 
 * LoadGeneratorModel is an abstract class which is used for 
 * deciding task generation pattern via a task list. For those who
 * wants to add a custom Load Generator Model to EdgeCloudSim should
 * extend this class and provide a concrete instance via ScenarioFactory
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.task_generator;

import java.util.List;

import edu.boun.edgecloudsim.utils.TaskProperty;

/**
 * Abstract base class for task load generation models in EdgeCloudSim.
 * 
 * <p>This class provides the foundation for implementing different task generation patterns
 * that determine when and how mobile devices create computational tasks. Load generator models
 * are responsible for creating realistic workload patterns that simulate user behavior
 * and application usage in edge computing scenarios.</p>
 * 
 * <p>The load generator works by pre-calculating a complete list of tasks with their
 * scheduled execution times during simulation initialization. This approach enables:
 * <ul>
 *   <li>Deterministic and reproducible simulation results</li>
 *   <li>Complex task generation patterns (idle/active periods, bursty traffic)</li>
 *   <li>Statistical distribution-based task characteristics</li>
 *   <li>Device-specific task type assignments</li>
 * </ul></p>
 * 
 * <p><b>Implementation Requirements:</b>
 * <ul>
 *   <li>Implement {@link #initializeModel()} to populate the task list</li>
 *   <li>Implement {@link #getTaskTypeOfDevice(int)} for device-task type mapping</li>
 *   <li>Use simulation parameters from SimSettings for realistic patterns</li>
 * </ul></p>
 * 
 * @see edu.boun.edgecloudsim.core.ScenarioFactory
 * @see edu.boun.edgecloudsim.utils.TaskProperty
 */
public abstract class LoadGeneratorModel {
	/** Pre-calculated list of all tasks to be executed during simulation */
	protected List<TaskProperty> taskList;
	
	/** Total number of mobile devices in the simulation */
	protected int numberOfMobileDevices;
	
	/** Total simulation duration in seconds */
	protected double simulationTime;
	
	/** Simulation scenario identifier for configuration-specific behavior */
	protected String simScenario;
	
	/**
	 * Constructs a new LoadGeneratorModel with the specified simulation parameters.
	 * 
	 * <p>This constructor initializes the load generator with the basic simulation
	 * parameters needed for task generation. The actual task list creation is
	 * deferred to the {@link #initializeModel()} method which should be called
	 * after construction.</p>
	 * 
	 * @param _numberOfMobileDevices total number of mobile devices in the simulation
	 * @param _simulationTime total simulation duration in seconds
	 * @param _simScenario simulation scenario identifier for configuration lookup
	 */
	public LoadGeneratorModel(int _numberOfMobileDevices, double _simulationTime, String _simScenario){
		numberOfMobileDevices = _numberOfMobileDevices;
		simulationTime = _simulationTime;
		simScenario = _simScenario;
	}
	
	/**
	 * Default constructor that creates an empty LoadGeneratorModel.
	 * 
	 * <p>This constructor is provided for frameworks that require no-argument
	 * constructors. When using this constructor, simulation parameters should
	 * be set separately before calling {@link #initializeModel()}.</p>
	 */
	public LoadGeneratorModel() {
	}

	/**
	 * Returns the complete list of pre-calculated tasks for the simulation.
	 * 
	 * <p>Each task in the list contains a virtual start time that determines when
	 * the task should be submitted during the simulation. The task list is populated
	 * by the {@link #initializeModel()} method and represents the entire workload
	 * pattern for all mobile devices throughout the simulation duration.</p>
	 * 
	 * <p>The returned list includes:
	 * <ul>
	 *   <li>Task submission timestamps</li>
	 *   <li>Task type and device ID assignments</li>
	 *   <li>Task characteristics (input size, output size, computational requirements)</li>
	 *   <li>Statistical variations based on configured distributions</li>
	 * </ul></p>
	 * 
	 * @return immutable list of all tasks scheduled for execution
	 */
	public List<TaskProperty> getTaskList() {
		return taskList;
	}

	/**
	 * Initializes the task generation model and populates the task list.
	 * 
	 * <p>This method must be implemented by concrete load generator classes to create
	 * a realistic task generation pattern. The implementation should use simulation
	 * configuration parameters to determine task characteristics and timing.</p>
	 * 
	 * <p><b>Implementation Responsibilities:</b>
	 * <ul>
	 *   <li>Create and populate the {@link #taskList} with {@link TaskProperty} objects</li>
	 *   <li>Assign appropriate task types to mobile devices based on usage patterns</li>
	 *   <li>Generate realistic task timing using statistical distributions</li>
	 *   <li>Ensure tasks are distributed across the entire simulation duration</li>
	 *   <li>Apply device-specific behavior patterns (idle/active periods, usage frequency)</li>
	 * </ul></p>
	 * 
	 * <p>The method should use configuration from SimSettings to determine parameters
	 * such as task inter-arrival times, active/idle periods, and task type distributions.</p>
	 */
	public abstract void initializeModel();
	
	/**
	 * Returns the primary task type (application type) assigned to a specific device.
	 * 
	 * <p>In EdgeCloudSim, each mobile device is typically associated with a primary
	 * task type that represents the dominant application or service the device uses.
	 * This method provides a way to query which task type category a specific
	 * device belongs to.</p>
	 * 
	 * <p>Task types are defined in the simulation configuration and typically represent
	 * different application categories such as:
	 * <ul>
	 *   <li>Augmented Reality applications</li>
	 *   <li>Health monitoring services</li>
	 *   <li>Infotainment applications</li>
	 *   <li>Computational offloading tasks</li>
	 * </ul></p>
	 * 
	 * @param deviceId the unique identifier of the mobile device
	 * @return task type index corresponding to the device's primary application type
	 */
	public abstract int getTaskTypeOfDevice(int deviceId);
}
