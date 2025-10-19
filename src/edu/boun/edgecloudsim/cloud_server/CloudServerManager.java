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

/**
 * Abstract base class for managing cloud server infrastructure in EdgeCloudSim.
 * Handles cloud datacenter operations, VM allocation, and resource management.
 */
public abstract class CloudServerManager {
	protected Datacenter localDatacenter;
	protected List<List<CloudVM>> vmList;

	/**
	 * Constructor initializes the VM list structure for cloud servers.
	 */
	public CloudServerManager() {
		vmList = new ArrayList<List<CloudVM>>();
	}

	/**
	 * Retrieves the list of VMs running on a specific host.
	 * @param hostId The ID of the host to query
	 * @return List of CloudVMs running on the specified host
	 */
	public List<CloudVM> getVmList(int hostId){
		return vmList.get(hostId);
	}
	
	/**
	 * Gets the cloud datacenter managed by this manager.
	 * @return The cloud Datacenter instance
	 */
	public Datacenter getDatacenter(){
		return localDatacenter;
	}
	
	/**
	 * Initializes the cloud server manager with required configurations.
	 * Called before datacenter operations begin.
	 */
	public abstract void initialize();

	/**
	 * Provides VM allocation policy for cloud datacenters.
	 * Determines how VMs are assigned to physical hosts.
	 * 
	 * @param list List of available hosts in the datacenter
	 * @param dataCenterIndex Index identifier of the datacenter
	 * @return VmAllocationPolicy instance for VM placement decisions
	 */
	public abstract VmAllocationPolicy getVmAllocationPolicy(List<? extends Host> list, int dataCenterIndex);

	/**
	 * Starts all cloud datacenters and initializes their resources.
	 * @throws Exception if datacenter initialization fails
	 */
	public abstract void startDatacenters() throws Exception;
	
	/**
	 * Terminates all cloud datacenters and releases their resources.
	 * Called during simulation cleanup.
	 */
	public abstract void terminateDatacenters();
	
	/**
	 * Creates the list of VMs for all cloud hosts.
	 * @param brokerId The broker ID that will manage these VMs
	 */
	public abstract void createVmList(int brokerId);
	
	/**
	 * Calculates and returns the average CPU utilization across all cloud VMs.
	 * @return Average utilization percentage (0.0 to 1.0)
	 */
	public abstract double getAvgUtilization();
}