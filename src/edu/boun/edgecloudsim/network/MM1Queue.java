/*
 * Title:        EdgeCloudSim - M/M/1 Queue model implementation
 * 
 * Description: 
 * MM1Queue implements M/M/1 Queue model for WLAN and WAN communication
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.network;

import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.edge_server.EdgeHost;
import edu.boun.edgecloudsim.utils.Location;

/**
 * Implementation of M/M/1 Queue network delay model for EdgeCloudSim.
 * 
 * <p>This class implements a Markovian queuing model (M/M/1) to calculate network delays
 * for both WLAN and WAN communications in edge computing scenarios. The model assumes
 * Poisson arrival processes and exponential service times, which are common assumptions
 * for network traffic modeling.</p>
 * 
 * <p>The M/M/1 queue model calculates delays based on:
 * <ul>
 *   <li><b>Arrival Rate (λ):</b> Determined by task generation patterns and device count</li>
 *   <li><b>Service Rate (μ):</b> Based on network bandwidth and average task sizes</li>
 *   <li><b>Queue Length:</b> Computed using Little's Law: L = λ/(μ-λ)</li>
 *   <li><b>Response Time:</b> Average delay including queuing and transmission time</li>
 * </ul></p>
 * 
 * <p>The model differentiates between WLAN (WiFi access point to device) and 
 * WAN (edge/cloud datacenter) communications, applying different bandwidth
 * and propagation delay characteristics for each network segment.</p>
 * 
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>Separate modeling of upload and download delays</li>
 *   <li>Location-aware device counting for congestion modeling</li>
 *   <li>Dynamic task size calculation from simulation configuration</li>
 *   <li>Integration with EdgeCloudSim's mobility and location models</li>
 * </ul></p>
 * 
 * @see NetworkModel
 * @see edu.boun.edgecloudsim.core.SimSettings
 */
public class MM1Queue extends NetworkModel {
	/** Mean inter-arrival time for WLAN tasks in seconds (1/λ for WLAN) */
	private double WlanPoissonMean;
	
	/** Mean inter-arrival time for WAN tasks in seconds (1/λ for WAN) */
	private double WanPoissonMean;
	
	/** Average input data size per task in bytes */
	private double avgTaskInputSize;
	
	/** Average output data size per task in bytes */
	private double avgTaskOutputSize;
	
	/** Maximum number of concurrent clients observed at any location (for debugging) */
	private int maxNumOfClientsInPlace;

	/**
	 * Constructs a new M/M/1 Queue network model instance.
	 * 
	 * <p>Initializes the queuing model with the specified number of mobile devices
	 * and simulation scenario. The actual queue parameters (arrival rates, service rates)
	 * are calculated during the initialization phase based on task characteristics
	 * and network configuration.</p>
	 * 
	 * @param _numberOfMobileDevices total number of mobile devices in the simulation
	 * @param _simScenario simulation scenario identifier for configuration lookup
	 */
	public MM1Queue(int _numberOfMobileDevices, String _simScenario) {
		super(_numberOfMobileDevices, _simScenario);
	}

