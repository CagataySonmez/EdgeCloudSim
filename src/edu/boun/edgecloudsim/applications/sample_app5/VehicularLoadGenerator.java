package edu.boun.edgecloudsim.applications.sample_app5;

import java.util.ArrayList;

import org.apache.commons.math3.distribution.ExponentialDistribution;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.task_generator.LoadGeneratorModel;
import edu.boun.edgecloudsim.utils.TaskProperty;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

public class VehicularLoadGenerator extends LoadGeneratorModel{
	int taskTypeOfDevices[];

	public VehicularLoadGenerator(int _numberOfMobileDevices, double _simulationTime, String _simScenario) {
		super(_numberOfMobileDevices, _simulationTime, _simScenario);
	}


	@Override
	public void initializeModel() {
		taskList = new ArrayList<TaskProperty>();

		//Each mobile device utilizes an app type (task type)
		taskTypeOfDevices = new int[numberOfMobileDevices];
		for(int i=0; i<numberOfMobileDevices; i++) {
			int randomTaskType = -1;
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

			double poissonMean = SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][2];
			double activePeriod = SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][3];
			double idlePeriod = SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][4];
			double activePeriodStartTime = SimUtils.getRandomDoubleNumber(
					SimSettings.CLIENT_ACTIVITY_START_TIME, 
					SimSettings.CLIENT_ACTIVITY_START_TIME * 2);  //active period starts shortly after the simulation started (e.g. 10 seconds)
			double virtualTime = activePeriodStartTime;

			ExponentialDistribution rng = new ExponentialDistribution(poissonMean);
			//ExponentialDistribution rng[] = new ExponentialDistribution[10];
			//for(int j=0; j<10; j++)
			//	rng[j] = new ExponentialDistribution(poissonMean * ((double)1 + (double)j * (double) 0.12));

			while(virtualTime < simulationTime) {
				//int index = Math.min(9, (int)virtualTime / 15);
				//double interval = rng[9-index].sample();
				double interval = rng.sample();

				if(interval <= 0){
					SimLogger.printLine("Impossible is occurred! interval is " + interval + " for device " + i + " time " + virtualTime);
					continue;
				}
				//SimLogger.printLine(virtualTime + " -> " + interval + " for device " + i + " time ");
				virtualTime += interval;

				if(virtualTime > activePeriodStartTime + activePeriod){
					activePeriodStartTime = activePeriodStartTime + activePeriod + idlePeriod;
					virtualTime = activePeriodStartTime;
					continue;
				}

				long inputFileSize = (long)SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][5];
				long inputFileSizeBias = inputFileSize / 10;

				long outputFileSize =(long)SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][6];
				long outputFileSizeBias = outputFileSize / 10;

				long length = (long)SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][7];	
				long lengthBias = length / 10;

				int pesNumber = (int)SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][8];

				inputFileSize = SimUtils.getRandomLongNumber(inputFileSize - inputFileSizeBias, inputFileSize + inputFileSizeBias);
				outputFileSize = SimUtils.getRandomLongNumber(outputFileSize - outputFileSizeBias, outputFileSize + outputFileSizeBias);
				length = SimUtils.getRandomLongNumber(length - lengthBias, length + lengthBias);

				taskList.add(new TaskProperty(virtualTime, i, randomTaskType, pesNumber, length, inputFileSize, outputFileSize));
			}
		}
	}

	@Override
	public int getTaskTypeOfDevice(int deviceId) {
		// TODO Auto-generated method stub
		return taskTypeOfDevices[deviceId];
	}

}