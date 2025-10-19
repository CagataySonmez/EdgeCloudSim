/*
 * Title:        EdgeCloudSim - MobileVM
 * 
 * Description: 
 * MobileVM adds vm type information over CloudSim's VM class
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

import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.Vm;

import edu.boun.edgecloudsim.core.SimSettings;

/**
 * MobileVM extends CloudSim's VM class to provide mobile device-specific virtual machine functionality.
 * Represents lightweight VMs running on mobile devices with limited computational resources.
 * Adds VM type identification for distinguishing from cloud and edge VMs.
 */
public class MobileVM extends Vm {
	private SimSettings.VM_TYPES type;
	
	/**
	 * Constructor for MobileVM with specified resource parameters.
	 * Creates a VM optimized for mobile device constraints and capabilities.
	 * 
	 * @param id Unique identifier for this mobile VM
	 * @param userId ID of the user/broker that owns this VM
	 * @param mips Processing capacity in MIPS (typically lower than cloud/edge VMs)
	 * @param numberOfPes Number of processing elements (cores) on mobile device
	 * @param ram Memory capacity in MB (limited by mobile device constraints)
	 * @param bw Network bandwidth capacity for mobile communications
	 * @param size Storage capacity in MB on the mobile device
	 * @param vmm Virtual Machine Monitor type for mobile virtualization
	 * @param cloudletScheduler Scheduler for managing tasks on this mobile VM
	 */
	public MobileVM(int id, int userId, double mips, int numberOfPes, int ram,
			long bw, long size, String vmm, CloudletScheduler cloudletScheduler) {
		super(id, userId, mips, numberOfPes, ram, bw, size, vmm, cloudletScheduler);

		type = SimSettings.VM_TYPES.MOBILE_VM;
	}

	/**
	 * Gets the type of this VM for identification and routing purposes.
	 * @return VM_TYPES.MOBILE_VM indicating this is a mobile device VM
	 */
	public SimSettings.VM_TYPES getVmType(){
		return type;
	}
}
