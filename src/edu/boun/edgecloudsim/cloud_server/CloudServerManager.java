/*
 * Title:        EdgeCloudSim - CloudServerManager
 * 
 * Description: 
 * CloudServerManager is responsible for creating and terminating
 * the cloud datacenters which operates the hosts and VMs.
 * It also provides the list of VMs running on the hosts and
 * the average utilization of all VMs.
 *
 * Please note that, EdgeCloudSim is built on top of CloudSim
 * Therefore, all the computational units are handled by CloudSim
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

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
	public abstract void createVmList(int brokerId);
	
	/*
	 * returns average utilization of all VMs
	 */
	public abstract double getAvgUtilization();
}