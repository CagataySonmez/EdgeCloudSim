package edu.boun.edgecloudsim.applications.tutorial4;

import java.util.ArrayList;

import org.apache.commons.math3.distribution.ExponentialDistribution;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.task_generator.LoadGeneratorModel;
import edu.boun.edgecloudsim.utils.TaskProperty;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

public class SampleLoadGenerator extends LoadGeneratorModel{
	int taskTypeOfDevices[];

	public SampleLoadGenerator(int _numberOfMobileDevices, double _simulationTime, String _simScenario) {
		super(_numberOfMobileDevices, _simulationTime, _simScenario);
	}

	@Override
	public void initializeModel() {
		// Allocate list for all scheduled tasks (absolute submission times)
		taskList = new ArrayList<TaskProperty>();

		//Each mobile device utilises an app type (task type)
		taskTypeOfDevices = new int[numberOfMobileDevices];
		for(int i=0; i<numberOfMobileDevices; i++) {
			// Choose task type according to cumulative usage percentages
			int randomTaskType = -1;
			double taskTypeSelector = SimUtils.getRandomDoubleNumber(0,100);
			double taskTypePercentage = 0;
			for (int j=0; j<SimSettings.getInstance().getTaskLookUpTable().length; j++) {
				// Cumulative probability walk; assumes percentages sum ~100 (tolerant to rounding).
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

			double poissonMean = SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][2];
			double activePeriod = SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][3];
			double idlePeriod = SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][4];
			double activePeriodStartTime = SimUtils.getRandomDoubleNumber(
					// Random offset reduces phase alignment (thundering herd) at simulation start.
					SimSettings.CLIENT_ACTIVITY_START_TIME, 
					SimSettings.CLIENT_ACTIVITY_START_TIME * 2);  //active period starts shortly after the simulation started (e.g. 10 seconds)
			double virtualTime = activePeriodStartTime;

			ExponentialDistribution rng = new ExponentialDistribution(poissonMean);
			//ExponentialDistribution rng[] = new ExponentialDistribution[10];
			//for(int j=0; j<10; j++)
			//	rng[j] = new ExponentialDistribution(poissonMean * ((double)1 + (double)j * (double) 0.12));

			// Exponential inter-arrival distribution (memoryless arrivals within active window)
			while(virtualTime < simulationTime) {
				// For heavy-tailed variants, replace Exponential with Pareto / Weibull here.
				// Skip zero/negative intervals (rare numeric edge case)
				//int index = Math.min(9, (int)virtualTime / 15);
				//double interval = rng[9-index].sample();
				double interval = rng.sample();

				if(interval <= 0){
					SimLogger.printLine("Impossible is occurred! interval is " + interval + " for device " + i + " time " + virtualTime);
					continue;
				}
				//SimLogger.printLine(virtualTime + " -> " + interval + " for device " + i + " time ");
				virtualTime += interval;

				// Active/Idle model: device alternates between activePeriod and idlePeriod windows
				// Start time randomized in [CLIENT_ACTIVITY_START_TIME, 2*CLIENT_ACTIVITY_START_TIME]
				if(virtualTime > activePeriodStartTime + activePeriod){
					// Switch to next active window; idle gap skipped without arrivals.
					activePeriodStartTime = activePeriodStartTime + activePeriod + idlePeriod;
					virtualTime = activePeriodStartTime;
					continue;
				}

				// Introduce +/-10% variability around nominal input/output/length values
				long inputFileSize = (long)SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][5];
				long inputFileSizeBias = inputFileSize / 10;

				long outputFileSize =(long)SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][6];
				long outputFileSizeBias = outputFileSize / 10;

				long length = (long)SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][7];	
				long lengthBias = length / 10;

				int pesNumber = (int)SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][8];

				// Bias = 10% gives modest variability; tune to model QoS sensitivity or network jitter.
				inputFileSize = SimUtils.getRandomLongNumber(inputFileSize - inputFileSizeBias, inputFileSize + inputFileSizeBias);
				outputFileSize = SimUtils.getRandomLongNumber(outputFileSize - outputFileSizeBias, outputFileSize + outputFileSizeBias);
				length = SimUtils.getRandomLongNumber(length - lengthBias, length + lengthBias);

				// Persist task specification
				taskList.add(new TaskProperty(virtualTime, i, randomTaskType, pesNumber, length, inputFileSize, outputFileSize));
			}
		}
		// Consider sorting taskList by submission time if downstream components assume ordered arrivals.
	}
	@Override
	public int getTaskTypeOfDevice(int deviceId) {
		// Direct lookup of assigned static task type for device
		return taskTypeOfDevices[deviceId];
	}

}