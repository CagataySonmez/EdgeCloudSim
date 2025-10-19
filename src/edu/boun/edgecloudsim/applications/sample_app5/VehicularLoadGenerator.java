package edu.boun.edgecloudsim.applications.sample_app5;

import java.util.ArrayList;

import org.apache.commons.math3.distribution.ExponentialDistribution;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.task_generator.LoadGeneratorModel;
import edu.boun.edgecloudsim.utils.TaskProperty;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

public class VehicularLoadGenerator extends LoadGeneratorModel{
	// Array storing the assigned task type for each mobile device
	int taskTypeOfDevices[];

	/**
	 * Constructor for vehicular load generator.
	 * 
	 * @param _numberOfMobileDevices total number of mobile devices (vehicles)
	 * @param _simulationTime total simulation duration in seconds
	 * @param _simScenario simulation scenario identifier
	 */
	public VehicularLoadGenerator(int _numberOfMobileDevices, double _simulationTime, String _simScenario) {
		super(_numberOfMobileDevices, _simulationTime, _simScenario);
	}


	/**
	 * Initializes the load generation model by creating task schedules for all vehicles.
	 * Each vehicle is assigned a random task type and generates tasks using Poisson distribution.
	 */
	@Override
	public void initializeModel() {
		taskList = new ArrayList<TaskProperty>();

		// Assign each mobile device (vehicle) a specific application type (task type)
		taskTypeOfDevices = new int[numberOfMobileDevices];
		for(int i=0; i<numberOfMobileDevices; i++) {
			int randomTaskType = -1;
			
			// Select task type based on probability distribution from lookup table
			double taskTypeSelector = SimUtils.getRandomDoubleNumber(0,100);
			double taskTypePercentage = 0;
			for (int j=0; j<SimSettings.getInstance().getTaskLookUpTable().length; j++) {
				taskTypePercentage += SimSettings.getInstance().getTaskLookUpTable()[j][0];
				if(taskTypeSelector <= taskTypePercentage){
					randomTaskType = j;
					break;
				}
			}
			if(randomTaskType == -1){
				SimLogger.printLine("Impossible is occurred! no random task type!");
				continue;
			}

			taskTypeOfDevices[i] = randomTaskType;

			// Get task generation parameters from lookup table
			double poissonMean = SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][2];
			double activePeriod = SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][3];
			double idlePeriod = SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][4];
			
			// Start activity at a random time within the initial window
			double activePeriodStartTime = SimUtils.getRandomDoubleNumber(
					SimSettings.CLIENT_ACTIVITY_START_TIME, 
					SimSettings.CLIENT_ACTIVITY_START_TIME * 2);
			double virtualTime = activePeriodStartTime;

			// Create exponential distribution for Poisson process (inter-arrival times)
			ExponentialDistribution rng = new ExponentialDistribution(poissonMean);

			// Generate tasks for this device throughout the simulation
			while(virtualTime < simulationTime) {
				// Sample next task inter-arrival time from exponential distribution
				double interval = rng.sample();

				if(interval <= 0){
					SimLogger.printLine("Impossible is occurred! interval is " + interval + " for device " + i + " time " + virtualTime);
					continue;
				}
				
				virtualTime += interval;

				// Check if we've exceeded the current active period
				if(virtualTime > activePeriodStartTime + activePeriod){
					// Move to next active period (skip idle period)
					activePeriodStartTime = activePeriodStartTime + activePeriod + idlePeriod;
					virtualTime = activePeriodStartTime;
					continue;
				}

				// Get base task parameters from lookup table
				long inputFileSize = (long)SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][5];
				long inputFileSizeBias = inputFileSize / 10;

				long outputFileSize =(long)SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][6];
				long outputFileSizeBias = outputFileSize / 10;

				long length = (long)SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][7];	
				long lengthBias = length / 10;

				int pesNumber = (int)SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][8];

				// Add random variation to task parameters (±10% of base value)
				inputFileSize = SimUtils.getRandomLongNumber(inputFileSize - inputFileSizeBias, inputFileSize + inputFileSizeBias);
				outputFileSize = SimUtils.getRandomLongNumber(outputFileSize - outputFileSizeBias, outputFileSize + outputFileSizeBias);
				length = SimUtils.getRandomLongNumber(length - lengthBias, length + lengthBias);

				// Create and add the task to the global task list
				taskList.add(new TaskProperty(virtualTime, i, randomTaskType, pesNumber, length, inputFileSize, outputFileSize));
			}
		}
	}

	/**
	 * Returns the assigned task type for a specific device.
	 * 
	 * @param deviceId the device identifier
	 * @return task type assigned to the device
	 */
	@Override
	public int getTaskTypeOfDevice(int deviceId) {
		return taskTypeOfDevices[deviceId];
	}

}