/*
 * Title:        EdgeCloudSim - Experimental Network Model implementation
 * 
 * Description: 
 * FuzzyExperimentalNetworkModel implements a network model for WLAN and WAN communication
 * using empirical data.
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app4;

import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;

/**
 * Fuzzy experimental network model implementing empirical WLAN performance data.
 * 
 * <p>This network model uses real-world experimental measurements of WLAN performance
 * to provide realistic network delay calculations. Unlike theoretical models, it incorporates
 * empirical data collected from actual wireless network deployments, capturing the
 * complex behavior of IEEE 802.11 networks under varying load conditions.</p>
 * 
 * <p><b>Key Features:</b>
 * <ul>
 *   <li><b>Empirical Data:</b> Uses measured WLAN throughput data for different client counts</li>
 *   <li><b>Load-dependent Performance:</b> Throughput degradation with increasing client density</li>
 *   <li><b>MAN/WAN Integration:</b> Combines experimental WLAN data with theoretical WAN models</li>
 *   <li><b>MMPP/M/1 Queuing:</b> Advanced queuing model for bursty traffic patterns</li>
 * </ul></p>
 * 
 * <p><b>Experimental WLAN Model:</b>
 * The model uses pre-measured throughput values for client counts from 1 to 34+,
 * showing realistic performance degradation as network load increases. This captures
 * the effects of contention, collisions, and protocol overhead in real Wi-Fi networks.</p>
 * 
 * <p><b>Application Context:</b>
 * This model is particularly suitable for vehicular edge computing scenarios where
 * accurate WLAN performance modeling is critical for realistic simulation results.</p>
 * 
 * @see NetworkModel
 * @see edu.boun.edgecloudsim.applications.sample_app4
 */
public class FuzzyExperimentalNetworkModel extends NetworkModel {
	/**
	 * Enumeration of network types in the experimental model.
	 */
	public static enum NETWORK_TYPE {WLAN, LAN};
	
	/**
	 * Enumeration of communication directions for link characterization.
	 */
	public static enum LINK_TYPE {DOWNLOAD, UPLOAD};
	
	/** Metropolitan Area Network bandwidth in Kbps */
	public static double MAN_BW = 1300*1024;

	/** Number of clients using MAN connection (reserved for future use) */
	@SuppressWarnings("unused")
	private int manClients;
	
	/** Array tracking WAN client counts per access point */
	private int[] wanClients;
	
	/** Array tracking WLAN client counts per access point */
	private int[] wlanClients;
	
	/** Last timestamp when M/M/1 queue statistics were updated */
	private double lastMM1QueeuUpdateTime;
	
	/** Mean inter-arrival time for MAN download tasks in seconds */
	private double ManPoissonMeanForDownload;
	
	/** Mean inter-arrival time for MAN upload tasks in seconds */
	private double ManPoissonMeanForUpload;

	/** Average input data size for MAN tasks in bytes */
	private double avgManTaskInputSize;
	
	/** Average output data size for MAN tasks in bytes */
	private double avgManTaskOutputSize;

	/** 
	 * Accumulated input data size for MMPP/M/1 queue modeling.
	 * Records statistics during MM1_QUEUE_MODEL_UPDATE_INTERVAL to simulate
	 * Markov Modulated Poisson Process over M/1 queue behavior.
	 */
	private double totalManTaskInputSize;
	
	/** Accumulated output data size for MMPP/M/1 queue modeling */
	private double totalManTaskOutputSize;
	
	/** Number of MAN download tasks in current measurement interval */
	private double numOfManTaskForDownload;
	
	/** Number of MAN upload tasks in current measurement interval */
	private double numOfManTaskForUpload;
	
	/**
	 * Empirical WLAN throughput measurements based on client count.
	 * 
	 * <p>This array contains real-world experimental data showing WLAN throughput
	 * (in Kbps) for different numbers of concurrent clients. The measurements
	 * capture the performance degradation effect as network contention increases
	 * with more active devices.</p>
	 * 
	 * <p><b>Key Observations:</b>
	 * <ul>
	 *   <li>Single client achieves ~88 Mbps (theoretical maximum)</li>
	 *   <li>Dramatic throughput reduction with 2-4 clients due to contention</li>
	 *   <li>Gradual degradation continues as client count increases</li>
	 *   <li>Stabilizes around 6-7 Mbps with 30+ clients</li>
	 * </ul></p>
	 * 
	 * <p>Data reflects realistic IEEE 802.11 behavior including protocol overhead,
	 * collision avoidance delays, and medium access contention effects.</p>
	 */
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
	
