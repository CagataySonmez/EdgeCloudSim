/* SPDX-License-Identifier: GPL-3.0-or-later */
/*
 * Title:        EdgeCloudSim - EdgeTask
 *
 * Description:
 * A custom class used in Load Generator Model to store tasks information
 *
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.utils;

import org.apache.commons.math3.distribution.ExponentialDistribution;

import edu.boun.edgecloudsim.core.SimSettings;

/**
 * Immutable-by-convention input record used to create a simulation task. It stores
 * the submission time, origin, application type, processing-element count,
 * instruction length, and input/output data sizes.
 */
public class TaskProperty {
	/** Simulation time when this task should be submitted (in seconds) */
	private double startTime;

	/** Computational length of the task (in million instructions) */
	private long length;

	/** Input data size in kilobytes. */
	private long inputFileSize;

	/** Output data size in kilobytes. */
	private long outputFileSize;

	/** Task type index defining application category and behavior */
	private int taskType;

	/** Number of processing elements (CPU cores) required for execution */
	private int pesNumber;

	/** ID of the mobile device that will generate this task */
	private int mobileDeviceId;

	/**
	 * Constructs a TaskProperty with explicitly specified parameters.
	 *
	 * <p>This constructor allows direct specification of all task characteristics,
	 * providing complete control over task properties. Typically used when task
	 * parameters are pre-calculated or derived from specific algorithms rather
	 * than statistical distributions.</p>
	 *
	 * @param _startTime simulation time when the task should be submitted (seconds)
	 * @param _mobileDeviceId ID of the mobile device generating this task
	 * @param _taskType task type index for application category classification
	 * @param _pesNumber number of processing elements (CPU cores) required
	 * @param _length computational requirement in million instructions
	 * @param _inputFileSize input data size in kilobytes
	 * @param _outputFileSize output data size in kilobytes
	 */
	public TaskProperty(double _startTime, int _mobileDeviceId, int _taskType, int _pesNumber, long _length, long _inputFileSize, long _outputFileSize) {
		startTime = _startTime;
		mobileDeviceId = _mobileDeviceId;
		taskType = _taskType;
		pesNumber = _pesNumber;
		length = _length;
		inputFileSize = _inputFileSize;
		outputFileSize = _outputFileSize;
	}

	/**
	 * Constructs a task property by sampling its data sizes and instruction length.
	 *
	 * <p><b>Distribution Sampling:</b>
	 * <ul>
	 *   <li>expRngList[taskType][0]: Input file size distribution</li>
	 *   <li>expRngList[taskType][1]: Output file size distribution</li>
	 *   <li>expRngList[taskType][2]: Computational length distribution</li>
	 * </ul>
	 *
	 * <p>The number of required processing elements is retrieved from the task lookup
	 * table configuration, providing consistent resource requirements for each task type.</p>
	 *
	 * @param _mobileDeviceId ID of the mobile device generating this task
	 * @param _taskType task type index for distribution and configuration lookup
	 * @param _startTime simulation time when the task should be submitted (seconds)
	 * @param expRngList 2D array of exponential distributions [taskType][characteristic]
	 */
	public TaskProperty(int _mobileDeviceId, int _taskType, double _startTime, ExponentialDistribution[][] expRngList) {
		mobileDeviceId = _mobileDeviceId;
		startTime = _startTime;
		taskType = _taskType;

		// Sample task characteristics from exponential distributions
		inputFileSize = (long)expRngList[_taskType][0].sample();
		outputFileSize = (long)expRngList[_taskType][1].sample();
		length = (long)expRngList[_taskType][2].sample();

		// Retrieve processing element requirement from configuration
		pesNumber = (int)SimSettings.getInstance().getTaskLookUpTable()[_taskType][8];
	}

