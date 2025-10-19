/*
 * Title:        EdgeCloudSim - Custom Vm Allocation Policy for Edge VMs
 * 
 * Description: 
 * VmAllocationPolicy_Custom implements VmAllocationPolicy to decide which.
 * VM is created on which host located on the datacenters. For those
 * who wants to add another Vm Allocation Policy to EdgeCloudSim should
 * provide another concrete instance of VmAllocationPolicy via ScenarioFactory
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.boun.edgecloudsim.core.SimSettings;

/**
 * Custom VM allocation policy for edge datacenters in EdgeCloudSim.
 * 
 * This class implements a configuration-driven VM allocation policy that places
 * VMs on specific hosts based on XML configuration specifications. Unlike simple
 * allocation policies, this implementation ensures VMs are created on designated
 * hosts according to the edge devices configuration file, enabling precise
 * control over VM-to-host mappings in edge computing scenarios.
 * 
 * Key features:
 * - XML configuration-driven VM placement
 * - Datacenter-aware allocation for multi-tier edge deployments  
 * - VM creation tracking and monitoring
 * - Integration with EdgeCloudSim's device specification framework
 * 
 * The policy parses the edge devices XML configuration to determine the exact
 * host placement for each VM based on VM ID and datacenter hierarchy.
 */
public class EdgeVmAllocationPolicy_Custom extends VmAllocationPolicy {
	private Map<String, Host> vmTable;    // Mapping of VM UIDs to their allocated hosts
	private static int createdVmNum;      // Global counter of successfully created VMs
	private int DataCenterIndex;          // Index of the datacenter managed by this policy
	
	/**
	 * Constructs a custom edge VM allocation policy for a specific datacenter.
	 * Initializes VM tracking structures and associates the policy with a
	 * particular datacenter index for configuration-driven allocation.
	 * 
	 * @param list List of hosts available in this datacenter
	 * @param _DataCenterIndex Index of the datacenter this policy manages
	 */
	public EdgeVmAllocationPolicy_Custom(List<? extends Host> list, int _DataCenterIndex) {
		super(list);
		
		setVmTable(new HashMap<String, Host>());
		DataCenterIndex=_DataCenterIndex;
		createdVmNum = 0;
	}

	/**
	 * Allocates a host for the given VM based on XML configuration specifications.
	 * Parses the edge devices configuration to find the designated host for this VM
	 * and attempts to create the VM on that specific host. Only EdgeVMs are processed.
	 * 
	 * @param vm The VM requesting host allocation
	 * @return true if VM was successfully allocated to the designated host, false otherwise
	 */
	@Override
	public boolean allocateHostForVm(Vm vm) {
		boolean result = false;
		
		// Only allocate if VM is not already allocated and is an EdgeVM
		if (!getVmTable().containsKey(vm.getUid()) && vm instanceof EdgeVM) {
			boolean vmFound = false;
			int vmCounter = 0;
			int hostIndex = 0;
			int dataCenterIndex = 0;
			
			// Parse XML configuration to find the designated host for this VM
			Document doc = SimSettings.getInstance().getEdgeDevicesDocument();
			NodeList datacenterList = doc.getElementsByTagName("datacenter");
			for (int i = 0; (!vmFound && i < datacenterList.getLength()); i++) {
				Node datacenterNode = datacenterList.item(i);
				Element datacenterElement = (Element) datacenterNode;
				NodeList hostNodeList = datacenterElement.getElementsByTagName("host");
				for (int j = 0; (!vmFound  && j < hostNodeList.getLength()); j++) {
					Node hostNode = hostNodeList.item(j);
					Element hostElement = (Element) hostNode;
					NodeList vmNodeList = hostElement.getElementsByTagName("VM");
					for (int k = 0; (!vmFound && k < vmNodeList.getLength()); k++) {

						// Match VM ID with configuration to find designated placement
						if(vmCounter == vm.getId()){
							dataCenterIndex = i;
							hostIndex = j;
							vmFound = true;
						}

						vmCounter++;
					}
				}
			}

			// Attempt VM creation if valid placement found and belongs to this datacenter
			if(vmFound && dataCenterIndex == DataCenterIndex && hostIndex < getHostList().size()){
				Host host = getHostList().get(hostIndex);
				result = host.vmCreate(vm);
	
				if (result) { // VM successfully created on designated host
					getVmTable().put(vm.getUid(), host);
					createdVmNum++;
					Log.formatLine("%.2f: Edge VM #" + vm.getId() + " has been allocated to the host #" + host.getId(),CloudSim.clock());
					result = true;
				}
			}
		}
		
		return result;
	}

	/**
	 * Allocates a VM to a specifically designated host.
	 * Attempts to create the VM on the given host and updates tracking structures.
	 * 
	 * @param vm The VM to be allocated
	 * @param host The specific host where the VM should be created
	 * @return true if VM was successfully created on the host, false otherwise
	 */
	@Override
	public boolean allocateHostForVm(Vm vm, Host host) {
		if (host.vmCreate(vm)) { // VM successfully created on the specified host
			getVmTable().put(vm.getUid(), host);
			createdVmNum++;
			
			Log.formatLine("%.2f: Edge VM #" + vm.getId() + " has been allocated to the host #" + host.getId(),CloudSim.clock());
			return true;
		}

		return false;
	}

	/**
	 * Optimizes VM allocation across hosts.
	 * Currently not implemented for the edge allocation policy.
	 * 
	 * @param vmList List of VMs to optimize allocation for
	 * @return null as optimization is not implemented
	 */
	@Override
	public List<Map<String, Object>> optimizeAllocation(
			List<? extends Vm> vmList) {
		// Optimization not implemented for edge allocation policy
		return null;
	}

	/**
	 * Deallocates a VM from its assigned host and destroys the VM.
	 * Removes the VM from tracking structures and cleans up resources.
	 * 
	 * @param vm The VM to be deallocated and destroyed
	 */
	@Override
	public void deallocateHostForVm(Vm vm) {
		Host host = getVmTable().remove(vm.getUid());
		if (host != null) {
			host.vmDestroy(vm);
		}
	}

	/**
	 * Returns the host where the specified VM is currently allocated.
	 * 
	 * @param vm The VM whose host is requested
	 * @return Host where the VM is allocated, or null if not found
	 */
	@Override
	public Host getHost(Vm vm) {
		return getVmTable().get(vm.getUid());
	}

	/**
	 * Returns the host where the specified VM (by ID and user) is allocated.
	 * 
	 * @param vmId The VM identifier
	 * @param userId The user identifier owning the VM
	 * @return Host where the VM is allocated, or null if not found
	 */
	@Override
	public Host getHost(int vmId, int userId) {
		return getVmTable().get(Vm.getUid(userId, vmId));
	}

	/**
	 * Returns the total number of VMs successfully created across all datacenters.
	 * Used for monitoring VM creation progress and validation.
	 * 
	 * @return Global count of successfully created edge VMs
	 */
	public static int getCreatedVmNum(){
		return createdVmNum;
	}
	
	/**
	 * Gets the VM-to-host mapping table for this allocation policy.
	 * 
	 * @return Map containing VM UID to Host mappings
	 */
	public Map<String, Host> getVmTable() {
		return vmTable;
	}

	/**
	 * Sets the VM-to-host mapping table for this allocation policy.
	 * 
	 * @param vmTable Map containing VM UID to Host mappings
	 */
	protected void setVmTable(Map<String, Host> vmTable) {
		this.vmTable = vmTable;
	}
}
