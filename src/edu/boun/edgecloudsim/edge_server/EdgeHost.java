/*
 * Title:        EdgeCloudSim - EdgeHost
 * 
 * Description: 
 * EdgeHost adds location information over CloudSim's Host class
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_server;

import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.VmScheduler;
import org.cloudbus.cloudsim.provisioners.BwProvisioner;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;

import edu.boun.edgecloudsim.utils.Location;

/**
 * Extended Host class for location-aware edge computing in EdgeCloudSim.
 * 
 * This class extends CloudSim's Host class to add geographic location information
 * essential for edge computing scenarios. EdgeHosts represent physical compute
 * resources deployed at specific geographic locations in the network edge,
 * enabling location-aware task placement and proximity-based orchestration.
 * 
 * Key features:
 * - Geographic location tracking for edge-aware placement decisions
 * - Integration with mobility models for dynamic device-to-edge associations
 * - Support for location-based resource selection algorithms
 * - Attractiveness modeling for heterogeneous edge deployment scenarios
 */
public class EdgeHost extends Host {
	private Location location;    // Geographic location information for this edge host
	
	/**
	 * Constructs an EdgeHost with the specified resource configuration.
	 * Initializes the host with processing elements, resource provisioners,
	 * and VM scheduling policy. Location information should be set separately
	 * using the setPlace() method.
	 * 
	 * @param id Unique identifier for this host
	 * @param ramProvisioner RAM allocation policy for VMs on this host
	 * @param bwProvisioner Bandwidth allocation policy for VMs on this host
	 * @param storage Total storage capacity available on this host
	 * @param peList List of processing elements (CPU cores) available
	 * @param vmScheduler VM scheduling policy for this host
	 */
	public EdgeHost(int id, RamProvisioner ramProvisioner,
			BwProvisioner bwProvisioner, long storage,
			List<? extends Pe> peList, VmScheduler vmScheduler) {
		super(id, ramProvisioner, bwProvisioner, storage, peList, vmScheduler);

	}
	
	/**
	 * Sets the geographic location of this edge host.
	 * Location information is crucial for edge computing scenarios where
	 * proximity-based decisions and location-aware orchestration are required.
	 * 
	 * @param _location Location object containing coordinates, place type, and WLAN ID
	 */
	public void setPlace(Location _location){
		location=_location;
	}
	
	/**
	 * Returns the geographic location of this edge host.
	 * Used by orchestrators and mobility models for location-aware
	 * task placement and device-to-host association decisions.
	 * 
	 * @return Location object with this host's geographic information
	 */
	public Location getLocation(){
		return location;
	}
}