	/**
	 * Constructor initializes the FuzzyExperimentalNetworkModel with mobile device count and simulation scenario.
	 * This network model uses empirical WLAN performance data combined with analytical models to simulate
	 * realistic network delays based on user load and network technology.
	 * 
	 * @param _numberOfMobileDevices Total number of mobile devices in the simulation
	 * @param _simScenario Simulation scenario identifier for configuration purposes
	 */
	public FuzzyExperimentalNetworkModel(int _numberOfMobileDevices, String _simScenario) {
		super(_numberOfMobileDevices, _simScenario);
	}

	/**
	 * Initializes the experimental network model by setting up client tracking arrays
	 * and calculating MAN/WAN Poisson parameters based on task configuration.
	 * This method analyzes the task lookup table to determine realistic traffic patterns
	 * and initializes statistical models for network delay calculation.
	 */
	@Override
	public void initialize() {
		wanClients = new int[SimSettings.getInstance().getNumOfEdgeDatacenters()];  //we have one access point for each datacenter
		wlanClients = new int[SimSettings.getInstance().getNumOfEdgeDatacenters()];  //we have one access point for each datacenter

		int numOfApp = SimSettings.getInstance().getTaskLookUpTable().length;
		SimSettings SS = SimSettings.getInstance();
		for(int taskIndex=0; taskIndex<numOfApp; taskIndex++) {
			if(SS.getTaskLookUpTable()[taskIndex][0] == 0) {
				SimLogger.printLine("Usage percentage of task " + taskIndex + " is 0! Terminating simulation...");
				System.exit(0);
			}
			else{
				double weight = SS.getTaskLookUpTable()[taskIndex][0]/(double)100;
				
				//assume half of the tasks use the MAN at the beginning
				ManPoissonMeanForDownload += ((SS.getTaskLookUpTable()[taskIndex][2])*weight) * 4;
				ManPoissonMeanForUpload = ManPoissonMeanForDownload;
				
				avgManTaskInputSize += SS.getTaskLookUpTable()[taskIndex][5]*weight;
				avgManTaskOutputSize += SS.getTaskLookUpTable()[taskIndex][6]*weight;
			}
		}

		ManPoissonMeanForDownload = ManPoissonMeanForDownload/numOfApp;
		ManPoissonMeanForUpload = ManPoissonMeanForUpload/numOfApp;
		avgManTaskInputSize = avgManTaskInputSize/numOfApp;
		avgManTaskOutputSize = avgManTaskOutputSize/numOfApp;
		
		lastMM1QueeuUpdateTime = SimSettings.CLIENT_ACTIVITY_START_TIME;
		totalManTaskOutputSize = 0;
		numOfManTaskForDownload = 0;
		totalManTaskInputSize = 0;
		numOfManTaskForUpload = 0;
	}

	/**
	 * Calculates upload delay from source to destination device using experimental data.
	 * This method differentiates between MAN, WAN, and WLAN communications and applies
	 * appropriate delay models based on empirical measurements and analytical models.
	 * 
	 * Note: Source device is always mobile device in our simulation scenarios!
	 * 
	 * @param sourceDeviceId ID of the source device (mobile device)
	 * @param destDeviceId ID of the destination device (cloud, edge, or MAN)
	 * @param task The task being uploaded containing file size information
	 * @return Upload delay in seconds based on network type and current load
	 */
	@Override
	public double getUploadDelay(int sourceDeviceId, int destDeviceId, Task task) {
		double delay = 0;
		
		//special case for man communication
		if(sourceDeviceId == destDeviceId && sourceDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID){
			return delay = getManUploadDelay();
		}
		
		Location accessPointLocation = SimManager.getInstance().getMobilityModel().getLocation(sourceDeviceId,CloudSim.clock());

		//mobile device to cloud server
		if(destDeviceId == SimSettings.CLOUD_DATACENTER_ID){
			delay = getWanUploadDelay(accessPointLocation, task.getCloudletFileSize());
		}
		//mobile device to edge device (wifi access point)
		else if (destDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID) {
			delay = getWlanUploadDelay(accessPointLocation, task.getCloudletFileSize());
		}
		
		return delay;
	}

