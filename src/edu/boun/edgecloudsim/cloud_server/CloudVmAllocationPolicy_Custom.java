/*
 * Title:        EdgeCloudSim - Custom VM Allocation Policy for Cloud VMs
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

package edu.boun.edgecloudsim.cloud_server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.core.SimSettings;

/**
 * Custom VM allocation policy for cloud VMs in EdgeCloudSim.
 * Implements deterministic VM-to-host mapping based on VM ID ranges.
 * Similar to VmAllocationPolicySimple but with EdgeCloudSim-specific optimizations.
 */
public class CloudVmAllocationPolicy_Custom extends VmAllocationPolicy {
	/** Mapping table of VM UIDs to their assigned hosts */
	private Map<String, Host> vmTable;
	/** Counter for tracking total number of created VMs */
	private static int createdVmNum;
	/** Index of the datacenter this policy manages */
	private int DataCenterIndex;
	
	/**
	 * Constructor for custom cloud VM allocation policy.
	 * 
	 * @param list List of hosts available for VM allocation
	 * @param _DataCenterIndex Index of the datacenter this policy manages
	 */
	public CloudVmAllocationPolicy_Custom(List<? extends Host> list, int _DataCenterIndex) {
		super(list);
		
		setVmTable(new HashMap<String, Host>());
		DataCenterIndex=_DataCenterIndex;
		createdVmNum = 0;
	}

	/**
	 * Allocates a host for the given VM using deterministic mapping.
	 * Maps VMs to hosts based on VM ID ranges to ensure balanced distribution.
	 * 
	 * @param vm The VM that needs host allocation
	 * @return true if allocation successful, false otherwise
	 */
	@Override
	public boolean allocateHostForVm(Vm vm) {
		boolean result = false;

		// Check if VM is not already allocated and is a CloudVM instance
		if (!getVmTable().containsKey(vm.getUid()) && vm instanceof CloudVM) {
			// Calculate target host index based on VM ID range
			int hostIndex = (vm.getId() - SimSettings.getInstance().getNumOfEdgeVMs()) / SimSettings.getInstance().getNumOfCloudVMsPerHost();
			
			// Only allocate if this is the designated cloud datacenter
			if(DataCenterIndex == SimSettings.CLOUD_DATACENTER_ID){
				Host host = getHostList().get(hostIndex);
				result = host.vmCreate(vm);
	
				if (result) { // VM successfully created on the host
					getVmTable().put(vm.getUid(), host);
					createdVmNum++;
					Log.formatLine("%.2f: Cloud VM #" + vm.getId() + " has been allocated to the host #" + host.getId(),CloudSim.clock());
					result = true;
				}
			}
		}
		
		return result;
	}

	/**
	 * Allocates a specific host for the given VM.
	 * Used when a specific host is preferred for VM placement.
	 * 
	 * @param vm The VM to be allocated
	 * @param host The specific host to allocate the VM to
	 * @return true if allocation successful, false otherwise
	 */
	@Override
	public boolean allocateHostForVm(Vm vm, Host host) {
		if (host.vmCreate(vm)) { // VM successfully created on the specified host
			getVmTable().put(vm.getUid(), host);
			createdVmNum++;
			
			Log.formatLine("%.2f: Cloud VM #" + vm.getId() + " has been allocated to the host #" + host.getId(),CloudSim.clock());
			return true;
		}

		return false;
	}

	/**
	 * Optimizes VM allocation across hosts.
	 * Currently not implemented as static allocation policy is used.
	 * 
	 * @param vmList List of VMs to optimize allocation for
	 * @return null (optimization not implemented)
	 */
	@Override
	public List<Map<String, Object>> optimizeAllocation(
			List<? extends Vm> vmList) {
		// Static allocation policy - no optimization needed
		return null;
	}

	/**
	 * Deallocates host resources for the given VM.
	 * Removes VM from the allocation table and destroys it on the host.
	 * 
	 * @param vm The VM to be deallocated
	 */
	@Override
	public void deallocateHostForVm(Vm vm) {
		Host host = getVmTable().remove(vm.getUid());
		if (host != null) {
			host.vmDestroy(vm);
		}
	}

	/**
	 * Gets the host currently assigned to the given VM.
	 * 
	 * @param vm The VM to query host assignment for
	 * @return Host where the VM is allocated, null if not found
	 */
	@Override
	public Host getHost(Vm vm) {
		return getVmTable().get(vm.getUid());
	}

	/**
	 * Gets the host assigned to a VM by its ID and user ID.
	 * 
	 * @param vmId The VM identifier
	 * @param userId The user/broker ID that owns the VM
	 * @return Host where the VM is allocated, null if not found
	 */
	@Override
	public Host getHost(int vmId, int userId) {
		return getVmTable().get(Vm.getUid(userId, vmId));
	}

	/**
	 * Gets the total number of VMs created by this allocation policy.
	 * Static method for global VM creation tracking.
	 * 
	 * @return Total number of VMs created
	 */
	public static int getCreatedVmNum(){
		return createdVmNum;
	}
	
	/**
	 * Gets the VM allocation table mapping VM UIDs to hosts.
	 * 
	 * @return Map of VM UIDs to their assigned hosts
	 */
	public Map<String, Host> getVmTable() {
		return vmTable;
	}

	/**
	 * Sets the VM allocation table.
	 * Protected method for internal table management.
	 * 
	 * @param vmTable Map of VM UIDs to hosts for allocation tracking
	 */
	protected void setVmTable(Map<String, Host> vmTable) {
		this.vmTable = vmTable;
	}
}
