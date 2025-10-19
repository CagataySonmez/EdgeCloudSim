/*
 * Title:        EdgeCloudSim - CloudVM
 * 
 * Description: 
 * CloudVM adds vm type information over CloudSim's VM class
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.cloud_server;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.Vm;

import edu.boun.edgecloudsim.core.SimSettings;

/**
 * CloudVM extends CloudSim's VM class to provide cloud-specific virtual machine functionality.
 * Adds VM type identification and dynamic resource reconfiguration capabilities.
 */
public class CloudVM extends Vm {
	private SimSettings.VM_TYPES type;

	/**
	 * Constructor for CloudVM with specified resource parameters.
	 * 
	 * @param id Unique identifier for this VM
	 * @param userId ID of the user/broker that owns this VM
	 * @param mips Processing capacity in MIPS (Million Instructions Per Second)
	 * @param numberOfPes Number of processing elements (cores)
	 * @param ram Memory capacity in MB
	 * @param bw Network bandwidth capacity
	 * @param size Storage capacity in MB
	 * @param vmm Virtual Machine Monitor type (e.g., "Xen")
	 * @param cloudletScheduler Scheduler for managing cloudlets on this VM
	 */
	public CloudVM(int id, int userId, double mips, int numberOfPes, int ram,
			long bw, long size, String vmm, CloudletScheduler cloudletScheduler) {
		super(id, userId, mips, numberOfPes, ram, bw, size, vmm, cloudletScheduler);

		type = SimSettings.VM_TYPES.CLOUD_VM;
	}

	/**
	 * Gets the type of this VM for identification purposes.
	 * @return VM_TYPES.CLOUD_VM indicating this is a cloud VM
	 */
	public SimSettings.VM_TYPES getVmType(){
		return type;
	}

	/**
	 * Dynamically reconfigures the MIPS processing capacity of this VM.
	 * Updates both the VM's MIPS value and reallocates processing elements on the host.
	 * 
	 * @param mips New MIPS processing capacity for this VM
	 */
	public void reconfigureMips(double mips){
		// Update the VM's MIPS capacity
		super.setMips(mips);
		
		// Deallocate current processing elements from the host scheduler
		super.getHost().getVmScheduler().deallocatePesForVm(this);

		// Create new MIPS allocation list for all processing elements
		List<Double> mipsShareAllocated = new ArrayList<Double>();
		for(int i= 0; i<getNumberOfPes(); i++)
			mipsShareAllocated.add(mips);

		// Reallocate processing elements with new MIPS capacity
		super.getHost().getVmScheduler().allocatePesForVm(this, mipsShareAllocated);
	}
}
