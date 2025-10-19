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

/**
 * Abstract base class for managing mobile device processing units in EdgeCloudSim.
 * Handles mobile datacenters that simulate local processing capabilities on mobile devices.
 * Uses CloudSim infrastructure to model mobile devices as lightweight servers.
 */
public abstract class MobileServerManager {
	protected Datacenter localDatacenter;
	protected List<List<MobileVM>> vmList;

	/**
	 * Constructor initializes the VM list structure for mobile servers.
	 */
	public MobileServerManager() {
		vmList = new ArrayList<List<MobileVM>>();
	}

	/**
	 * Retrieves the list of VMs running on a specific mobile host.
	 * Includes bounds checking to prevent index out of bounds errors.
	 * 
	 * @param hostId The ID of the mobile host to query
	 * @return List of MobileVMs running on the specified host, null if hostId is invalid
	 */
	public List<MobileVM> getVmList(int hostId){
		if(vmList.size() > hostId)
			return vmList.get(hostId);
		else
			return null;
	}
	
	/**
	 * Gets the mobile datacenter managed by this manager.
	 * @return The mobile Datacenter instance representing local processing capability
	 */
	public Datacenter getDatacenter(){
		return localDatacenter;
	}
	
	/**
	 * Initializes the mobile server manager with required configurations.
	 * Called before mobile datacenter operations begin.
	 */
	public abstract void initialize();

	/**
	 * Provides VM allocation policy for mobile datacenters.
	 * Determines how VMs are assigned to mobile device hosts.
	 * 
	 * @param list List of available mobile hosts in the datacenter
	 * @param dataCenterIndex Index identifier of the mobile datacenter
	 * @return VmAllocationPolicy instance for mobile VM placement decisions
	 */
	public abstract VmAllocationPolicy getVmAllocationPolicy(List<? extends Host> list, int dataCenterIndex);

	/**
	 * Starts all mobile datacenters and initializes their resources.
	 * Creates local processing capabilities on mobile devices.
	 * @throws Exception if mobile datacenter initialization fails
	 */
	public abstract void startDatacenters() throws Exception;
	
	/**
	 * Terminates all mobile datacenters and releases their resources.
	 * Called during simulation cleanup.
	 */
	public abstract void terminateDatacenters();
	
	/**
	 * Creates the list of VMs for all mobile device hosts.
	 * @param brokerId The broker ID that will manage these mobile VMs
	 */
	public abstract void createVmList(int brokerId);
	
	/**
	 * Calculates and returns the average CPU utilization across all mobile VMs.
	 * @return Average utilization percentage (0.0 to 1.0) of mobile processing units
	 */
	public abstract double getAvgUtilization();
}