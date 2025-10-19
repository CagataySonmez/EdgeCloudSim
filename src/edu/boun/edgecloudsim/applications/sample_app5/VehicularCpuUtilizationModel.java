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

package edu.boun.edgecloudsim.applications.sample_app5;

import org.cloudbus.cloudsim.UtilizationModel;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.utils.SimLogger;

public class VehicularCpuUtilizationModel implements UtilizationModel {
	// Current task being processed
	private Task task;

	public VehicularCpuUtilizationModel(){
	}

	/**
	 * Returns the CPU utilization for the current task at a given time.
	 * The utilization is determined by task type and datacenter type from lookup table.
	 * 
	 * @param time current simulation time (not used in this implementation)
	 * @return CPU utilization percentage [0.0-1.0]
	 */
	@Override
	public double getUtilization(double time) {
		int datacenterId = task.getAssociatedDatacenterId();
		int index = 0;

		// Map datacenter type to lookup table index
		if(datacenterId == VehicularEdgeOrchestrator.EDGE_DATACENTER)
			index = 9;  // Edge datacenter utilization column
		else if(datacenterId == VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_GSM ||
				datacenterId == VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_RSU)
			index = 10; // Cloud datacenter utilization column

		return SimSettings.getInstance().getTaskLookUpTable()[task.getTaskType()][index];
	}

	/**
	 * Sets the current task for utilization calculation.
	 * 
	 * @param _task the task to be processed
	 */
	public void setTask(Task _task){
		task=_task;
	}

	/**
	 * Predicts CPU utilization for a given VM type without executing the task.
	 * Used for decision making in orchestration policies.
	 * 
	 * @param _vmType the VM type to predict utilization for
	 * @return predicted CPU utilization percentage [0.0-1.0]
	 */
	public double predictUtilization(SimSettings.VM_TYPES _vmType){
		int index = 0;
		
		// Map VM type to lookup table column index
		if(_vmType == SimSettings.VM_TYPES.EDGE_VM)
			index = 9;   // Edge VM utilization column
		else if(_vmType == SimSettings.VM_TYPES.CLOUD_VM)
			index = 10;  // Cloud VM utilization column
		else if(_vmType == SimSettings.VM_TYPES.MOBILE_VM)
			index = 11;  // Mobile VM utilization column
		else{
			SimLogger.printLine("Unknown VM Type! Terminating simulation...");
			System.exit(1);
		}
		return SimSettings.getInstance().getTaskLookUpTable()[task.getTaskType()][index];
	}
}
