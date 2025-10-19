/*
 * Title:        EdgeCloudSim - EdgeServerManager
 * 
 * Description: 
 * EdgeServerManager is responsible for creating and terminating
 * the edge datacenters which operates the hosts and VMs.
 * It also provides the list of VMs running on the hosts and
 * the average utilization of all VMs.
 *
 * Please note that, EdgeCloudSim is built on top of CloudSim
 * Therefore, all the computational units are handled by CloudSim
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_server;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.VmAllocationPolicy;

/**
 * Abstract base class for managing edge server infrastructure in EdgeCloudSim.
 * 
 * This class provides the interface for creating, managing, and terminating
 * edge datacenters that host the computational resources (VMs and hosts) at
 * the network edge. It integrates with CloudSim's datacenter infrastructure
 * while providing edge-specific functionality for distributed computing scenarios.
 * 
 * Key responsibilities:
 * - Datacenter lifecycle management (creation, startup, termination)
 * - VM allocation policy definition for edge-specific requirements
 * - VM list management and host-to-VM mapping
 * - Resource utilization monitoring across edge infrastructure
 * 
 * Concrete implementations should extend this class and provide scenario-specific
 * datacenter configurations, VM allocation strategies, and monitoring logic.
 */
public abstract class EdgeServerManager {
	protected List<Datacenter> localDatacenters;    // Edge datacenters managed by this instance
	protected List<List<EdgeVM>> vmList;             // VM lists organized by host ID

	/**
	 * Constructs an EdgeServerManager with empty datacenter and VM collections.
	 * Initializes the data structures for tracking edge infrastructure components.
	 */
	public EdgeServerManager() {
		localDatacenters=new ArrayList<Datacenter>();
		vmList = new ArrayList<List<EdgeVM>>();
	}

	/**
	 * Retrieves the list of VMs running on a specific edge host.
	 * 
	 * @param hostId The ID of the host whose VMs are requested
	 * @return List of EdgeVMs running on the specified host
	 */
	public List<EdgeVM> getVmList(int hostId){
		return vmList.get(hostId);
	}
	
	/**
	 * Retrieves the list of all edge datacenters managed by this instance.
	 * 
	 * @return List of edge datacenters under management
	 */
	public List<Datacenter> getDatacenterList(){
		return localDatacenters;
	}
	
	/**
	 * Initializes the edge server manager with scenario-specific configurations.
	 * This method should set up any required data structures, load datacenter
	 * specifications, and prepare for edge infrastructure deployment.
	 */
	public abstract void initialize();

	/**
	 * Provides VM allocation policy for edge datacenters.
	 * Defines how VMs should be allocated to hosts within edge datacenters,
	 * considering edge-specific constraints like proximity, resource availability,
	 * and load balancing requirements.
	 * 
	 * @param list List of hosts available in the target datacenter
	 * @param dataCenterIndex Index of the datacenter requiring allocation policy
	 * @return VM allocation policy instance for the specified datacenter
	 */
	public abstract VmAllocationPolicy getVmAllocationPolicy(List<? extends Host> list, int dataCenterIndex);

	/**
	 * Starts all edge datacenters and makes them ready for operation.
	 * Initializes datacenter entities, registers them with CloudSim,
	 * and prepares the edge infrastructure for task execution.
	 * 
	 * @throws Exception if datacenter initialization or registration fails
	 */
	public abstract void startDatacenters() throws Exception;
	
	/**
	 * Terminates all edge datacenters and cleans up resources.
	 * Properly shuts down datacenter entities and releases any
	 * allocated computational resources.
	 */
	public abstract void terminateDatacenters();
	
	/**
	 * Creates and initializes the VM list for all edge hosts.
	 * Sets up VMs according to simulation configuration and assigns
	 * them to appropriate hosts within the edge infrastructure.
	 * 
	 * @param brokerId ID of the broker that will manage the created VMs
	 */
	public abstract void createVmList(int brokerId);
	
	/**
	 * Calculates the average CPU utilization across all edge VMs.
	 * Provides a system-wide view of resource usage for monitoring
	 * and load balancing purposes.
	 * 
	 * @return Average utilization percentage (0-100) across all edge VMs
	 */
	public abstract double getAvgUtilization();
}