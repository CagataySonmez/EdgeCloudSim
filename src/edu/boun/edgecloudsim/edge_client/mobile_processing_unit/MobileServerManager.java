/*
 * Title:        EdgeCloudSim - MobileServerManager
 * 
 * Description: 
 * MobileServerManager is responsible for creating and terminating
 * the mobile datacenters which operates the hosts and VMs.
 * It also provides the list of VMs running on the hosts and
 * the average utilization of all VMs.
 *
 * Please note that, EdgeCloudSim is built on top of CloudSim
 * Therefore, all the computational units are handled by CloudSim
 *
 * The mobile processing units are simulated via CloudSim as well.
 * It is assumed that the mobile devices operate Hosts and VMs
 * like a server. That is why the class names are similar to other
 * Cloud and Edge components.
 *
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_client.mobile_processing_unit;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.VmAllocationPolicy;

public abstract class MobileServerManager {
	protected Datacenter localDatacenter;
	protected List<List<MobileVM>> vmList;

	public MobileServerManager() {
		vmList = new ArrayList<List<MobileVM>>();
	}

	public List<MobileVM> getVmList(int hostId){
		if(vmList.size() > hostId)
			return vmList.get(hostId);
		else
			return null;
	}
	
	public Datacenter getDatacenter(){
		return localDatacenter;
	}
	
	/*
	 * initialize edge server manager if needed
	 */
	public abstract void initialize();

	/*
	 * provides abstract Vm Allocation Policy for Mobile Datacenters
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