	/**
	 * Initializes the M/M/1 queue model parameters from simulation configuration.
	 * 
	 * <p>This method calculates the key parameters needed for the M/M/1 queuing model
	 * by analyzing the task lookup table from SimSettings. It computes weighted averages
	 * of task characteristics across all task types defined in the simulation scenario.</p>
	 * 
	 * <p><b>Calculated Parameters:</b>
	 * <ul>
	 *   <li><b>WlanPoissonMean:</b> Mean inter-arrival time for WLAN tasks</li>
	 *   <li><b>WanPoissonMean:</b> Mean inter-arrival time for WAN tasks (adjusted for cloud percentage)</li>
	 *   <li><b>avgTaskInputSize:</b> Average input data size across all task types</li>
	 *   <li><b>avgTaskOutputSize:</b> Average output data size across all task types</li>
	 * </ul></p>
	 * 
	 * <p>The calculation uses task weights and cloud processing percentages to determine
	 * realistic arrival rates for both local edge processing and remote cloud processing.</p>
	 */
	@Override
	public void initialize() {
		WlanPoissonMean = 0;
		WanPoissonMean = 0;
		avgTaskInputSize = 0;
		avgTaskOutputSize = 0;
		maxNumOfClientsInPlace = 0;

		// Calculate weighted average inter-arrival times and task sizes from task lookup table
		double numOfTaskType = 0;
		SimSettings SS = SimSettings.getInstance();
		
		// Iterate through all defined task types in the simulation configuration
		for (int i = 0; i < SimSettings.getInstance().getTaskLookUpTable().length; i++) {
			// Extract task weight (percentage of total tasks) from lookup table
			double weight = SS.getTaskLookUpTable()[i][0] / (double)100;
			
			if(weight != 0) {
				// Accumulate WLAN inter-arrival time (weighted by task frequency)
				WlanPoissonMean += (SS.getTaskLookUpTable()[i][2]) * weight;

				// Calculate WAN inter-arrival time based on cloud processing percentage
				// Higher cloud percentage means more frequent WAN communications
				double percentageOfCloudCommunication = SS.getTaskLookUpTable()[i][1];
				WanPoissonMean += (WlanPoissonMean) * ((double)100 / percentageOfCloudCommunication) * weight;

				// Accumulate weighted average input and output data sizes
				avgTaskInputSize += SS.getTaskLookUpTable()[i][5] * weight;
				avgTaskOutputSize += SS.getTaskLookUpTable()[i][6] * weight;

				numOfTaskType++;
			}
		}

		// Normalize accumulated values by number of active task types
		WlanPoissonMean = WlanPoissonMean / numOfTaskType;
		avgTaskInputSize = avgTaskInputSize / numOfTaskType;
		avgTaskOutputSize = avgTaskOutputSize / numOfTaskType;
	}

	/**
	 * Calculates upload delay using M/M/1 queuing model for different destination types.
	 * 
	 * <p>In EdgeCloudSim scenarios, the source device is always a mobile device that needs
	 * to upload task data to various destinations. The method applies different delay
	 * calculations based on the network path:</p>
	 * 
	 * <p><b>Supported Upload Paths:</b>
	 * <ul>
	 *   <li><b>Mobile → Cloud:</b> WLAN delay + WAN delay (two-hop communication)</li>
	 *   <li><b>Mobile → Edge Orchestrator:</b> WLAN delay + Internal LAN delay</li>
	 *   <li><b>Mobile → Edge Device:</b> WLAN delay only (single-hop)</li>
	 * </ul></p>
	 * 
	 * <p>The delay calculation uses the device's current location to determine the serving
	 * access point and counts co-located devices for congestion modeling.</p>
	 * 
	 * @param sourceDeviceId ID of the mobile device uploading data (always mobile)
	 * @param destDeviceId ID of destination (cloud, orchestrator, or edge device)
	 * @param task the task being uploaded (used for size-based delay calculation)
	 * @return total upload delay in seconds, or 0 if calculation fails
	 */
	@Override
	public double getUploadDelay(int sourceDeviceId, int destDeviceId, Task task) {
		double delay = 0;
		// Get mobile device's current location for access point determination
		Location accessPointLocation = SimManager.getInstance().getMobilityModel().getLocation(sourceDeviceId, CloudSim.clock());

		// Case 1: Mobile device uploading to cloud datacenter (two-hop: WLAN + WAN)
		if(destDeviceId == SimSettings.CLOUD_DATACENTER_ID){
			// Calculate WLAN delay from mobile device to edge access point
			double wlanDelay = getWlanUploadDelay(accessPointLocation, CloudSim.clock());
			// Calculate WAN delay from edge to cloud (accounting for WLAN transmission time)
			double wanDelay = getWanUploadDelay(accessPointLocation, CloudSim.clock() + wlanDelay);
			// Only proceed if both segments are available (positive delays)
			if(wlanDelay > 0 && wanDelay > 0)
				delay = wlanDelay + wanDelay;
		}
		// Case 2: Mobile device uploading to edge orchestrator (WLAN + internal routing)
		else if(destDeviceId == SimSettings.EDGE_ORCHESTRATOR_ID){
			delay = getWlanUploadDelay(accessPointLocation, CloudSim.clock()) +
					SimSettings.getInstance().getInternalLanDelay();
		}
		// Case 3: Mobile device uploading to edge device (single-hop WLAN)
		else if (destDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID) {
			delay = getWlanUploadDelay(accessPointLocation, CloudSim.clock());
		}

		return delay;
	}

