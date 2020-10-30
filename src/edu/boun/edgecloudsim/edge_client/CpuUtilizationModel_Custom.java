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

public class CpuUtilizationModel_Custom implements UtilizationModel {
	private Task task;
	
	public CpuUtilizationModel_Custom(){
	}
	
	/*
	 * (non-Javadoc)
	 * @see cloudsim.power.UtilizationModel#getUtilization(double)
	 */
	@Override
	public double getUtilization(double time) {
		int index = 9;
		if(task.getAssociatedDatacenterId() == SimSettings.CLOUD_DATACENTER_ID)
			index = 10;
		else if(task.getAssociatedDatacenterId() == SimSettings.MOBILE_DATACENTER_ID)
			index = 11;

		return SimSettings.getInstance().getTaskLookUpTable()[task.getTaskType()][index];
	}
	
	public void setTask(Task _task){
		task=_task;
	}
	
	public double predictUtilization(SimSettings.VM_TYPES _vmType){
		int index = 0;
		if(_vmType == SimSettings.VM_TYPES.EDGE_VM)
			index = 9;
		else if(_vmType == SimSettings.VM_TYPES.CLOUD_VM)
			index = 10;
		else if(_vmType == SimSettings.VM_TYPES.MOBILE_VM)
			index = 11;
		else{
			SimLogger.printLine("Unknown VM Type! Terminating simulation...");
			System.exit(1);
		}
		return SimSettings.getInstance().getTaskLookUpTable()[task.getTaskType()][index];
	}
}
