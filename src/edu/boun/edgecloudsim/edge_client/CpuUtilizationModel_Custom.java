/*
 * Title:        EdgeCloudSim - Custom VM Cpu Utilization Model
 * 
 * Description: 
 * CpuUtilizationModel_Custom implements UtilizationModel and used for
 * VM CPU utilization model. In CloudSim, the CPU utilization of the VM
 * is a simple counter. We provide more realistic utilization model
 * which decide CPU utilization of each application by using the
 * values defined in the applications.xml file. For those who wants to
 * add another VM Cpu Utilization Model to EdgeCloudSim should provide
 * another concrete instance of UtilizationModel via ScenarioFactory
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_client;

import org.cloudbus.cloudsim.UtilizationModel;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.utils.SimLogger;

/**
 * Custom CPU utilization model for EdgeCloudSim that provides realistic VM resource utilization.
 * Unlike CloudSim's simple counter-based approach, this model uses application-specific
 * utilization values defined in the applications.xml configuration file.
 */
public class CpuUtilizationModel_Custom implements UtilizationModel {
	private Task task;
	
	/**
	 * Constructor for custom CPU utilization model.
	 */
	public CpuUtilizationModel_Custom(){
	}
	
	/**
	 * Gets the CPU utilization for the associated task at a given time.
	 * Utilization is determined based on task type and target datacenter type
	 * using values from the task lookup table configuration.
	 * 
	 * @param time Simulation time (unused in this implementation)
	 * @return CPU utilization percentage (0.0 to 1.0) for this task
	 */
	@Override
	public double getUtilization(double time) {
		int index = 9;  // Default to edge VM utilization index
		if(task.getAssociatedDatacenterId() == SimSettings.CLOUD_DATACENTER_ID)
			index = 10;  // Cloud VM utilization index
		else if(task.getAssociatedDatacenterId() == SimSettings.MOBILE_DATACENTER_ID)
			index = 11;  // Mobile VM utilization index

		return SimSettings.getInstance().getTaskLookUpTable()[task.getTaskType()][index];
	}
	
	/**
	 * Associates this utilization model with a specific task.
	 * Required for retrieving task-specific utilization parameters.
	 * @param _task The task to associate with this utilization model
	 */
	public void setTask(Task _task){
		task=_task;
	}
	
	/**
	 * Predicts CPU utilization for a given VM type without task execution.
	 * Used by orchestrators for making offloading decisions before task assignment.
	 * 
	 * @param _vmType The type of VM to predict utilization for
	 * @return Predicted CPU utilization percentage (0.0 to 1.0)
	 */
	public double predictUtilization(SimSettings.VM_TYPES _vmType){
		int index = 0;
		if(_vmType == SimSettings.VM_TYPES.EDGE_VM)
			index = 9;   // Edge VM utilization index
		else if(_vmType == SimSettings.VM_TYPES.CLOUD_VM)
			index = 10;  // Cloud VM utilization index
		else if(_vmType == SimSettings.VM_TYPES.MOBILE_VM)
			index = 11;  // Mobile VM utilization index
		else{
			SimLogger.printLine("Unknown VM Type! Terminating simulation...");
			System.exit(1);
		}
		return SimSettings.getInstance().getTaskLookUpTable()[task.getTaskType()][index];
	}
}
