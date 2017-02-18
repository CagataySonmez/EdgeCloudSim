/*
 * Title:        EdgeCloudSim - Network Model
 * 
 * Description: 
 * NetworkModel is an abstract class which is used for calculating the
 * network delay from device to device. For those who wants to add a
 * custom Network Model to EdgeCloudSim should extend this class and
 * provide a concreate instance via ScenarioFactory
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.network;

public abstract class NetworkModel {
	protected int numberOfMobileDevices;

	public NetworkModel(int _numberOfMobileDevices){
		numberOfMobileDevices=_numberOfMobileDevices;
	};
	
	/**
	* initializes costom network model
	*/
	public abstract void initialize();
	
    /**
    * calculates the upload delay from source to destination device
    */
	public abstract double getUploadDelay(int sourceDeviceId, int destDeviceId);
	
    /**
    * calculates the download delay from source to destination device
    */
	public abstract double getDownloadDelay(int sourceDeviceId, int destDeviceId);
}
