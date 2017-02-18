/*
 * Title:        EdgeCloudSim - Task
 * 
 * Description: 
 * Task adds app type, task submission location, mobile device id and host id
 * information to CloudSim's Cloudlet class.
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_client;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModel;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.utils.Location;

public class Task extends Cloudlet {
	private SimSettings.APP_TYPES type;
	private Location submittedLocation;
	private int mobileDeviceId;
	private int hostIndex;

	public Task(int _mobileDeviceId, int cloudletId, long cloudletLength, int pesNumber,
			long cloudletFileSize, long cloudletOutputSize,
			UtilizationModel utilizationModelCpu,
			UtilizationModel utilizationModelRam,
			UtilizationModel utilizationModelBw) {
		super(cloudletId, cloudletLength, pesNumber, cloudletFileSize,
				cloudletOutputSize, utilizationModelCpu, utilizationModelRam,
				utilizationModelBw);
		
		mobileDeviceId = _mobileDeviceId;
	}

	
	public void setSubmittedLocation(Location _submittedLocation){
		submittedLocation =_submittedLocation;
	}
	
	public void setAssociatedHostId(int _hostIndex){
		hostIndex=_hostIndex;
	}

	public void setTaskType(SimSettings.APP_TYPES _type){
		type=_type;
	}

	public int getMobileDeviceId(){
		return mobileDeviceId;
	}
	
	public Location getSubmittedLocation(){
		return submittedLocation;
	}
	
	public int getAssociatedHostId(){
		return hostIndex;
	}

	public SimSettings.APP_TYPES getTaskType(){
		return type;
	}
}
