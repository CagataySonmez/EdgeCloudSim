package edu.boun.edgecloudsim.cloud_server;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.VmAllocationPolicy;

public abstract class CloudServerManager {
	protected Datacenter localDatacenter;
	protected List<List<CloudVM>> vmList;

	public CloudServerManager() {
		vmList = new ArrayList<List<CloudVM>>();
	}

	public List<CloudVM> getVmList(int hostId){
		return vmList.get(hostId);
	}
	
	public Datacenter getDatacenter(){
		return localDatacenter;
	}
	
	/*
	 * initialize edge server manager if needed
	 */
	public abstract void initialize();

	/*
	 * provides abstract Vm Allocation Policy for Cloud Datacenters
	 */
	public abstract VmAllocationPolicy getVmAllocationPolicy(List<? extends Host> list, int dataCenterIndex);

	/*
	 * Starts Datacenters
	 */
	public abstract void startDatacenters() throws Exception;
	
	/*
	 * Terminates Datacenters
	 */
	public abstract void terminateDatacenters();
	/*
	 * Creates VM List
	 */
	public abstract void createVmList(int brockerId);
	
	/*
	 * returns average utilization of all VMs
	 */
	public abstract double getAvgUtilization();
}