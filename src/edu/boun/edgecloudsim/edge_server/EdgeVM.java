/*
 * Title:        EdgeCloudSim - EdgeVM
 * 
 * Description: 
 * EdgeVM adds vm type information over CloudSim's VM class
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_server;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.Vm;

import edu.boun.edgecloudsim.core.SimSettings;

/**
 * Extended VM class for edge computing scenarios in EdgeCloudSim.
 * 
 * This class extends CloudSim's VM class to add edge-specific functionality
 * including VM type classification and dynamic resource reconfiguration.
 * EdgeVMs are specifically designed for distributed edge infrastructure
 * where resources may need to be adjusted during runtime based on
 * changing workload conditions and orchestration decisions.
 * 
 * Key features:
 * - VM type identification for edge-specific orchestration
 * - Dynamic MIPS reconfiguration during simulation execution
 * - Integration with EdgeCloudSim's resource management framework
 */
public class EdgeVM extends Vm {
	private SimSettings.VM_TYPES type;    // VM type classification (EDGE_VM, CLOUD_VM, etc.)
	
	/**
	 * Constructs an EdgeVM with the specified resource configuration.
	 * Initializes the VM as an EDGE_VM type and delegates basic VM functionality
	 * to the parent CloudSim VM class.
	 * 
	 * @param id Unique identifier for this VM
	 * @param userId ID of the user/broker owning this VM
	 * @param mips Processing capacity in million instructions per second
	 * @param numberOfPes Number of processing elements (CPU cores)
	 * @param ram Memory allocation in MB
	 * @param bw Bandwidth allocation in Mbps
	 * @param size Storage allocation in MB
	 * @param vmm Virtual machine monitor type
	 * @param cloudletScheduler Scheduling policy for tasks assigned to this VM
	 */
	public EdgeVM(int id, int userId, double mips, int numberOfPes, int ram,
			long bw, long size, String vmm, CloudletScheduler cloudletScheduler) {
		super(id, userId, mips, numberOfPes, ram, bw, size, vmm, cloudletScheduler);

		type = SimSettings.VM_TYPES.EDGE_VM;
	}

	/**
	 * Returns the type classification of this VM for orchestration purposes.
	 * 
	 * @return VM type enumeration value (typically EDGE_VM for EdgeVM instances)
	 */
	public SimSettings.VM_TYPES getVmType(){
		return type;
	}

	/**
	 * Dynamically reconfigures the MIPS capacity of this VM during simulation.
	 * This method enables runtime resource adjustment for adaptive edge computing
	 * scenarios where VM performance needs to be modified based on workload
	 * conditions or orchestration decisions.
	 * 
	 * The reconfiguration process:
	 * 1. Updates the VM's MIPS value
	 * 2. Deallocates current PE assignments from the host scheduler
	 * 3. Reallocates PEs with the new MIPS capacity
	 * 
	 * @param mips New processing capacity in million instructions per second
	 */
	public void reconfigureMips(double mips){
		super.setMips(mips);
		// Deallocate current PE assignments to prepare for reconfiguration
		super.getHost().getVmScheduler().deallocatePesForVm(this);
		
		// Create new MIPS allocation for each PE
		List<Double> mipsShareAllocated = new ArrayList<Double>();
		for(int i= 0; i<getNumberOfPes(); i++)
			mipsShareAllocated.add(mips);

		// Reallocate PEs with updated MIPS capacity
		super.getHost().getVmScheduler().allocatePesForVm(this, mipsShareAllocated);
	}
}