	/**
	 * Calculates download delay using M/M/1 queuing model for result data transmission.
	 * 
	 * <p>In EdgeCloudSim scenarios, the destination device is typically a mobile device
	 * receiving processed results from various processing nodes. The method handles
	 * different source-destination combinations with appropriate delay calculations.</p>
	 * 
	 * <p><b>Supported Download Paths:</b>
	 * <ul>
	 *   <li><b>Cloud → Mobile:</b> WAN delay + WLAN delay (reverse path from upload)</li>
	 *   <li><b>Edge Device → Mobile:</b> WLAN delay + potential inter-edge routing</li>
	 *   <li><b>Edge Orchestrator → Edge Device:</b> Internal LAN delay only</li>
	 * </ul></p>
	 * 
	 * <p>For edge-to-mobile downloads, the method checks if source and destination are
	 * served by different access points and adds internal LAN delay for cross-location routing.</p>
	 * 
	 * @param sourceDeviceId ID of device sending results (cloud, edge, or orchestrator)
	 * @param destDeviceId ID of destination device (typically mobile device)
	 * @param task the completed task being returned (contains result size information)
	 * @return total download delay in seconds, or 0 if calculation fails
	 */
	@Override
	public double getDownloadDelay(int sourceDeviceId, int destDeviceId, Task task) {
		// Special Case: Edge orchestrator sending control/config data to edge device
		if(sourceDeviceId == SimSettings.EDGE_ORCHESTRATOR_ID &&
				destDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID){
			return SimSettings.getInstance().getInternalLanDelay();
		}

		double delay = 0;
		// Get destination mobile device's current location for access point determination
		Location accessPointLocation = SimManager.getInstance().getMobilityModel().getLocation(destDeviceId, CloudSim.clock());

		// Case 1: Cloud server downloading results to mobile device (two-hop: WAN + WLAN)
		if(sourceDeviceId == SimSettings.CLOUD_DATACENTER_ID){
			// Calculate WLAN delay from access point to mobile device
			double wlanDelay = getWlanDownloadDelay(accessPointLocation, CloudSim.clock());
			// Calculate WAN delay from cloud to edge (accounting for WLAN transmission time)
			double wanDelay = getWanDownloadDelay(accessPointLocation, CloudSim.clock() + wlanDelay);
			// Only proceed if both network segments are available
			if(wlanDelay > 0 && wanDelay > 0)
				delay = wlanDelay + wanDelay;
		}
		// Case 2: Edge device downloading results to mobile device
		else{
			// Base WLAN delay from access point to mobile device
			delay = getWlanDownloadDelay(accessPointLocation, CloudSim.clock());

			// Get the edge host serving the source device for location comparison
			EdgeHost host = (EdgeHost)(SimManager.
					getInstance().
					getEdgeServerManager().
					getDatacenterList().get(sourceDeviceId).
					getHostList().get(0));

			// Check if source edge server is in a different location than destination mobile device
			// If so, add inter-edge routing delay (round-trip through network infrastructure)
			// Note: In this scenario, serving WLAN ID equals host ID (one host per location)
			if(host.getLocation().getServingWlanId() != accessPointLocation.getServingWlanId())
				delay += (SimSettings.getInstance().getInternalLanDelay() * 2);
		}

		return delay;
	}

	/**
	 * Returns the maximum number of clients observed at any single location.
	 * 
	 * <p>This method provides debugging information about network congestion levels
	 * by tracking the peak number of concurrent mobile devices at any access point
	 * throughout the simulation. This metric helps validate congestion modeling
	 * assumptions and identify potential bottlenecks.</p>
	 * 
	 * @return maximum concurrent client count observed at any location
	 */
	public int getMaxNumOfClientsInPlace(){
		return maxNumOfClientsInPlace;
	}

