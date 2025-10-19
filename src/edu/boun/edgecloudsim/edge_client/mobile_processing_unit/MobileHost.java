/*
 * Title:        EdgeCloudSim - MobileHost
 * 
 * Description: 
 * MobileHost adds associated mobile device id information over CloudSim's Host class
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
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.VmScheduler;
import org.cloudbus.cloudsim.provisioners.BwProvisioner;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;

/**
 * MobileHost extends CloudSim's Host class to represent mobile device processing capabilities.
 * Associates mobile device ID with host to enable device-specific resource management.
 * Models mobile devices as lightweight hosts with limited computational resources.
 */
public class MobileHost extends Host {
	private int mobileDeviceId;
	
	/**
	 * Constructor for MobileHost with specified resource parameters.
	 * 
	 * @param id Unique identifier for this mobile host
	 * @param ramProvisioner RAM resource provisioner for this host
	 * @param bwProvisioner Bandwidth resource provisioner for this host
	 * @param storage Storage capacity in MB for this mobile device
	 * @param peList List of processing elements (cores) available on this device
	 * @param vmScheduler VM scheduler for managing VMs on this mobile host
	 */
	public MobileHost(int id, RamProvisioner ramProvisioner,
			BwProvisioner bwProvisioner, long storage,
			List<? extends Pe> peList, VmScheduler vmScheduler) {
		super(id, ramProvisioner, bwProvisioner, storage, peList, vmScheduler);

	}
	
	/**
	 * Associates this host with a specific mobile device.
	 * @param _mobileDeviceId The ID of the mobile device this host represents
	 */
	public void setMobileDeviceId(int _mobileDeviceId){
		mobileDeviceId=_mobileDeviceId;
	}
	
	/**
	 * Gets the mobile device ID associated with this host.
	 * @return The ID of the mobile device this host represents
	 */
	public int getMobileDeviceId(){
		return mobileDeviceId;
	}
}
