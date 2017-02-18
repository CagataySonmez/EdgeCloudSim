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
 * another concreate instance of UtilizationModel via ScenarioFactory
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_client;

import org.cloudbus.cloudsim.UtilizationModel;

import edu.boun.edgecloudsim.core.SimSettings;

public class CpuUtilizationModel_Custom implements UtilizationModel {
	private SimSettings.APP_TYPES taskType;
	
	public CpuUtilizationModel_Custom(SimSettings.APP_TYPES _taskType){
		taskType=_taskType;
	}
	
	/*
	 * (non-Javadoc)
	 * @see cloudsim.power.UtilizationModel#getUtilization(double)
	 */
	@Override
	public double getUtilization(double time) {
		return SimSettings.getInstance().getTaskLookUpTable()[taskType.ordinal()][9];
	}
	
	public double predictUtilization(SimSettings.VM_TYPES _vmType){
		return SimSettings.getInstance().getTaskLookUpTable()[taskType.ordinal()][9];
	}
}