	/**
	 * Calculates download delay from source to destination device using experimental data.
	 * This method handles cloud-to-mobile and edge-to-mobile communications with
	 * realistic delay models based on empirical WLAN performance measurements.
	 * 
	 * Note: Destination device is always mobile device in our simulation scenarios!
	 * 
	 * @param sourceDeviceId ID of the source device (cloud, edge, or MAN)
	 * @param destDeviceId ID of the destination device (mobile device)
	 * @param task The task being downloaded containing output size information
	 * @return Download delay in seconds based on network type and current load
	 */
	@Override
	public double getDownloadDelay(int sourceDeviceId, int destDeviceId, Task task) {
		double delay = 0;
		
		//special case for man communication
		if(sourceDeviceId == destDeviceId && sourceDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID){
			return delay = getManDownloadDelay();
		}
		
		Location accessPointLocation = SimManager.getInstance().getMobilityModel().getLocation(destDeviceId,CloudSim.clock());
		
		//cloud server to mobile device
		if(sourceDeviceId == SimSettings.CLOUD_DATACENTER_ID){
			delay = getWanDownloadDelay(accessPointLocation, task.getCloudletOutputSize());
		}
		//edge device (wifi access point) to mobile device
		else{
			delay = getWlanDownloadDelay(accessPointLocation, task.getCloudletOutputSize());
		}
		
		return delay;
	}

	/**
	 * Tracks the start of an upload operation by incrementing the appropriate client counter.
	 * This method maintains real-time statistics of active connections for accurate
	 * delay calculation based on current network load.
	 * 
	 * @param accessPointLocation Location of the access point handling the connection
	 * @param destDeviceId ID of the destination device (cloud, edge, or MAN)
	 */
	@Override
	public void uploadStarted(Location accessPointLocation, int destDeviceId) {
		if(destDeviceId == SimSettings.CLOUD_DATACENTER_ID)
			wanClients[accessPointLocation.getServingWlanId()]++;
		else if (destDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID)
			wlanClients[accessPointLocation.getServingWlanId()]++;
		else if (destDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID+1)
			manClients++;
		else {
			SimLogger.printLine("Error - unknown device id in FuzzyExperimentalNetworkModel.uploadStarted(. Terminating simulation...");
			System.exit(0);
		}
	}

	/**
	 * Tracks the completion of an upload operation by decrementing the appropriate client counter.
	 * This method updates real-time network load statistics to maintain accurate
	 * delay calculations for subsequent operations.
	 * 
	 * @param accessPointLocation Location of the access point that handled the connection
	 * @param destDeviceId ID of the destination device (cloud, edge, or MAN)
	 */
	@Override
	public void uploadFinished(Location accessPointLocation, int destDeviceId) {
		if(destDeviceId == SimSettings.CLOUD_DATACENTER_ID)
			wanClients[accessPointLocation.getServingWlanId()]--;
		else if (destDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID)
			wlanClients[accessPointLocation.getServingWlanId()]--;
		else if (destDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID+1)
			manClients--;
		else {
			SimLogger.printLine("Error - unknown device id in FuzzyExperimentalNetworkModel.uploadFinished(. Terminating simulation...");
			System.exit(0);
		}
	}

	/**
	 * Tracks the start of a download operation by incrementing the appropriate client counter.
	 * This method maintains accurate network load statistics for realistic delay modeling
	 * based on current connection density at each access point.
	 * 
	 * @param accessPointLocation Location of the access point handling the connection
	 * @param sourceDeviceId ID of the source device (cloud, edge, or MAN)
	 */
	@Override
	public void downloadStarted(Location accessPointLocation, int sourceDeviceId) {
		if(sourceDeviceId == SimSettings.CLOUD_DATACENTER_ID)
			wanClients[accessPointLocation.getServingWlanId()]++;
		else if(sourceDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID)
			wlanClients[accessPointLocation.getServingWlanId()]++;
		else if(sourceDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID+1)
			manClients++;
		else {
			SimLogger.printLine("Error - unknown device id in FuzzyExperimentalNetworkModel.downloadStarted(. Terminating simulation...");
			System.exit(0);
		}
	}

