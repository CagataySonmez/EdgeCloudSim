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

import edu.boun.edgecloudsim.core.SimManager;
import org.apache.commons.math3.distribution.ExponentialDistribution;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.utils.TaskProperty;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;
import org.cloudbus.cloudsim.core.CloudSim;

public class IdleActiveLoadGenerator extends LoadGeneratorModel{
	int taskTypeOfDevices[];
	ExponentialDistribution[][] expRngList;
	ExponentialDistribution[] taskRng;
	double[] activePeriods;
	double[] idlePeriods;

	public IdleActiveLoadGenerator(int _numberOfMobileDevices, double _simulationTime, String _simScenario) {
		super(_numberOfMobileDevices, _simulationTime, _simScenario);
	}

	/**
	 * initializeModel sets the necessary attributes and starts the task creation process
	 */

	@Override
	public void initializeModel() {
		taskRng = new ExponentialDistribution[numberOfMobileDevices];

		//exponential number generator for file input size, file output size and task length
		expRngList = new ExponentialDistribution[SimSettings.getInstance().getTaskLookUpTable().length][3];

		//create random number generator for each place
		for (int i = 0; i < SimSettings.getInstance().getTaskLookUpTable().length; i++) {
			if (SimSettings.getInstance().getTaskLookUpTable()[i][0] == 0)
				continue;

			expRngList[i][0] = new ExponentialDistribution(SimSettings.getInstance().getTaskLookUpTable()[i][5]);
			expRngList[i][1] = new ExponentialDistribution(SimSettings.getInstance().getTaskLookUpTable()[i][6]);
			expRngList[i][2] = new ExponentialDistribution(SimSettings.getInstance().getTaskLookUpTable()[i][7]);
		}

		//Each mobile device utilizes an app type (task type)
		taskTypeOfDevices = new int[numberOfMobileDevices];
		activePeriods = new double[numberOfMobileDevices];
		idlePeriods = new double[numberOfMobileDevices];

		for (int i = 0; i < numberOfMobileDevices; i++) {
			int randomTaskType = -1;
			double taskTypeSelector = SimUtils.getRandomDoubleNumber(0, 100);
			double taskTypePercentage = 0;
			for (int j = 0; j < SimSettings.getInstance().getTaskLookUpTable().length; j++) {
				taskTypePercentage += SimSettings.getInstance().getTaskLookUpTable()[j][0];
				if (taskTypeSelector <= taskTypePercentage) {
					randomTaskType = j;
					break;
				}
			}
			if (randomTaskType == -1) {
				SimLogger.printLine("Impossible is occured! no random task type!");
				continue;
			}


			taskTypeOfDevices[i] = randomTaskType;
			double poissonMean = SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][2];
			taskRng[i] = new ExponentialDistribution(poissonMean);
			SimManager sm = SimManager.getInstance();
			activePeriods[i] = SimSettings.getInstance().getTaskLookUpTable()[taskTypeOfDevices[i]][3];
			idlePeriods[i] = SimSettings.getInstance().getTaskLookUpTable()[taskTypeOfDevices[i]][4];
			double activePeriodStartTime = SimUtils.getRandomDoubleNumber(
					0,
					activePeriods[i]);
			sm.schedule(sm.getId(), SimSettings.CLIENT_ACTIVITY_START_TIME + activePeriodStartTime, sm.getGenTasks(), i);
		}
	}


	/**
	 * createTasks schedules the creation of tasks for the given device for a single active-idle period
	 * @param deviceId the id of the device, for which tasks shall be created
	 */
	@Override
	public void createTask(int deviceId){
		SimManager sm = SimManager.getInstance();
		double virtualTime = taskRng[deviceId].sample();

		while(virtualTime < activePeriods[deviceId]) {
			sm.schedule(sm.getId(), virtualTime, sm.getCreateTask(), new TaskProperty(deviceId,taskTypeOfDevices[deviceId], 0, expRngList));

			double interval = taskRng[deviceId].sample();
			if(interval <= 0){
				SimLogger.printLine("Impossible is occured! interval is " + interval + " for device " + deviceId + " time " + virtualTime);
				continue;
			}
			//SimLogger.printLine(virtualTime + " -> " + interval + " for device " + i + " time ");
			virtualTime += interval;
		}
		sm.schedule(sm.getId(), activePeriods[deviceId] + idlePeriods[deviceId], sm.getGenTasks(), deviceId);

	}

	@Override
	public int getTaskTypeOfDevice(int deviceId) {
		// TODO Auto-generated method stub
		return taskTypeOfDevices[deviceId];
	}

}
