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

package edu.boun.edgecloudsim.applications.scenario4;

import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.Vm;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.edge_client.Task;

public class SampleCpuUtilizationModel implements UtilizationModel {
	private Task task;
	
	public SampleCpuUtilizationModel(){
	}
	
	/*
	 * (non-Javadoc)
	 * @see cloudsim.power.UtilizationModel#getUtilization(double)
	 */
	@Override
	public double getUtilization(double time) {
		Vm vm = SimManager.getInstance().getMobileDeviceManager().getVmList().get(task.getAssociatedVmId());
		
		return predictUtilization(vm);
	}
	
	public void setTask(Task _task){
		task=_task;
	}
	
	public double predictUtilization(Vm vm){
		/**
		 * This scenario is configured as the CPU utilization is 100 * taskSize / edgeCapacity
		 * For example,
		 *   if task size is 4000, and edge server VM MIPS value is 20000
		 *   CPU utilization for this task is 20%
		 */
		return (double)100 * (task.getCloudletLength() / vm.getMips());
	}
}
