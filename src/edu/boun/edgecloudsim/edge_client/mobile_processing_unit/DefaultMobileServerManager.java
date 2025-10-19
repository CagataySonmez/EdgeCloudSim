/*
 * Title:        EdgeCloudSim - Mobile Server Manager
 * 
 * Description: 
 * DefaultMobileServerManager is responsible for creating datacenters, hosts and VMs.
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

import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.VmAllocationPolicy;

/**
 * Default implementation of MobileServerManager that disables local computation.
 * This implementation assumes mobile devices only generate tasks and offload them
 * to edge or cloud servers, without performing local processing.
 */
public class DefaultMobileServerManager extends MobileServerManager{

	/**
	 * Constructor for default mobile server manager.
	 */
	public DefaultMobileServerManager() {

	}

	/**
	 * Initializes the mobile server manager.
	 * No initialization required as local computation is disabled.
	 */
	@Override
	public void initialize() {
	}
	
	/**
	 * Creates and returns the VM allocation policy for mobile datacenters.
	 * @param list List of mobile hosts available for VM allocation
	 * @param dataCenterIndex Index of the mobile datacenter
	 * @return Custom VM allocation policy for mobile resources
	 */
	@Override
	public VmAllocationPolicy getVmAllocationPolicy(List<? extends Host> list, int dataCenterIndex) {
		return new MobileVmAllocationPolicy_Custom(list, dataCenterIndex);
	}

	/**
	 * Starts mobile datacenters for local computation.
	 * Not supported in default implementation - mobile devices only offload tasks.
	 * @throws Exception This method does nothing as local computation is disabled
	 */
	@Override
	public void startDatacenters() throws Exception {
		// Local computation is not supported in default Mobile Device Manager
	}

	/**
	 * Terminates mobile datacenters and releases resources.
	 * Not supported in default implementation - no local resources to terminate.
	 */
	@Override
	public void terminateDatacenters() {
		// Local computation is not supported in default Mobile Device Manager
	}

	/**
	 * Creates the list of VMs for mobile device local processing.
	 * Not supported in default implementation - mobile devices only generate tasks.
	 * @param brokerId The broker ID (unused in this implementation)
	 */
	@Override
	public void createVmList(int brokerId) {
		// Local computation is not supported in default Mobile Device Manager
	}

	/**
	 * Calculates average utilization of mobile processing units.
	 * Always returns 0 as local computation is disabled in default implementation.
	 * @return 0.0 indicating no local processing utilization
	 */
	@Override
	public double getAvgUtilization() {
		// Local computation is not supported in default Mobile Device Manager
		return 0;
	}
}
