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
import edu.boun.edgecloudsim.core.SimSettings.APP_TYPES;
import edu.boun.edgecloudsim.utils.EdgeTask;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

public class IdleActiveLoadGenerator extends LoadGeneratorModel{

	public IdleActiveLoadGenerator(int _numberOfMobileDevices, double _simulationTime, String _simScenario) {
		super(_numberOfMobileDevices, _simulationTime, _simScenario);
	}

	@Override
	public void initializeModel() {
		taskList = new ArrayList<EdgeTask>();
		
		//exponential number generator for file input size, file output size and task length
		ExponentialDistribution[][] expRngList = new ExponentialDistribution[SimSettings.APP_TYPES.values().length][3];
		
		//create random number generator for each place
		for(int i=0; i<SimSettings.APP_TYPES.values().length; i++) {
			if(SimSettings.getInstance().getTaskLookUpTable()[i][0] ==0)
				continue;
			
			expRngList[i][0] = new ExponentialDistribution(SimSettings.getInstance().getTaskLookUpTable()[i][5]);
			expRngList[i][1] = new ExponentialDistribution(SimSettings.getInstance().getTaskLookUpTable()[i][6]);
			expRngList[i][2] = new ExponentialDistribution(SimSettings.getInstance().getTaskLookUpTable()[i][7]);
		}
		
		//Each mobile device utilizes an app type (task type)
		for(int i=0; i<numberOfMobileDevices; i++) {
			APP_TYPES randomTaskType = null;
			double taskTypeSelector = SimUtils.getRandomDoubleNumber(0,100);
			double taskTypePercentage = 0;
			for (SimSettings.APP_TYPES taskType : SimSettings.APP_TYPES.values()) {
				taskTypePercentage += SimSettings.getInstance().getTaskLookUpTable()[taskType.ordinal()][0];
				if(taskTypeSelector <= taskTypePercentage){
					randomTaskType = taskType;
					break;
				}
			}
			if(randomTaskType == null){
				SimLogger.printLine("Impossible is occured! no random task type!");
				continue;
			}
			
			double poissonMean = SimSettings.getInstance().getTaskLookUpTable()[randomTaskType.ordinal()][2];
			double activePeriod = SimSettings.getInstance().getTaskLookUpTable()[randomTaskType.ordinal()][3];
			double idlePeriod = SimSettings.getInstance().getTaskLookUpTable()[randomTaskType.ordinal()][4];
			double activePeriodStartTime = SimUtils.getRandomDoubleNumber(10, 10+activePeriod);  //start from 10th seconds
			double virtualTime = activePeriodStartTime; //start from 10th seconds

			ExponentialDistribution rng = new ExponentialDistribution(poissonMean);
			while(virtualTime < simulationTime) {
				double interval = rng.sample();

				if(interval <= 0){
					SimLogger.printLine("Impossible is occured! interval is " + interval + " for device " + i + " time " + virtualTime);
					continue;
				}
				//SimLogger.printLine(virtualTime + " -> " + interval + " for device " + i + " time ");
				virtualTime += interval;
				
				if(virtualTime > activePeriodStartTime + activePeriod){
					activePeriodStartTime = activePeriodStartTime + activePeriod + idlePeriod;
					virtualTime = activePeriodStartTime;
					continue;
				}
				
				taskList.add(new EdgeTask(i,randomTaskType, virtualTime, expRngList));
			}
		}
	}

}
