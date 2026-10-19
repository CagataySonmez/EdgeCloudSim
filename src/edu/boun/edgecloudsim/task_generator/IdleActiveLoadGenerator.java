/* SPDX-License-Identifier: GPL-3.0-or-later */
/*
 * Title:        EdgeCloudSim - Idle/Active Load Generator implementation
 *
 * Description:
 * IdleActiveLoadGenerator implements basic load generator model where the
 * mobile devices generate task in active period and waits in idle period.
 * Task interarrival time (load generation period), Idle and active periods
 * are defined in the configuration file.
 *
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.task_generator;

import java.util.ArrayList;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.random.Well19937c;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.utils.TaskProperty;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

/**
 * Generates tasks while each device is active and pauses generation during its
 * configured idle interval. Inter-arrival times, data sizes, and task lengths are
 * sampled from exponential distributions whose means come from the application table.
 */
public class IdleActiveLoadGenerator extends LoadGeneratorModel{
	/** Array storing the assigned task type for each mobile device */
	int taskTypeOfDevices[];

	/**
	 * Constructs a new IdleActiveLoadGenerator with the specified simulation parameters.
	 *
	 * <p>Initializes the load generator for creating idle/active usage patterns.
	 * The actual task generation and device type assignments are performed during
	 * the {@link #initializeModel()} phase.</p>
	 *
	 * @param _numberOfMobileDevices total number of mobile devices to generate tasks for
	 * @param _simulationTime total simulation duration in seconds
	 * @param _simScenario simulation scenario identifier for configuration lookup
	 */
	public IdleActiveLoadGenerator(int _numberOfMobileDevices, double _simulationTime, String _simScenario) {
		super(_numberOfMobileDevices, _simulationTime, _simScenario);
	}

	/**
	 * Assigns one application type to each device and constructs its complete task
	 * schedule through the configured simulation duration.
	 */
	@Override
	public void initializeModel() {
		taskList = new ArrayList<TaskProperty>();

		// Create exponential distribution generators for task characteristics
		// [task_type][0] = input size distribution, [1] = output size, [2] = task length
		ExponentialDistribution[][] expRngList = new ExponentialDistribution[SimSettings.getInstance().getTaskLookUpTable().length][3];

		// Initialize exponential random number generators for each active task type
		for(int i = 0; i < SimSettings.getInstance().getTaskLookUpTable().length; i++) {
			// Skip task types with zero probability (inactive task types)
			if(SimSettings.getInstance().getTaskLookUpTable()[i][0] == 0)
				continue;

			// Create generators for: [0] input size, [1] output size, [2] task computational length
			expRngList[i][0] = createExponentialDistribution(SimSettings.getInstance().getTaskLookUpTable()[i][5]);
			expRngList[i][1] = createExponentialDistribution(SimSettings.getInstance().getTaskLookUpTable()[i][6]);
			expRngList[i][2] = createExponentialDistribution(SimSettings.getInstance().getTaskLookUpTable()[i][7]);
		}

		// Phase 2: Assign each mobile device a primary application/task type
		taskTypeOfDevices = new int[numberOfMobileDevices];
		for(int i = 0; i < numberOfMobileDevices; i++) {
			int randomTaskType = -1;

			// Use weighted random selection based on task type probabilities
			double taskTypeSelector = SimUtils.getRandomDoubleNumber(0, 100);
			double taskTypePercentage = 0;

			// Find the task type by cumulative probability distribution
			for (int j = 0; j < SimSettings.getInstance().getTaskLookUpTable().length; j++) {
				taskTypePercentage += SimSettings.getInstance().getTaskLookUpTable()[j][0];
				if(taskTypeSelector < taskTypePercentage){
					randomTaskType = j;
					break;
				}
			}

			// Validation: ensure a valid task type was selected
			if(randomTaskType == -1){
				throw new IllegalArgumentException("Application usage percentages must sum to 100");
			}

			taskTypeOfDevices[i] = randomTaskType;

			// Extract task type specific parameters from lookup table
			double poissonMean = SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][2];  // Mean inter-arrival time
			double activePeriod = SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][3]; // Active period duration
			double idlePeriod = SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][4];   // Idle period duration

			// Randomize active period start time to avoid device synchronization
			// Start sometime between CLIENT_ACTIVITY_START_TIME and CLIENT_ACTIVITY_START_TIME + activePeriod
			double activePeriodStartTime = SimUtils.getRandomDoubleNumber(
					SimSettings.CLIENT_ACTIVITY_START_TIME,
					SimSettings.CLIENT_ACTIVITY_START_TIME + activePeriod);
			double virtualTime = activePeriodStartTime;

			// Create Poisson process generator for task inter-arrival times
			ExponentialDistribution rng = createExponentialDistribution(poissonMean);

			// Generate tasks for this device throughout the simulation duration
			while(virtualTime < simulationTime) {
				// Sample next task arrival interval from exponential distribution
				double interval = rng.sample();

				// Validate interval (should always be positive for exponential distribution)
				if(interval <= 0){
					SimLogger.printLine("Warning: Invalid interval " + interval + " for device " + i + " at time " + virtualTime);
					continue;
				}

				// Advance virtual time by the inter-arrival interval
				virtualTime += interval;

				// Check if we've exceeded the current active period
				if(virtualTime > activePeriodStartTime + activePeriod){
					// Start new active period after idle period
					activePeriodStartTime = activePeriodStartTime + activePeriod + idlePeriod;
					virtualTime = activePeriodStartTime;
					continue;  // Skip task creation, jump to next active period
				}

				// Create task within active period
				taskList.add(new TaskProperty(i, randomTaskType, virtualTime, expRngList));
			}
		}
	}

	private ExponentialDistribution createExponentialDistribution(double mean) {
		return new ExponentialDistribution(new Well19937c(SimUtils.nextSeed()), mean);
	}

	/**
	 * Returns the assigned task type for the specified mobile device.
	 *
	 * <p>Each device is assigned a primary task type during initialization based on
	 * the probability distribution defined in the simulation configuration. This method
	 * provides access to that assignment for other simulation components.</p>
	 *
	 * <p>The task type determines the device's application behavior including:
	 * <ul>
	 *   <li>Task generation frequency (Poisson mean)</li>
	 *   <li>Active and idle period durations</li>
	 *   <li>Task computational requirements</li>
	 *   <li>Input and output data size characteristics</li>
	 * </ul>
	 *
	 * @param deviceId the unique identifier of the mobile device
	 * @return the task type index assigned to this device during initialization
	 */
	@Override
	public int getTaskTypeOfDevice(int deviceId) {
		return taskTypeOfDevices[deviceId];
	}

}