	/**
	 * Counts the number of mobile devices currently at the specified location.
	 * 
	 * <p>This method iterates through all mobile devices and counts how many are
	 * currently located at the same access point as the specified location. The count
	 * is used to model network congestion in the M/M/1 queue calculations, where
	 * higher device counts lead to increased arrival rates and longer delays.</p>
	 * 
	 * <p>The method also tracks the maximum observed device count for debugging
	 * and validation purposes.</p>
	 * 
	 * @param deviceLocation the location to count devices at
	 * @param time the simulation time for location lookup
	 * @return number of devices currently at the specified location
	 */
	private int getDeviceCount(Location deviceLocation, double time){
		int deviceCount = 0;

		// Iterate through all mobile devices to count co-located devices
		for(int i = 0; i < numberOfMobileDevices; i++) {
			Location location = SimManager.getInstance().getMobilityModel().getLocation(i, time);
			if(location.equals(deviceLocation))
				deviceCount++;
		}

		// Update maximum client count for debugging and validation
		if(maxNumOfClientsInPlace < deviceCount)
			maxNumOfClientsInPlace = deviceCount;

		return deviceCount;
	}

	/**
	 * Calculates network delay using the M/M/1 queuing theory formula.
	 * 
	 * <p>This method implements the core M/M/1 queue delay calculation based on:
	 * <ul>
	 *   <li><b>λ (lambda):</b> Aggregate arrival rate = (device count) × (1/inter-arrival time)</li>
	 *   <li><b>μ (mu):</b> Service rate = bandwidth / average task size</li>
	 *   <li><b>Response Time:</b> W = 1/(μ - λ) + propagation delay</li>
	 * </ul></p>
	 * 
	 * <p>The formula assumes:
	 * <ul>
	 *   <li>Poisson arrival process (exponentially distributed inter-arrival times)</li>
	 *   <li>Exponentially distributed service times</li>
	 *   <li>Single server queue (one network channel)</li>
	 *   <li>FIFO scheduling discipline</li>
	 * </ul></p>
	 * 
	 * <p>If the system becomes unstable (λ ≥ μ) or delay exceeds 5 seconds,
	 * the method returns -1 to indicate transmission failure.</p>
	 * 
	 * @param propagationDelay physical propagation delay in seconds
	 * @param bandwidth available bandwidth in Kbps
	 * @param PoissonMean mean inter-arrival time for individual devices in seconds
	 * @param avgTaskSize average task size in KB
	 * @param deviceCount number of competing devices at the same location
	 * @return total delay in seconds, or -1 if system is overloaded
	 */
	private double calculateMM1(double propagationDelay, int bandwidth /*Kbps*/, double PoissonMean, double avgTaskSize /*KB*/, int deviceCount){
		double Bps = 0, mu = 0, lamda = 0;

		// Convert task size from KB to bytes
		avgTaskSize = avgTaskSize * (double)1000;

		// Convert bandwidth from Kbps to bytes per second
		Bps = bandwidth * (double)1000 / (double)8;
		
		// Calculate arrival rate: λ = device_count / mean_inter_arrival_time
		lamda = ((double)1 / (double)PoissonMean);
		
		// Calculate service rate: μ = bandwidth / task_size
		mu = Bps / avgTaskSize;
		
		// Apply M/M/1 formula: W = 1/(μ - λ×N) where N is device count
		double result = (double)1 / (mu - lamda * (double)deviceCount);

		// Add physical propagation delay
		result += propagationDelay;

		// Return -1 if delay is excessive (indicates network overload)
		return (result > 5) ? -1 : result;
	}

	/**
	 * Calculates WLAN download delay for result transmission to mobile device.
	 * 
	 * <p>Models the WiFi access point to mobile device delay using M/M/1 queuing.
	 * Uses task output size and WLAN bandwidth with no propagation delay
	 * (assumes negligible distance within WiFi coverage area).</p>
	 * 
	 * @param accessPointLocation location of the serving WiFi access point
	 * @param time current simulation time for device counting
	 * @return WLAN download delay in seconds
	 */
	private double getWlanDownloadDelay(Location accessPointLocation, double time) {
		return calculateMM1(0,
				SimSettings.getInstance().getWlanBandwidth(),
				WlanPoissonMean,
				avgTaskOutputSize,
				getDeviceCount(accessPointLocation, time));
	}

