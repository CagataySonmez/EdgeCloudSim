/*
 * Title:        EdgeCloudSim - Network Model
 * 
 * Description: 
 * NetworkModel is an abstract class which is used for calculating the
 * network delay from device to device. For those who wants to add a
 * custom Network Model to EdgeCloudSim should extend this class and
 * provide a concrete instance via ScenarioFactory
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.network;

import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.utils.Location;

public abstract class NetworkModel {
	protected int numberOfMobileDevices;
	protected String simScenario;

	public NetworkModel(int _numberOfMobileDevices, String _simScenario){
		numberOfMobileDevices=_numberOfMobileDevices;
		simScenario = _simScenario;
	};

	/**
	 * initializes custom network model
	 */
	public abstract void initialize();

	/**
	 * calculates the upload delay from source to destination device
	 */
	public abstract double getUploadDelay(int sourceDeviceId, int destDeviceId, Task task);

	/**
	 * calculates the download delay from source to destination device
	 */
	public abstract double getDownloadDelay(int sourceDeviceId, int destDeviceId, Task task);

	/**
	 * Mobile device manager should inform network manager about the network operation
	 * This information may be important for some network delay models
	 */
	public abstract void uploadStarted(Location accessPointLocation, int destDeviceId);
	public abstract void uploadFinished(Location accessPointLocation, int destDeviceId);
	public abstract void downloadStarted(Location accessPointLocation, int sourceDeviceId);
	public abstract void downloadFinished(Location accessPointLocation, int sourceDeviceId);
}
