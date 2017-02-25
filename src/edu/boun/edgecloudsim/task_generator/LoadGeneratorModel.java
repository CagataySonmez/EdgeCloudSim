/*
 * Title:        EdgeCloudSim - Load Generator Model
 * 
 * Description: 
 * LoadGeneratorModel is an abstract class which is used for 
 * deciding task generation pattern via a task list. For those who
 * wants to add a custom Load Generator Model to EdgeCloudSim should
 * extend this class and provide a concreate instance via ScenarioFactory
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.task_generator;

import java.util.List;

import edu.boun.edgecloudsim.utils.EdgeTask;

public abstract class LoadGeneratorModel {
	protected List<EdgeTask> taskList;
	protected int numberOfMobileDevices;
	protected double simulationTime;
	protected String simScenario;
	
	public LoadGeneratorModel(int _numberOfMobileDevices, double _simulationTime, String _simScenario){
		numberOfMobileDevices=_numberOfMobileDevices;
		simulationTime=_simulationTime;
		simScenario=_simScenario;
	};
	
	/*
	 * each task has a virtual start time
	 * it will be used while generating task
	 */
	public List<EdgeTask> getTaskList() {
		return taskList;
	}

	/*
	 * fill task list according to related task generation model
	 */
	public abstract void initializeModel();
}