	/**
	 * Calculates WLAN upload delay for task data transmission from mobile device.
	 * 
	 * <p>Models the mobile device to WiFi access point delay using M/M/1 queuing.
	 * Uses task input size and WLAN bandwidth with no propagation delay.</p>
	 * 
	 * @param accessPointLocation location of the serving WiFi access point
	 * @param time current simulation time for device counting
	 * @return WLAN upload delay in seconds
	 */
	private double getWlanUploadDelay(Location accessPointLocation, double time) {
		return calculateMM1(0,
				SimSettings.getInstance().getWlanBandwidth(),
				WlanPoissonMean,
				avgTaskInputSize,
				getDeviceCount(accessPointLocation, time));
	}

	/**
	 * Calculates WAN download delay for result transmission from cloud datacenter.
	 * 
	 * <p>Models the wide area network delay from cloud to edge infrastructure
	 * using M/M/1 queuing. Includes WAN propagation delay and uses WAN bandwidth
	 * characteristics with task output size.</p>
	 * 
	 * @param accessPointLocation location for device count (affects congestion)
	 * @param time current simulation time for device counting
	 * @return WAN download delay in seconds
	 */
	private double getWanDownloadDelay(Location accessPointLocation, double time) {
		return calculateMM1(SimSettings.getInstance().getWanPropagationDelay(),
				SimSettings.getInstance().getWanBandwidth(),
				WanPoissonMean,
				avgTaskOutputSize,
				getDeviceCount(accessPointLocation, time));
	}

	/**
	 * Calculates WAN upload delay for task data transmission to cloud datacenter.
	 * 
	 * <p>Models the wide area network delay from edge to cloud infrastructure
	 * using M/M/1 queuing. Includes WAN propagation delay and uses WAN bandwidth
	 * characteristics with task input size.</p>
	 * 
	 * @param accessPointLocation location for device count (affects congestion)
	 * @param time current simulation time for device counting
	 * @return WAN upload delay in seconds
	 */
	private double getWanUploadDelay(Location accessPointLocation, double time) {
		return calculateMM1(SimSettings.getInstance().getWanPropagationDelay(),
				SimSettings.getInstance().getWanBandwidth(),
				WanPoissonMean,
				avgTaskInputSize,
				getDeviceCount(accessPointLocation, time));
	}

	/**
	 * Notification callback for upload operation start (not implemented in M/M/1 model).
	 * 
	 * <p>The M/M/1 queue model calculates delays instantaneously based on current
	 * network conditions and does not maintain state about ongoing operations.
	 * This method is provided for interface compliance but does not perform any
	 * operations in this implementation.</p>
	 * 
	 * @param accessPointLocation location of the access point handling the upload
	 * @param destDeviceId destination device ID for the upload
	 */
	@Override
	public void uploadStarted(Location accessPointLocation, int destDeviceId) {
		// M/M/1 model does not track individual operation state
		// Delay calculations are performed instantaneously based on current conditions
	}

	/**
	 * Notification callback for upload operation completion (not implemented in M/M/1 model).
	 * 
	 * <p>The M/M/1 queue model does not maintain operation state, so this notification
	 * does not trigger any specific actions in this implementation.</p>
	 * 
	 * @param accessPointLocation location of the access point that handled the upload
	 * @param destDeviceId destination device ID for the completed upload
	 */
	@Override
	public void uploadFinished(Location accessPointLocation, int destDeviceId) {
		// M/M/1 model does not track individual operation state
	}

	/**
	 * Notification callback for download operation start (not implemented in M/M/1 model).
	 * 
	 * <p>This method is called when a download operation begins, but the M/M/1 model
	 * does not use this information for delay calculations.</p>
	 * 
	 * @param accessPointLocation location of the access point handling the download
	 * @param sourceDeviceId source device ID for the download
	 */
	@Override
	public void downloadStarted(Location accessPointLocation, int sourceDeviceId) {
		// M/M/1 model does not track individual operation state
	}

	/**
	 * Notification callback for download operation completion (not implemented in M/M/1 model).
	 * 
	 * <p>This method is called when a download operation completes, but the M/M/1 model
	 * does not use this information for state management.</p>
	 * 
	 * @param accessPointLocation location of the access point that handled the download
	 * @param sourceDeviceId source device ID for the completed download
	 */
	@Override
	public void downloadFinished(Location accessPointLocation, int sourceDeviceId) {
		// M/M/1 model does not track individual operation state
	}
}
