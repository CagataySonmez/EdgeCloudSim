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

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.utils.TaskProperty;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

/**
 * Idle/Active load generator implementation for realistic mobile device usage patterns.
 * 
 * <p>This load generator implements a behavioral model where mobile devices alternate between
 * active periods (generating tasks) and idle periods (no task generation). This pattern
 * closely mimics real-world mobile device usage where users interact with applications
 * in bursts followed by periods of inactivity.</p>
 * 
 * <p><b>Key Behavioral Characteristics:</b>
 * <ul>
 *   <li><b>Active Periods:</b> Devices generate tasks according to a Poisson process</li>
 *   <li><b>Idle Periods:</b> Devices remain inactive (no task generation)</li>
 *   <li><b>Periodic Cycling:</b> Alternates between active and idle states throughout simulation</li>
 *   <li><b>Exponential Distributions:</b> Task characteristics follow exponential distributions</li>
 * </ul></p>
 * 
 * <p><b>Task Generation Process:</b>
 * <ol>
 *   <li>Assign each device a primary task type based on configured probabilities</li>
 *   <li>Create exponential random number generators for task characteristics</li>
 *   <li>For each device, generate tasks during active periods using Poisson arrivals</li>
 *   <li>Skip task generation during idle periods</li>
 *   <li>Continue until simulation time is exhausted</li>
 * </ol></p>
 * 
 * <p><b>Configuration Parameters (from SimSettings):</b>
 * <ul>
 *   <li>Task type probabilities and characteristics</li>
 *   <li>Poisson mean inter-arrival times</li>
 *   <li>Active and idle period durations</li>
 *   <li>Task input/output sizes and processing requirements</li>
 * </ul></p>
 * 
 * @see LoadGeneratorModel
 * @see edu.boun.edgecloudsim.core.SimSettings
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
	 * Initializes the idle/active load generation model and creates the complete task schedule.
	 * 
	 * <p>This method implements the core idle/active task generation algorithm with the following steps:
	 * <ol>
	 *   <li><b>Setup Phase:</b> Create exponential distribution generators for task characteristics</li>
	 *   <li><b>Device Assignment:</b> Assign each device a primary task type based on probabilities</li>
	 *   <li><b>Task Generation:</b> For each device, generate tasks during active periods only</li>
	 *   <li><b>Timing Control:</b> Use Poisson process for task arrivals within active periods</li>
	 *   <li><b>Period Management:</b> Handle transitions between active and idle states</li>
	 * </ol></p>
	 * 
	 * <p><b>Statistical Modeling:</b>
	 * <ul>
	 *   <li>Task inter-arrival times follow exponential distribution (Poisson process)</li>
	 *   <li>Task characteristics (input size, output size, length) use exponential distributions</li>
	 *   <li>Active period start times are randomized to avoid synchronization</li>
	 *   <li>Device task type assignment uses weighted random selection</li>
	 * </ul></p>
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
			expRngList[i][0] = new ExponentialDistribution(SimSettings.getInstance().getTaskLookUpTable()[i][5]);
			expRngList[i][1] = new ExponentialDistribution(SimSettings.getInstance().getTaskLookUpTable()[i][6]);
			expRngList[i][2] = new ExponentialDistribution(SimSettings.getInstance().getTaskLookUpTable()[i][7]);
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
				if(taskTypeSelector <= taskTypePercentage){
					randomTaskType = j;
					break;
				}
			}
			
			// Validation: ensure a valid task type was selected
			if(randomTaskType == -1){
				SimLogger.printLine("Critical Error: No valid task type assigned to device " + i + "!");
				continue;
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
			ExponentialDistribution rng = new ExponentialDistribution(poissonMean);
			
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
	 * </ul></p>
	 * 
	 * @param deviceId the unique identifier of the mobile device
	 * @return the task type index assigned to this device during initialization
	 */
	@Override
	public int getTaskTypeOfDevice(int deviceId) {
		return taskTypeOfDevices[deviceId];
	}

}