	/**
	 * Tracks the completion of a download operation by decrementing the appropriate client counter.
	 * This method ensures accurate real-time network load tracking for subsequent
	 * delay calculations and network performance modeling.
	 * 
	 * @param accessPointLocation Location of the access point that handled the connection
	 * @param sourceDeviceId ID of the source device (cloud, edge, or MAN)
	 */
	@Override
	public void downloadFinished(Location accessPointLocation, int sourceDeviceId) {
		if(sourceDeviceId == SimSettings.CLOUD_DATACENTER_ID)
			wanClients[accessPointLocation.getServingWlanId()]--;
		else if(sourceDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID)
			wlanClients[accessPointLocation.getServingWlanId()]--;
		else if(sourceDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID+1)
			manClients--;
		else {
			SimLogger.printLine("Error - unknown device id in FuzzyExperimentalNetworkModel.downloadFinished(. Terminating simulation...");
			System.exit(0);
		}
	}

	/**
	 * Calculates WLAN download delay using experimental throughput data.
	 * This method applies empirical measurements from real WLAN environments,
	 * adjusting for 802.11ac technology (3x faster than 802.11n baseline).
	 * 
	 * @param accessPointLocation Location of the WLAN access point
	 * @param dataSize Size of data to download in KB
	 * @return Download delay in seconds based on current user load and empirical data
	 */
	private double getWlanDownloadDelay(Location accessPointLocation, double dataSize) {
		int numOfWlanUser = wlanClients[accessPointLocation.getServingWlanId()];
		double taskSizeInKb = dataSize * (double)8; //KB to Kb
		double result=0;
		
		if(numOfWlanUser < experimentalWlanDelay.length)
			result = taskSizeInKb /*Kb*/ / (experimentalWlanDelay[numOfWlanUser] * (double) 3 ) /*Kbps*/; //802.11ac is around 3 times faster than 802.11n

		//System.out.println("--> " + numOfWlanUser + " user, " + taskSizeInKb + " KB, " +result + " sec");
		return result;
	}
	
	/**
	 * Calculates WLAN upload delay using symmetric delay model.
	 * In this experimental model, WLAN upload and download delays are considered symmetric,
	 * applying the same empirical throughput measurements for both directions.
	 * 
	 * @param accessPointLocation Location of the WLAN access point
	 * @param dataSize Size of data to upload in KB
	 * @return Upload delay in seconds (symmetric to download delay)
	 */
	private double getWlanUploadDelay(Location accessPointLocation, double dataSize) {
		return getWlanDownloadDelay(accessPointLocation, dataSize);
	}
	
	/**
	 * Calculates WAN download delay using experimental throughput data.
	 * This method applies empirical WAN performance measurements to model
	 * realistic Internet backbone delays based on concurrent user load.
	 * 
	 * @param accessPointLocation Location of the access point for WAN connection
	 * @param dataSize Size of data to download in KB
	 * @return Download delay in seconds based on WAN experimental data
	 */
	private double getWanDownloadDelay(Location accessPointLocation, double dataSize) {
		int numOfWanUser = wanClients[accessPointLocation.getServingWlanId()];
		double taskSizeInKb = dataSize * (double)8; //KB to Kb
		double result=0;
		
		if(numOfWanUser < experimentalWanDelay.length)
			result = taskSizeInKb /*Kb*/ / (experimentalWanDelay[numOfWanUser]) /*Kbps*/;
		
		//System.out.println("--> " + numOfWanUser + " user, " + taskSizeInKb + " KB, " +result + " sec");
		
		return result;
	}
	
	/**
	 * Calculates WAN upload delay using symmetric delay model.
	 * In this experimental model, WAN upload and download delays are considered symmetric,
	 * applying the same empirical throughput measurements for both directions.
	 * 
	 * @param accessPointLocation Location of the access point for WAN connection
	 * @param dataSize Size of data to upload in KB
	 * @return Upload delay in seconds (symmetric to download delay)
	 */
	private double getWanUploadDelay(Location accessPointLocation, double dataSize) {
		return getWanDownloadDelay(accessPointLocation, dataSize);
	}
	