	/**
	 * Constructs a TaskProperty for single task type scenarios with statistical distributions.
	 *
	 * <p>This simplified constructor is used when all tasks belong to the same type (taskType = 0),
	 * typically in scenarios with homogeneous workloads. It uses a 1D array of exponential
	 * distributions to generate task characteristics while maintaining statistical realism.</p>
	 *
	 * <p><b>Distribution Array Structure:</b>
	 * <ul>
	 *   <li>expRngList[0]: Input file size distribution</li>
	 *   <li>expRngList[1]: Output file size distribution</li>
	 *   <li>expRngList[2]: Computational length distribution</li>
	 * </ul>
	 *
	 * @param mobileDeviceId ID of the mobile device generating this task
	 * @param startTime simulation time when the task should be submitted (seconds)
	 * @param expRngList array of exponential distributions for task characteristics
	 */
	public TaskProperty(int mobileDeviceId, double startTime, ExponentialDistribution[] expRngList) {
		this.mobileDeviceId = mobileDeviceId;
		this.startTime = startTime;

		// Default to task type 0 for homogeneous workload scenarios
		taskType = 0;

		// Sample task characteristics from provided distributions
		inputFileSize = (long)expRngList[0].sample();
		outputFileSize = (long)expRngList[1].sample();
		length = (long)expRngList[2].sample();

		// Retrieve processing element requirement for task type 0
		pesNumber = (int)SimSettings.getInstance().getTaskLookUpTable()[0][8];
	}

	/**
	 * Returns the simulation time when this task should be submitted.
	 *
	 * <p>The start time determines when the mobile device will generate and submit
	 * this task for processing. Load generators use this value to preserve workload
	 * patterns and enables proper sequencing of simulation events.</p>
	 *
	 * @return task submission time in simulation seconds
	 */
	public double getStartTime(){
		return startTime;
	}

	/**
	 * Returns the computational length of this task.
	 *
	 * <p>Task length represents the computational requirement in million instructions (MI).
	 * This value is used by processing nodes to estimate execution time based on
	 * their processing capacity and current load conditions.</p>
	 *
	 * @return computational requirement in million instructions
	 */
	public long getLength(){
		return length;
	}

	/**
	 * Returns the size of input data that needs to be uploaded.
	 *
	 * <p>Input file size determines the amount of data that must be transmitted
	 * from the mobile device to the processing node before task execution can begin.
	 * This affects network upload delay calculations and bandwidth utilization.</p>
	 *
	 * @return input data size in kilobytes
	 */
	public long getInputFileSize(){
		return inputFileSize;
	}

	/**
	 * Returns the size of output data that will be downloaded after processing.
	 *
	 * <p>Output file size represents the result data that must be transmitted back
	 * from the processing node to the mobile device after task completion.
	 * This affects network download delay calculations and response time.</p>
	 *
	 * @return output data size in kilobytes
	 */
	public long getOutputFileSize(){
		return outputFileSize;
	}

	/**
	 * Returns the task type index for application category classification.
	 *
	 * <p>Task type defines the application category and determines behavioral
	 * characteristics such as processing requirements, data patterns, and
	 * quality of service expectations. Different task types may have different
	 * scheduling priorities and resource allocation strategies.</p>
	 *
	 * @return task type index as defined in simulation configuration
	 */
	public int getTaskType(){
		return taskType;
	}

	/**
	 * Returns the number of processing elements (CPU cores) required.
	 *
	 * <p>PES (Processing Element) number indicates how many CPU cores this task
	 * needs for execution. This information is used by VM allocation policies
	 * and task schedulers to ensure adequate computational resources are available.</p>
	 *
	 * @return required number of CPU cores for task execution
	 */
	public int getPesNumber(){
		return pesNumber;
	}

	/**
	 * Returns the ID of the mobile device that will generate this task.
	 *
	 * <p>Mobile device ID provides traceability from task back to its originating
	 * device, enabling device-specific analytics, location-based processing
	 * decisions, and personalized service optimization.</p>
	 *
	 * @return unique identifier of the originating mobile device
	 */
	public int getMobileDeviceId(){
		return mobileDeviceId;
	}
}
