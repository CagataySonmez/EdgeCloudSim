/*
 * Title:        EdgeCloudSim - Network Model
 * 
 * Description: 
 * SampleNetworkModel uses
 * -> the result of an empirical study for the WLAN and WAN delays
 * The experimental network model is developed
 * by taking measurements from the real life deployments.
 *   
 * -> MMPP/MMPP/1 queue model for MAN delay
 * MAN delay is observed via a single server queue model with
 * Markov-modulated Poisson process (MMPP) arrivals.
 *   
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.tutorial1;

import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;

public class SampleNetworkModel extends NetworkModel {
	// Overview:
	// - Maintains concurrent user counts per WLAN (edge AP) and WAN link.
	// - Uses empirical per-user aggregate throughput tables (Kbps) to derive transfer delay.
	// - MAN (edge-to-edge relay) modeled with constant internal LAN delay (from settings).
	// - Upload and download assumed symmetric for WAN and WLAN in this simplified model.
	// Delay formula: delay_seconds = (data_size_KB * 8 bits_per_byte) / effective_throughput_Kbps
	// WLAN adjustment: divides by (throughput * 3) to approximate 802.11ac vs 802.11n speed gain.

	public static enum NETWORK_TYPE {WLAN, LAN};
	public static enum LINK_TYPE {DOWNLOAD, UPLOAD};

	@SuppressWarnings("unused")
	private int manClients; // active MAN relay transfers (edge<->edge); single shared queue abstraction
	private int[] wanClients;  // active WAN sessions per WLAN zone (indexed by servingWlanId)
	private int[] wlanClients; // active WLAN sessions per WLAN zone

	// Empirical per-user WLAN aggregate throughput (Kbps).
	// Index == number of concurrent WLAN clients. Used to approximate contention impact.
	// Note: duplicate comment "15 Clients" in source preserved; second value actually for 16? Kept intact for compatibility.
	public static final double[] experimentalWlanDelay = {
		/*1 Client*/ 88040.279 /*(Kbps)*/,
		/*2 Clients*/ 45150.982 /*(Kbps)*/,
		/*3 Clients*/ 30303.641 /*(Kbps)*/,
		/*4 Clients*/ 27617.211 /*(Kbps)*/,
		/*5 Clients*/ 24868.616 /*(Kbps)*/,
		/*6 Clients*/ 22242.296 /*(Kbps)*/,
		/*7 Clients*/ 20524.064 /*(Kbps)*/,
		/*8 Clients*/ 18744.889 /*(Kbps)*/,
		/*9 Clients*/ 17058.827 /*(Kbps)*/,
		/*10 Clients*/ 15690.455 /*(Kbps)*/,
		/*11 Clients*/ 14127.744 /*(Kbps)*/,
		/*12 Clients*/ 13522.408 /*(Kbps)*/,
		/*13 Clients*/ 13177.631 /*(Kbps)*/,
		/*14 Clients*/ 12811.330 /*(Kbps)*/,
		/*15 Clients*/ 12584.387 /*(Kbps)*/,
		/*15 Clients*/ 12135.161 /*(Kbps)*/,
		/*16 Clients*/ 11705.638 /*(Kbps)*/,
		/*17 Clients*/ 11276.116 /*(Kbps)*/,
		/*18 Clients*/ 10846.594 /*(Kbps)*/,
		/*19 Clients*/ 10417.071 /*(Kbps)*/,
		/*20 Clients*/ 9987.549 /*(Kbps)*/,
		/*21 Clients*/ 9367.587 /*(Kbps)*/,
		/*22 Clients*/ 8747.625 /*(Kbps)*/,
		/*23 Clients*/ 8127.663 /*(Kbps)*/,
		/*24 Clients*/ 7907.701 /*(Kbps)*/,
		/*25 Clients*/ 7887.739 /*(Kbps)*/,
		/*26 Clients*/ 7690.831 /*(Kbps)*/,
		/*27 Clients*/ 7393.922 /*(Kbps)*/,
		/*28 Clients*/ 7297.014 /*(Kbps)*/,
		/*29 Clients*/ 7100.106 /*(Kbps)*/,
		/*30 Clients*/ 6903.197 /*(Kbps)*/,
		/*31 Clients*/ 6701.986 /*(Kbps)*/,
		/*32 Clients*/ 6500.776 /*(Kbps)*/,
		/*33 Clients*/ 6399.565 /*(Kbps)*/,
		/*34 Clients*/ 6098.354 /*(Kbps)*/,
		/*35 Clients*/ 5897.143 /*(Kbps)*/,
		/*36 Clients*/ 5552.127 /*(Kbps)*/,
		/*37 Clients*/ 5207.111 /*(Kbps)*/,
		/*38 Clients*/ 4862.096 /*(Kbps)*/,
		/*39 Clients*/ 4517.080 /*(Kbps)*/,
		/*40 Clients*/ 4172.064 /*(Kbps)*/,
		/*41 Clients*/ 4092.922 /*(Kbps)*/,
		/*42 Clients*/ 4013.781 /*(Kbps)*/,
		/*43 Clients*/ 3934.639 /*(Kbps)*/,
		/*44 Clients*/ 3855.498 /*(Kbps)*/,
		/*45 Clients*/ 3776.356 /*(Kbps)*/,
		/*46 Clients*/ 3697.215 /*(Kbps)*/,
		/*47 Clients*/ 3618.073 /*(Kbps)*/,
		/*48 Clients*/ 3538.932 /*(Kbps)*/,
		/*49 Clients*/ 3459.790 /*(Kbps)*/,
		/*50 Clients*/ 3380.649 /*(Kbps)*/,
		/*51 Clients*/ 3274.611 /*(Kbps)*/,
		/*52 Clients*/ 3168.573 /*(Kbps)*/,
		/*53 Clients*/ 3062.536 /*(Kbps)*/,
		/*54 Clients*/ 2956.498 /*(Kbps)*/,
		/*55 Clients*/ 2850.461 /*(Kbps)*/,
		/*56 Clients*/ 2744.423 /*(Kbps)*/,
		/*57 Clients*/ 2638.386 /*(Kbps)*/,
		/*58 Clients*/ 2532.348 /*(Kbps)*/,
		/*59 Clients*/ 2426.310 /*(Kbps)*/,
		/*60 Clients*/ 2320.273 /*(Kbps)*/,
		/*61 Clients*/ 2283.828 /*(Kbps)*/,
		/*62 Clients*/ 2247.383 /*(Kbps)*/,
		/*63 Clients*/ 2210.939 /*(Kbps)*/,
		/*64 Clients*/ 2174.494 /*(Kbps)*/,
		/*65 Clients*/ 2138.049 /*(Kbps)*/,
		/*66 Clients*/ 2101.604 /*(Kbps)*/,
		/*67 Clients*/ 2065.160 /*(Kbps)*/,
		/*68 Clients*/ 2028.715 /*(Kbps)*/,
		/*69 Clients*/ 1992.270 /*(Kbps)*/,
		/*70 Clients*/ 1955.825 /*(Kbps)*/,
		/*71 Clients*/ 1946.788 /*(Kbps)*/,
		/*72 Clients*/ 1937.751 /*(Kbps)*/,
		/*73 Clients*/ 1928.714 /*(Kbps)*/,
		/*74 Clients*/ 1919.677 /*(Kbps)*/,
		/*75 Clients*/ 1910.640 /*(Kbps)*/,
		/*76 Clients*/ 1901.603 /*(Kbps)*/,
		/*77 Clients*/ 1892.566 /*(Kbps)*/,
		/*78 Clients*/ 1883.529 /*(Kbps)*/,
		/*79 Clients*/ 1874.492 /*(Kbps)*/,
		/*80 Clients*/ 1865.455 /*(Kbps)*/,
		/*81 Clients*/ 1833.185 /*(Kbps)*/,
		/*82 Clients*/ 1800.915 /*(Kbps)*/,
		/*83 Clients*/ 1768.645 /*(Kbps)*/,
		/*84 Clients*/ 1736.375 /*(Kbps)*/,
		/*85 Clients*/ 1704.106 /*(Kbps)*/,
		/*86 Clients*/ 1671.836 /*(Kbps)*/,
		/*87 Clients*/ 1639.566 /*(Kbps)*/,
		/*88 Clients*/ 1607.296 /*(Kbps)*/,
		/*89 Clients*/ 1575.026 /*(Kbps)*/,
		/*90 Clients*/ 1542.756 /*(Kbps)*/,
		/*91 Clients*/ 1538.544 /*(Kbps)*/,
		/*92 Clients*/ 1534.331 /*(Kbps)*/,
		/*93 Clients*/ 1530.119 /*(Kbps)*/,
		/*94 Clients*/ 1525.906 /*(Kbps)*/,
		/*95 Clients*/ 1521.694 /*(Kbps)*/,
		/*96 Clients*/ 1517.481 /*(Kbps)*/,
		/*97 Clients*/ 1513.269 /*(Kbps)*/,
		/*98 Clients*/ 1509.056 /*(Kbps)*/,
		/*99 Clients*/ 1504.844 /*(Kbps)*/,
		/*100 Clients*/ 1500.631 /*(Kbps)*/
	};
	
	// Empirical per-user WAN throughput (Kbps) for upstream/downstream symmetry assumption.
	public static final double[] experimentalWanDelay = {
		/*1 Client*/ 20703.973 /*(Kbps)*/,
		/*2 Clients*/ 12023.957 /*(Kbps)*/,
		/*3 Clients*/ 9887.785 /*(Kbps)*/,
		/*4 Clients*/ 8915.775 /*(Kbps)*/,
		/*5 Clients*/ 8259.277 /*(Kbps)*/,
		/*6 Clients*/ 7560.574 /*(Kbps)*/,
		/*7 Clients*/ 7262.140 /*(Kbps)*/,
		/*8 Clients*/ 7155.361 /*(Kbps)*/,
		/*9 Clients*/ 7041.153 /*(Kbps)*/,
		/*10 Clients*/ 6994.595 /*(Kbps)*/,
		/*11 Clients*/ 6653.232 /*(Kbps)*/,
		/*12 Clients*/ 6111.868 /*(Kbps)*/,
		/*13 Clients*/ 5570.505 /*(Kbps)*/,
		/*14 Clients*/ 5029.142 /*(Kbps)*/,
		/*15 Clients*/ 4487.779 /*(Kbps)*/,
		/*16 Clients*/ 3899.729 /*(Kbps)*/,
		/*17 Clients*/ 3311.680 /*(Kbps)*/,
		/*18 Clients*/ 2723.631 /*(Kbps)*/,
		/*19 Clients*/ 2135.582 /*(Kbps)*/,
		/*20 Clients*/ 1547.533 /*(Kbps)*/,
		/*21 Clients*/ 1500.252 /*(Kbps)*/,
		/*22 Clients*/ 1452.972 /*(Kbps)*/,
		/*23 Clients*/ 1405.692 /*(Kbps)*/,
		/*24 Clients*/ 1358.411 /*(Kbps)*/,
		/*25 Clients*/ 1311.131 /*(Kbps)*/
	};
	
	public SampleNetworkModel(int _numberOfMobileDevices, String _simScenario) {
		super(_numberOfMobileDevices, _simScenario);
	}

	@Override
	public void initialize() {
		// One WAN and WLAN access point per edge datacenter (servingWlanId acts as index).
		// Arrays store current concurrent session counts used to pick throughput entries.
		wanClients = new int[SimSettings.getInstance().getNumOfEdgeDatacenters()];  //we have one access point for each datacenter
		wlanClients = new int[SimSettings.getInstance().getNumOfEdgeDatacenters()];  //we have one access point for each datacenter
	}

    /**
	 * Upload delay from mobile to target device.
	 * Branches:
	 *  - MAN relay (edge->edge): constant internal LAN delay
	 *  - Cloud: WAN empirical table
	 *  - Edge: WLAN empirical table
	 */
	@Override
	public double getUploadDelay(int sourceDeviceId, int destDeviceId, Task task) {
		double delay = 0;
		
		//special case for man communication
		if(sourceDeviceId == destDeviceId && sourceDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID){
			// Special MAN case: same generic edge ID used as placeholder (relay hop)
			return delay = getManUploadDelay();
		}
		
		Location accessPointLocation = SimManager.getInstance().getMobilityModel().getLocation(sourceDeviceId,CloudSim.clock());

		//mobile device to cloud server
		if(destDeviceId == SimSettings.CLOUD_DATACENTER_ID){
			delay = getWanUploadDelay(accessPointLocation, task.getCloudletFileSize());
		}
		//mobile device to edge device (wifi access point)
		else if (destDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID) {
			// Fetch current WLAN zone of mobile for throughput lookup
			delay = getWlanUploadDelay(accessPointLocation, task.getCloudletFileSize());
		}
		
		return delay;
	}

    /**
	 * Download delay to mobile from source device.
	 * Symmetric logic to upload with MAN shortcut.
	 */
	@Override
	public double getDownloadDelay(int sourceDeviceId, int destDeviceId, Task task) {
		double delay = 0;
		
		//special case for man communication
		if(sourceDeviceId == destDeviceId && sourceDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID){
			// Special MAN case
			return delay = getManDownloadDelay();
		}
		
		Location accessPointLocation = SimManager.getInstance().getMobilityModel().getLocation(destDeviceId,CloudSim.clock());
		
		//cloud server to mobile device
		if(sourceDeviceId == SimSettings.CLOUD_DATACENTER_ID){
			delay = getWanDownloadDelay(accessPointLocation, task.getCloudletOutputSize());
		}
		//edge device (wifi access point) to mobile device
		else{
			// Destination (mobile) current WLAN determines contention level
			delay = getWlanDownloadDelay(accessPointLocation, task.getCloudletOutputSize());
		}
		
		return delay;
	}

	@Override
	public void uploadStarted(Location accessPointLocation, int destDeviceId) {
		// Increment concurrent session counters to reflect resource occupation.
		// Consistency: every started must have matching finished for accurate contention.
		if(destDeviceId == SimSettings.CLOUD_DATACENTER_ID)
			wanClients[accessPointLocation.getServingWlanId()]++;
		else if (destDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID)
			wlanClients[accessPointLocation.getServingWlanId()]++;
		else if (destDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID+1)
			manClients++;
		else {
			SimLogger.printLine("Error - unknown device id in uploadStarted(). Terminating simulation...");
			System.exit(0);
		}
	}

	@Override
	public void uploadFinished(Location accessPointLocation, int destDeviceId) {
		// Decrement counters; negative values would indicate logic errors (not checked here).
		if(destDeviceId == SimSettings.CLOUD_DATACENTER_ID)
			wanClients[accessPointLocation.getServingWlanId()]--;
		else if (destDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID)
			wlanClients[accessPointLocation.getServingWlanId()]--;
		else if (destDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID+1)
			manClients--;
		else {
			SimLogger.printLine("Error - unknown device id in uploadFinished(). Terminating simulation...");
			System.exit(0);
		}
	}

	@Override
	public void downloadStarted(Location accessPointLocation, int sourceDeviceId) {
		if(sourceDeviceId == SimSettings.CLOUD_DATACENTER_ID)
			wanClients[accessPointLocation.getServingWlanId()]++;
		else if(sourceDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID)
			wlanClients[accessPointLocation.getServingWlanId()]++;
		else if(sourceDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID+1)
			manClients++;
		else {
			SimLogger.printLine("Error - unknown device id in downloadStarted(). Terminating simulation...");
			System.exit(0);
		}
	}

	@Override
	public void downloadFinished(Location accessPointLocation, int sourceDeviceId) {
		if(sourceDeviceId == SimSettings.CLOUD_DATACENTER_ID)
			wanClients[accessPointLocation.getServingWlanId()]--;
		else if(sourceDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID)
			wlanClients[accessPointLocation.getServingWlanId()]--;
		else if(sourceDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID+1)
			manClients--;
		else {
			SimLogger.printLine("Error - unknown device id in downloadFinished(). Terminating simulation...");
			System.exit(0);
		}
	}

	private double getWlanDownloadDelay(Location accessPointLocation, double dataSize) {
		// Convert KB -> Kb, then divide by empirical throughput (adjusted x3 for 802.11ac uplift).
		// If #users exceeds table length => result stays 0 (interpreted as bandwidth failure upstream).
		int numOfWlanUser = wlanClients[accessPointLocation.getServingWlanId()];
		double taskSizeInKb = dataSize * (double)8; //KB to Kb
		double result=0;
		
		if(numOfWlanUser < experimentalWlanDelay.length)
			result = taskSizeInKb /*Kb*/ / (experimentalWlanDelay[numOfWlanUser] * (double) 3 ) /*Kbps*/; //802.11ac is around 3 times faster than 802.11n

		//System.out.println("--> " + numOfWlanUser + " user, " + taskSizeInKb + " KB, " +result + " sec");
		return result;
	}
	
	// WLAN upload mirrors download (symmetric assumption).
	private double getWlanUploadDelay(Location accessPointLocation, double dataSize) {
		return getWlanDownloadDelay(accessPointLocation, dataSize);
	}
	
	private double getWanDownloadDelay(Location accessPointLocation, double dataSize) {
		// Similar to WLAN formula without 802.11ac multiplier (table already calibrated).
		int numOfWanUser = wanClients[accessPointLocation.getServingWlanId()];
		double taskSizeInKb = dataSize * (double)8; //KB to Kb
		double result=0;
		
		if(numOfWanUser < experimentalWanDelay.length)
			result = taskSizeInKb /*Kb*/ / (experimentalWanDelay[numOfWanUser]) /*Kbps*/;
		
		//System.out.println("--> " + numOfWanUser + " user, " + taskSizeInKb + " KB, " +result + " sec");
		
		return result;
	}
	
	// WAN upload mirrors download (symmetric assumption).
	private double getWanUploadDelay(Location accessPointLocation, double dataSize) {
		return getWanDownloadDelay(accessPointLocation, dataSize);
	}
	
	
	private double getManDownloadDelay() {
		// Constant latency model for internal MAN (could be replaced by queue model).
		return SimSettings.getInstance().getInternalLanDelay();
	}
	
	private double getManUploadDelay() {
		return SimSettings.getInstance().getInternalLanDelay();
	}
}
