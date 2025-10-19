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

/**
 * Abstract base class for network delay calculation models in EdgeCloudSim.
 * 
 * <p>This class provides the foundation for implementing different network delay models
 * that calculate communication delays between various devices in the edge-cloud ecosystem.
 * Concrete implementations should extend this class and provide specific algorithms
 * for calculating upload and download delays based on network characteristics,
 * device locations, and task properties.</p>
 * 
 * <p>The network model handles delay calculations for multiple communication paths:
 * <ul>
 *   <li>Mobile device to edge server (WLAN)</li>
 *   <li>Edge server to cloud datacenter (WAN)</li>
 *   <li>Inter-edge server communication (LAN)</li>
 *   <li>Edge orchestrator communications</li>
 * </ul></p>
 * 
 * @see edu.boun.edgecloudsim.core.ScenarioFactory
 */
public abstract class NetworkModel {
	/** Total number of mobile devices in the simulation scenario */
	protected int numberOfMobileDevices;
	
	/** Simulation scenario identifier for configuration-specific behavior */
	protected String simScenario;

	/**
	 * Constructs a new NetworkModel instance with the specified parameters.
	 * 
	 * @param _numberOfMobileDevices the total number of mobile devices in the simulation
	 * @param _simScenario the simulation scenario identifier used for configuration
	 */
	public NetworkModel(int _numberOfMobileDevices, String _simScenario){
		numberOfMobileDevices = _numberOfMobileDevices;
		simScenario = _simScenario;
	}

	/**
	 * Initializes the custom network model with scenario-specific parameters.
	 * 
	 * <p>This method should be called once before any delay calculations are performed.
	 * Implementations should use this method to set up network parameters, load
	 * configuration values, and perform any necessary preprocessing for the specific
	 * network model algorithm.</p>
	 * 
	 * <p>Common initialization tasks include:
	 * <ul>
	 *   <li>Loading bandwidth and latency configuration</li>
	 *   <li>Setting up statistical parameters for queuing models</li>
	 *   <li>Initializing network topology information</li>
	 *   <li>Preprocessing task characteristics for delay calculations</li>
	 * </ul></p>
	 */
	public abstract void initialize();

	/**
	 * Calculates the network upload delay from source device to destination device.
	 * 
	 * <p>This method computes the time required for a task's input data to be transmitted
	 * from the source device to the destination device. The calculation should consider
	 * network topology, current network conditions, task size, and device characteristics.</p>
	 * 
	 * <p>Different communication paths have different delay characteristics:
	 * <ul>
	 *   <li>Mobile to Edge: WLAN delay based on local network conditions</li>
	 *   <li>Mobile to Cloud: WLAN + WAN delays with potential queuing</li>
	 *   <li>Edge to Cloud: WAN delay with backbone network characteristics</li>
	 *   <li>Edge to Edge: LAN delay for inter-edge communication</li>
	 * </ul></p>
	 * 
	 * @param sourceDeviceId the ID of the device sending the task
	 * @param destDeviceId the ID of the device receiving the task
	 * @param task the task being transmitted (contains size and other properties)
	 * @return the upload delay in seconds, or -1 if transmission fails
	 */
	public abstract double getUploadDelay(int sourceDeviceId, int destDeviceId, Task task);

	/**
	 * Calculates the network download delay from source device to destination device.
	 * 
	 * <p>This method computes the time required for a task's output data to be transmitted
	 * back from the processing device to the requesting device. The calculation should
	 * consider the same network factors as upload delay but may use different data sizes
	 * based on task output characteristics.</p>
	 * 
	 * <p>Download delays typically involve:
	 * <ul>
	 *   <li>Result data transmission based on task output size</li>
	 *   <li>Reverse path network conditions (may differ from upload path)</li>
	 *   <li>Potential asymmetric bandwidth (different up/down rates)</li>
	 *   <li>Network congestion from concurrent downloads</li>
	 * </ul></p>
	 * 
	 * @param sourceDeviceId the ID of the device sending the result
	 * @param destDeviceId the ID of the device receiving the result
	 * @param task the completed task being returned (contains output size)
	 * @return the download delay in seconds, or -1 if transmission fails
	 */
	public abstract double getDownloadDelay(int sourceDeviceId, int destDeviceId, Task task);

	/**
	 * Notifies the network model that an upload operation has started.
	 * 
	 * <p>This callback allows the network model to track ongoing network operations
	 * and adjust delay calculations accordingly. Some network models may use this
	 * information to model network congestion, bandwidth sharing, or queuing effects.</p>
	 * 
	 * @param accessPointLocation the location of the access point handling the upload
	 * @param destDeviceId the ID of the destination device receiving the upload
	 */
	public abstract void uploadStarted(Location accessPointLocation, int destDeviceId);
	
	/**
	 * Notifies the network model that an upload operation has finished.
	 * 
	 * <p>This callback allows the network model to update its internal state
	 * regarding network utilization and resource availability.</p>
	 * 
	 * @param accessPointLocation the location of the access point that handled the upload
	 * @param destDeviceId the ID of the destination device that received the upload
	 */
	public abstract void uploadFinished(Location accessPointLocation, int destDeviceId);
	
	/**
	 * Notifies the network model that a download operation has started.
	 * 
	 * <p>This callback enables tracking of download operations for models that
	 * consider network congestion and bandwidth contention in their delay calculations.</p>
	 * 
	 * @param accessPointLocation the location of the access point handling the download
	 * @param sourceDeviceId the ID of the source device sending the download
	 */
	public abstract void downloadStarted(Location accessPointLocation, int sourceDeviceId);
	
	/**
	 * Notifies the network model that a download operation has finished.
	 * 
	 * <p>This callback allows the network model to update network utilization
	 * statistics and resource availability for future delay calculations.</p>
	 * 
	 * @param accessPointLocation the location of the access point that handled the download
	 * @param sourceDeviceId the ID of the source device that sent the download
	 */
	public abstract void downloadFinished(Location accessPointLocation, int sourceDeviceId);
}
