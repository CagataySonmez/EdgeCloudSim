/*
 * Title:        EdgeCloudSim - Custom VM Allocation Policy for Mobile Devices' VMs
 * 
 * Description: 
 * VmAllocationPolicy_Custom implements VmAllocationPolicy to decide which.
 * VM is created on which host located on the datacenters. For those
 * who wants to add another Vm Allocation Policy to EdgeCloudSim should
 * provide another concrete instance of VmAllocationPolicy via ScenarioFactory
 *
 * Please note that the mobile processing units are simulated via
 * CloudSim. It is assumed that the mobile devices operate Hosts
 * and VMs like a server. That is why the class names are similar
 * to other Cloud and Edge components (to provide consistency).
 *
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_client.mobile_processing_unit;

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
 * Custom VM allocation policy for mobile device VMs in EdgeCloudSim.
 * Implements deterministic VM-to-host mapping based on mobile device VM ID ranges.
 * Similar to VmAllocationPolicySimple but optimized for mobile device constraints.
 */
public class MobileVmAllocationPolicy_Custom extends VmAllocationPolicy {
	/** Mapping table of mobile VM UIDs to their assigned mobile hosts */
	private Map<String, Host> vmTable;
	/** Counter for tracking total number of created mobile VMs */
	private static int createdVmNum;
	/** Index of the mobile datacenter this policy manages */
	private int DataCenterIndex;
	
	/**
	 * Constructor for custom mobile VM allocation policy.
	 * 
	 * @param list List of mobile hosts available for VM allocation
	 * @param _DataCenterIndex Index of the mobile datacenter this policy manages
	 */
	public MobileVmAllocationPolicy_Custom(List<? extends Host> list, int _DataCenterIndex) {
		super(list);
		
		setVmTable(new HashMap<String, Host>());
		DataCenterIndex = _DataCenterIndex;
		createdVmNum = 0;
	}

	/**
	 * Allocates a mobile host for the given mobile VM using deterministic mapping.
	 * Maps mobile VMs to mobile hosts based on VM ID ranges after edge and cloud VMs.
	 * 
	 * @param vm The mobile VM that needs host allocation
	 * @return true if allocation successful, false otherwise
	 */
	@Override
	public boolean allocateHostForVm(Vm vm) {
		boolean result = false;

		// Check if VM is not already allocated and is a MobileVM instance
		if (!getVmTable().containsKey(vm.getUid()) && vm instanceof MobileVM) {
			// Calculate target mobile host index based on VM ID range (after edge and cloud VMs)
			int hostIndex = vm.getId() - SimSettings.getInstance().getNumOfEdgeVMs() - SimSettings.getInstance().getNumOfCloudVMs();
			
			// Only allocate if this is the designated mobile datacenter
			if(DataCenterIndex == SimSettings.MOBILE_DATACENTER_ID){
				Host host = getHostList().get(hostIndex);
				result = host.vmCreate(vm);
	
				if (result) { // Mobile VM successfully created on the mobile host
					getVmTable().put(vm.getUid(), host);
					createdVmNum++;
					Log.formatLine("%.2f: Mobile VM #" + vm.getId() + " has been allocated to the host #" + host.getId(),CloudSim.clock());
					result = true;
				}
			}
		}
		
		return result;
	}

	/**
	 * Allocates a specific mobile host for the given mobile VM.
	 * Used when a specific mobile host is preferred for VM placement.
	 * 
	 * @param vm The mobile VM to be allocated
	 * @param host The specific mobile host to allocate the VM to
	 * @return true if allocation successful, false otherwise
	 */
	@Override
	public boolean allocateHostForVm(Vm vm, Host host) {
		if (host.vmCreate(vm)) { // Mobile VM successfully created on the specified mobile host
			getVmTable().put(vm.getUid(), host);
			createdVmNum++;
			
			Log.formatLine("%.2f: Mobile VM #" + vm.getId() + " has been allocated to the host #" + host.getId(),CloudSim.clock());
			return true;
		}

		return false;
	}

	/**
	 * Optimizes mobile VM allocation across mobile hosts.
	 * Currently not implemented as static allocation policy is used for mobile devices.
	 * 
	 * @param vmList List of mobile VMs to optimize allocation for
	 * @return null (optimization not implemented for mobile devices)
	 */
	@Override
	public List<Map<String, Object>> optimizeAllocation(
			List<? extends Vm> vmList) {
		// Static allocation policy for mobile devices - no optimization needed
		return null;
	}

	/**
	 * Deallocates mobile host resources for the given mobile VM.
	 * Removes VM from the allocation table and destroys it on the mobile host.
	 * 
	 * @param vm The mobile VM to be deallocated
	 */
	@Override
	public void deallocateHostForVm(Vm vm) {
		Host host = getVmTable().remove(vm.getUid());
		if (host != null) {
			host.vmDestroy(vm);
		}
	}

	/**
	 * Gets the mobile host currently assigned to the given mobile VM.
	 * 
	 * @param vm The mobile VM to query host assignment for
	 * @return Mobile host where the VM is allocated, null if not found
	 */
	@Override
	public Host getHost(Vm vm) {
		return getVmTable().get(vm.getUid());
	}

	/**
	 * Gets the mobile host assigned to a mobile VM by its ID and user ID.
	 * 
	 * @param vmId The mobile VM identifier
	 * @param userId The user/broker ID that owns the mobile VM
	 * @return Mobile host where the VM is allocated, null if not found
	 */
	@Override
	public Host getHost(int vmId, int userId) {
		return getVmTable().get(Vm.getUid(userId, vmId));
	}

	/**
	 * Gets the total number of mobile VMs created by this allocation policy.
	 * Static method for global mobile VM creation tracking.
	 * 
	 * @return Total number of mobile VMs created
	 */
	public static int getCreatedVmNum(){
		return createdVmNum;
	}
	
	/**
	 * Gets the mobile VM allocation table mapping VM UIDs to mobile hosts.
	 * 
	 * @return Map of mobile VM UIDs to their assigned mobile hosts
	 */
	public Map<String, Host> getVmTable() {
		return vmTable;
	}

	/**
	 * Sets the mobile VM allocation table.
	 * Protected method for internal table management.
	 * 
	 * @param vmTable Map of mobile VM UIDs to mobile hosts for allocation tracking
	 */
	protected void setVmTable(Map<String, Host> vmTable) {
		this.vmTable = vmTable;
	}
}
