package edu.boun.edgecloudsim.edge_client;

import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.UtilizationModel;

import edu.boun.edgecloudsim.utils.TaskProperty;

public abstract class MobileDeviceManager  extends DatacenterBroker {

	public MobileDeviceManager() throws Exception {
		super("Global_Broker");
	}
	
	/*
	 * initialize mobile device manager if needed
	 */
	public abstract void initialize();
	
	/*
	 * provides abstract CPU Utilization Model
	 */
	public abstract UtilizationModel getCpuUtilizationModel();
	
	public abstract void submitTask(TaskProperty edgeTask);
}