	/**
	 * Calculates delay using M/M/1 queuing theory model.
	 * This method implements the classical M/M/1 queue formula: E[T] = 1/(μ - λ)
	 * where μ is service rate and λ is arrival rate, accounting for multiple devices.
	 * 
	 * @param propagationDelay Base propagation delay in seconds
	 * @param bandwidth Available bandwidth in Kbps
	 * @param PoissonMean Mean inter-arrival time for Poisson process
	 * @param avgTaskSize Average task size in KB
	 * @param deviceCount Number of devices sharing the network
	 * @return Total delay in seconds (queuing + propagation), capped at 15 seconds
	 */
	private double calculateMM1(double propagationDelay, double bandwidth /*Kbps*/, double PoissonMean, double avgTaskSize /*KB*/, int deviceCount){
		double mu=0, lamda=0;
		
		avgTaskSize = avgTaskSize * 8; //convert from KB to Kb

        lamda = ((double)1/(double)PoissonMean); //task per seconds
		mu = bandwidth /*Kbps*/ / avgTaskSize /*Kb*/; //task per seconds
		double result = (double)1 / (mu-lamda*(double)deviceCount);
		
		if(result < 0)
			return 0;
		
		result += propagationDelay;
		
		return (result > 15) ? 0 : result;
	}
	
	/**
	 * Calculates MAN (Metropolitan Area Network) download delay using M/M/1 queuing model.
	 * This method models intra-datacenter communication delays using analytical queuing theory
	 * with adaptive parameters based on actual traffic patterns and task statistics.
	 * 
	 * @return Download delay in seconds for MAN communication
	 */
	private double getManDownloadDelay() {
		double result = calculateMM1(SimSettings.getInstance().getInternalLanDelay(),
				MAN_BW,
				ManPoissonMeanForDownload,
				avgManTaskOutputSize,
				numberOfMobileDevices);
		
		totalManTaskOutputSize += avgManTaskOutputSize;
		numOfManTaskForDownload++;
		
		//System.out.println("--> " + SimManager.getInstance().getNumOfMobileDevice() + " user, " +result + " sec");
		
		return result;
	}
	
	/**
	 * Calculates MAN (Metropolitan Area Network) upload delay using M/M/1 queuing model.
	 * This method models intra-datacenter communication delays for upload operations
	 * using adaptive queuing parameters that evolve with simulation dynamics.
	 * 
	 * @return Upload delay in seconds for MAN communication
	 */
	private double getManUploadDelay() {
		double result = calculateMM1(SimSettings.getInstance().getInternalLanDelay(),
				MAN_BW,
				ManPoissonMeanForUpload,
				avgManTaskInputSize,
				numberOfMobileDevices);
		
		totalManTaskInputSize += avgManTaskInputSize;
		numOfManTaskForUpload++;

		//System.out.println(CloudSim.clock() + " -> " + SimManager.getInstance().getNumOfMobileDevice() + " user, " + result + " sec");
		
		return result;
	}
	
	/**
	 * Updates the M/M/1 queue model parameters based on recent traffic patterns.
	 * This method implements adaptive parameter estimation by analyzing actual task
	 * arrivals and sizes over time intervals, enabling realistic queue behavior
	 * that evolves with simulation dynamics.
	 * 
	 * The method recalculates:
	 * - Poisson arrival rates based on observed inter-arrival times
	 * - Average task sizes based on recent traffic patterns
	 * - Resets counters for the next measurement interval
	 */
	public void updateMM1QueeuModel(){
		double lastInterval = CloudSim.clock() - lastMM1QueeuUpdateTime;
		lastMM1QueeuUpdateTime = CloudSim.clock();
		
		if(numOfManTaskForDownload != 0){
			ManPoissonMeanForDownload = lastInterval / (numOfManTaskForDownload / (double)numberOfMobileDevices);
			avgManTaskOutputSize = totalManTaskOutputSize / numOfManTaskForDownload;
		}
		if(numOfManTaskForUpload != 0){
			ManPoissonMeanForUpload = lastInterval / (numOfManTaskForUpload / (double)numberOfMobileDevices);
			avgManTaskInputSize = totalManTaskInputSize / numOfManTaskForUpload;
		}
		
		totalManTaskOutputSize = 0;
		numOfManTaskForDownload = 0;
		totalManTaskInputSize = 0;
		numOfManTaskForUpload = 0;
	}
}