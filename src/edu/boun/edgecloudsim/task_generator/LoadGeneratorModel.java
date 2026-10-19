/* SPDX-License-Identifier: GPL-3.0-or-later */
/*
 * Title:        EdgeCloudSim - Load Generator Model
 *
 * Description:
 * Defines the task list and device-to-application mapping produced by a
 * workload generator. Custom models extend this class and are supplied by
 * ScenarioFactory.
 *
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.task_generator;

import java.util.List;

import edu.boun.edgecloudsim.utils.TaskProperty;

/**
 * Base class for models that construct the simulation workload.
 * Implementations populate {@link #taskList} in {@link #initializeModel()} and
 * define the application type assigned to each device.
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
	 * </ul>
	 *
	 * <p>The returned list is the model's mutable backing list. Mutating it changes
	 * the workload consumed by the simulation.</p>
	 *
	 * @return tasks scheduled for execution, in the order stored by the model
	 */
	public List<TaskProperty> getTaskList() {
		return taskList;
	}

	/**
	 * Initializes the model and populates {@link #taskList}.
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
	 * </ul>
	 *
	 * @param deviceId the unique identifier of the mobile device
	 * @return task type index corresponding to the device's primary application type
	 */
	public abstract int getTaskTypeOfDevice(int deviceId);
}
