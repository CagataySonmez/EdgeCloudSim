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

public class EdgeHost extends Host {
	private Location location;
	
	public EdgeHost(int id, RamProvisioner ramProvisioner,
			BwProvisioner bwProvisioner, long storage,
			List<? extends Pe> peList, VmScheduler vmScheduler) {
		super(id, ramProvisioner, bwProvisioner, storage, peList, vmScheduler);

	}
	
	public void setPlace(Location _location){
		location=_location;
	}
	
	public Location getLocation(){
		return location;
	}
}
