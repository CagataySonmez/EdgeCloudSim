/*
 * Title:        EdgeCloudSim - Vehicular Network Model Implementation
 * 
 * Description: 
 * VehicularNetworkModel implements advanced MMPP/M/1 (Markov Modulated Poisson Process/M/1)
 * queuing models specifically designed for vehicular edge computing environments. This model
 * provides realistic network delay calculations for multi-modal vehicular connectivity:
 * 
 * Supported Network Technologies:
 * - WLAN: Vehicle-to-Infrastructure (V2I) via Road Side Units (RSUs)
 * - MAN: Metropolitan Area Network for inter-RSU communication
 * - WAN: Wide Area Network for RSU-to-cloud connectivity
 * - GSM: Cellular network for direct vehicle-to-cloud communication
 * 
 * Key Features:
 * - MMPP/M/1 queuing theory for realistic traffic modeling
 * - Dynamic parameter adaptation based on real-time traffic patterns
 * - Vehicular mobility-aware delay calculations
 * - Multi-hop routing support for complex vehicular scenarios
 * - Adaptive bandwidth allocation and congestion management
 * - Statistical traffic analysis with Poisson process modeling
 * 
 * The model accounts for the unique characteristics of vehicular networks including
 * high mobility, variable connectivity, and heterogeneous network access technologies.
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app5;

import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.core.SimSettings.NETWORK_DELAY_TYPES;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;

/**
 * VehicularNetworkModel extends the base NetworkModel to provide sophisticated
 * network delay modeling specifically designed for vehicular edge computing scenarios.
 * This implementation uses MMPP/M/1 queuing theory to model realistic network behavior
 * across multiple vehicular communication technologies.
 * 
 * Network Technologies Supported:
 * - WLAN: V2I communication via RSUs with adaptive bandwidth allocation
 * - MAN: Inter-RSU communication for distributed edge computing
 * - WAN: RSU-to-cloud connectivity for remote processing
 * - GSM: Direct cellular connectivity for ubiquitous access
 * 
 * Key Features:
 * - MMPP (Markov Modulated Poisson Process) traffic modeling
 * - Dynamic parameter adaptation based on observed traffic patterns
 * - Real-time delay calculation with mobility awareness
 * - Statistical analysis for performance optimization
 * - Multi-modal network selection and handoff support
 */
public class VehicularNetworkModel extends NetworkModel {
	/** Maximum observed WLAN delay for performance analysis */
	public static double maxWlanDelay = 0;
	/** Maximum observed WAN delay for performance analysis */
	public static double maxWanDelay = 0;
	/** Maximum observed GSM delay for performance analysis */
	public static double maxGsmDelay = 0;

	/**
	 * MMPPWrapper implements MMPP/M/1 queue modeling for realistic vehicular traffic patterns.
	 * This inner class manages the statistical parameters and adaptive behavior required
	 * for accurate network delay calculations in dynamic vehicular environments.
	 * 
	 * The wrapper maintains both current and historical traffic statistics to enable
	 * adaptive parameter estimation and realistic queue behavior simulation.
	 */
	private class MMPPWrapper {
		/** Current Poisson arrival rate (tasks per second) */
		private double currentPoissonMean;
		/** Current average task size (KB) */
		private double currentTaskSize;

		/** Last successful Poisson rate for stability analysis */
		private double lastPoissonMean;
		/** Last successful task size for trend analysis */
		private double lastTaskSize;

		/** Number of tasks in current measurement window for MMPP modeling */
		private double numOfTasks;
		/** Total task size in current measurement window for average calculation */
		private double totalTaskSize;

		/**
		 * Constructor initializes MMPP wrapper with default values.
		 * Sets up the statistical tracking system for adaptive queue modeling
		 * in vehicular network environments.
		 */
		public MMPPWrapper() {
			currentPoissonMean = 0;
			currentTaskSize = 0;

			lastPoissonMean = 0;
			lastTaskSize = 0;

			numOfTasks = 0;
			totalTaskSize = 0;
		}

		/**
		 * Returns the current Poisson arrival rate for queue calculations.
		 * 
		 * @return Current Poisson mean (tasks per second)
		 */
		public double getPoissonMean() {
			return currentPoissonMean;
		}

		/**
		 * Returns the current average task size for bandwidth calculations.
		 * 
		 * @return Current average task size in KB
		 */
		public double getTaskSize() {
			return currentTaskSize;
		}

		/**
		 * Updates task statistics for MMPP modeling.
		 * Accumulates task count and size data for adaptive parameter estimation.
		 * 
		 * @param taskSize Size of the processed task in KB
		 */
		public void increaseMM1StatValues(double taskSize) {
			numOfTasks++;
			totalTaskSize += taskSize;
		}

		/**
		 * Initializes M/M/1 queue parameters with validation for queue stability.
		 * This method sets up the fundamental queueing parameters and validates
		 * that the service rate exceeds the arrival rate to ensure queue stability.
		 * 
		 * @param poissonMean Inter-arrival time for Poisson process (seconds)
		 * @param taskSize Average task size (KB)
		 * @param bandwidth Available bandwidth (Kbps)
		 * @throws RuntimeException if μ ≤ λ (unstable queue condition)
		 */
		public void initializeMM1QueueValues(double poissonMean, double taskSize, double bandwidth) {
			currentPoissonMean = poissonMean;
			currentTaskSize = taskSize;

			lastPoissonMean = poissonMean;
			lastTaskSize = taskSize;

			// Calculate M/M/1 queue parameters: λ (arrival rate) and μ (service rate)
			double avgTaskSize = taskSize * 8; // Convert from KB to Kb
			double lamda = ((double)1/(double)poissonMean); // Tasks per second (arrival rate)
			double mu = bandwidth /*Kbps*/ / avgTaskSize /*Kb*/; // Tasks per second (service rate)

			// Validate queue stability: service rate must exceed arrival rate
			if(mu <= lamda) {
				SimLogger.printLine("Error in initializeMM1QueueValues function:" +
						"MU is smaller than LAMDA! Check your simulation settings.");
				System.exit(1);
			}
		}

		/**
		 * Records the current queue parameters as last successful configuration.
		 * This method is called when queue operations are successful to maintain
		 * fallback parameters for adaptive queue management.
		 */
		public void updateLastSuccessfulMM1QueueValues() {
			lastPoissonMean = currentPoissonMean;
			lastTaskSize = currentTaskSize;
		}

		/**
		 * Updates M/M/1 queue parameters based on observed traffic during measurement interval.
		 * This method implements adaptive parameter estimation with smoothing for vehicular
		 * traffic patterns, including optional background traffic consideration.
		 * 
		 * @param interval Time interval for measurement window (seconds)
		 * @param optionalBackgroundDataCount Additional background tasks count
		 * @param optionalBackgroundDataSize Additional background data size (KB)
		 */
		public void updateMM1Values(double interval, double optionalBackgroundDataCount, double optionalBackgroundDataSize) {
			if(numOfTasks == 0) {
				// No tasks observed, use last successful parameters
				currentPoissonMean = lastPoissonMean;
				currentTaskSize = lastTaskSize;
			}
			else {
				// Calculate new parameters based on observed traffic
				double poissonMean = interval / (numOfTasks + optionalBackgroundDataCount);
				double taskSize = (totalTaskSize + optionalBackgroundDataSize) / (numOfTasks + optionalBackgroundDataCount);

				// Apply smoothing after warm-up period to prevent sudden changes
				if(CloudSim.clock() > SimSettings.getInstance().getWarmUpPeriod() && poissonMean > currentPoissonMean)
					poissonMean = (poissonMean + currentPoissonMean * 3) / 4; // Weighted average for stability

				currentPoissonMean = poissonMean;
				currentTaskSize = taskSize;
			}

			// Reset measurement window counters
			numOfTasks = 0;
			totalTaskSize = 0;
		}
	}

	/** MAN control message frequency for inter-RSU coordination (messages per second) */
	private static double MAN_CONTROL_MESSAGE_PER_SECONDS = 10;
	/** MAN control message size for network overhead calculation (KB) */
	private static double MAN_CONTROL_MESSAGE_SIZE = 25;

	/** Timestamp of last M/M/1 queue model parameter update */
	private double lastMM1QueeuUpdateTime;

	/** MMPP wrappers for WLAN download traffic (per access point/RSU) */
	private MMPPWrapper[] wlanMMPPForDownload;
	/** MMPP wrappers for WLAN upload traffic (per access point/RSU) */
	private MMPPWrapper[] wlanMMPPForUpload;

	/** MMPP wrapper for MAN download traffic (inter-RSU communication) */
	private MMPPWrapper manMMPPForDownload;
	/** MMPP wrapper for MAN upload traffic (inter-RSU communication) */
	private MMPPWrapper manMMPPForUpload;

	/** MMPP wrapper for WAN download traffic (RSU-to-cloud communication) */
	private MMPPWrapper wanMMPPForDownload;
	/** MMPP wrapper for WAN upload traffic (RSU-to-cloud communication) */
	private MMPPWrapper wanMMPPForUpload;

	/** MMPP wrapper for GSM download traffic (direct cellular communication) */
	private MMPPWrapper gsmMMPPForDownload;
	/** MMPP wrapper for GSM upload traffic (direct cellular communication) */
	private MMPPWrapper gsmMMPPForUpload;

	/**
	 * Constructor initializes the vehicular network model with MMPP/M/1 queuing capabilities.
	 * Sets up the foundation for multi-modal vehicular network modeling including
	 * WLAN, MAN, WAN, and GSM connectivity options.
	 * 
	 * @param _numberOfMobileDevices Total number of mobile devices (vehicles) in simulation
	 * @param _simScenario Simulation scenario identifier for configuration
	 * @param _orchestratorPolicy Task orchestration policy (unused in constructor)
	 */
	public VehicularNetworkModel(int _numberOfMobileDevices, String _simScenario, String _orchestratorPolicy) {
		super(_numberOfMobileDevices, _simScenario);
		lastMM1QueeuUpdateTime = SimSettings.CLIENT_ACTIVITY_START_TIME;
	}

	/**
	 * Initializes all MMPP wrappers and queue parameters for vehicular network modeling.
	 * This method sets up per-access-point WLAN queues and global MAN/WAN/GSM queues
	 * with appropriate parameters based on simulation configuration and task properties.
	 */
	@Override
	public void initialize() {
		SimSettings SS = SimSettings.getInstance();

		int numOfApp = SimSettings.getInstance().getTaskLookUpTable().length;
		int numOfAccessPoint = SimSettings.getInstance().getNumOfEdgeDatacenters();

		// Initialize per-RSU WLAN MMPP wrappers for V2I communication
		wlanMMPPForDownload = new MMPPWrapper[numOfAccessPoint];
		wlanMMPPForUpload = new MMPPWrapper[numOfAccessPoint];
		for(int apIndex=0; apIndex<numOfAccessPoint; apIndex++) {
			wlanMMPPForDownload[apIndex] = new MMPPWrapper();
			wlanMMPPForUpload[apIndex] = new MMPPWrapper();
		}

		// Initialize global MMPP wrappers for inter-infrastructure communication
		manMMPPForDownload = new MMPPWrapper();  // Inter-RSU MAN network
		manMMPPForUpload = new MMPPWrapper();

		wanMMPPForDownload = new MMPPWrapper();  // RSU-to-cloud WAN network
		wanMMPPForUpload = new MMPPWrapper();

		gsmMMPPForDownload = new MMPPWrapper();  // Direct cellular network
		gsmMMPPForUpload = new MMPPWrapper();

		// Approximate initial usage distribution of access technologies for MMPP modeling
		// These probabilities represent typical vehicular connectivity patterns
		double probOfWlanComm = 0.40;  // V2I via RSU (most common in urban areas)
		double probOfWanComm = 0.15;   // RSU-to-cloud for heavy computation
		double probOfGsmComm = 0.10;   // Direct cellular for continuous coverage
		double probOfManComm = 0.35;   // Inter-RSU for load balancing and handoffs

		double weightedTaskPerSecond = 0;
		double weightedTaskInputSize = 0;
		double weightedTaskOutputSize = 0;

		// Calculate weighted average task parameters across all application types
		for(int taskIndex=0; taskIndex<numOfApp; taskIndex++) {
			double percentageOfAppUsage = SS.getTaskLookUpTable()[taskIndex][0];
			double poissonOfApp = SS.getTaskLookUpTable()[taskIndex][2];
			double taskInputSize = SS.getTaskLookUpTable()[taskIndex][5];
			double taskOutputSize = SS.getTaskLookUpTable()[taskIndex][6];

			// Validate application usage percentage
			if(percentageOfAppUsage <= 0 && percentageOfAppUsage > 100) {
				SimLogger.printLine("Usage percentage of task " + taskIndex + " is invalid (" +
						percentageOfAppUsage + ")! Terminating simulation...");
				System.exit(1);
			}

			// Calculate weighted averages based on application usage patterns
			weightedTaskInputSize += taskInputSize * (percentageOfAppUsage / (double)100);
			weightedTaskOutputSize += taskOutputSize * (percentageOfAppUsage / (double)100);
			weightedTaskPerSecond += ((double)1 / poissonOfApp)  * (percentageOfAppUsage / (double)100);
		}

		// Initialize WLAN MMPP parameters for each RSU/access point
		for(int apIndex=0; apIndex<numOfAccessPoint; apIndex++) {
			// Calculate Poisson inter-arrival time considering vehicle distribution across RSUs
			double poisson = (double)1 / (weightedTaskPerSecond * (numberOfMobileDevices/numOfAccessPoint) * probOfWlanComm);
			wlanMMPPForDownload[apIndex].initializeMM1QueueValues(poisson, weightedTaskOutputSize, SimSettings.getInstance().getWlanBandwidth());
			wlanMMPPForUpload[apIndex].initializeMM1QueueValues(poisson, weightedTaskInputSize, SimSettings.getInstance().getWlanBandwidth());
		}

		// Initialize MAN MMPP parameters for inter-RSU communication
		double poisson = (double)1 / (weightedTaskPerSecond * numberOfMobileDevices * probOfManComm);
		manMMPPForDownload.initializeMM1QueueValues(poisson, weightedTaskOutputSize, SimSettings.getInstance().getManBandwidth());
		manMMPPForUpload.initializeMM1QueueValues(poisson, weightedTaskInputSize, SimSettings.getInstance().getManBandwidth());

		// Initialize WAN MMPP parameters for RSU-to-cloud communication
		poisson = (double)1 / (weightedTaskPerSecond * numberOfMobileDevices *  probOfWanComm);
		wanMMPPForDownload.initializeMM1QueueValues(poisson, weightedTaskOutputSize, SimSettings.getInstance().getWanBandwidth());
		wanMMPPForUpload.initializeMM1QueueValues(poisson, weightedTaskInputSize, SimSettings.getInstance().getWanBandwidth());

		// Initialize GSM MMPP parameters for direct cellular communication
		poisson = (double)1 / (weightedTaskPerSecond * numberOfMobileDevices * probOfGsmComm);
		gsmMMPPForDownload.initializeMM1QueueValues(poisson, weightedTaskOutputSize, SimSettings.getInstance().getGsmBandwidth());
		gsmMMPPForUpload.initializeMM1QueueValues(poisson, weightedTaskInputSize, SimSettings.getInstance().getGsmBandwidth());
	}

	/**
	 * Legacy upload delay method - not used in vehicular scenarios.
	 * Vehicular network model uses specialized delay calculation methods
	 * that account for network technology types and MMPP/M/1 modeling.
	 * 
	 * Note: Source device is always mobile device in vehicular simulation scenarios!
	 * 
	 * @param sourceDeviceId ID of source device (not used)
	 * @param destDeviceId ID of destination device (not used)
	 * @param task Task object (not used)
	 * @return Never returns - terminates simulation
	 * @throws RuntimeException Always thrown as method is not supported
	 */
	@Override
	public double getUploadDelay(int sourceDeviceId, int destDeviceId, Task task) {
		SimLogger.printLine("getUploadDelay is not used in this scenario! Terminating simulation...");
		System.exit(1);
		return 0;
	}

	/**
	 * Legacy download delay method - not used in vehicular scenarios.
	 * Vehicular network model uses specialized delay calculation methods
	 * based on network technology types (WLAN, MAN, WAN, GSM).
	 * 
	 * Note: Destination device is always mobile device in vehicular simulation scenarios!
	 * 
	 * @param sourceDeviceId ID of source device (not used)
	 * @param destDeviceId ID of destination device (not used) 
	 * @param task Task object (not used)
	 * @return Never returns - terminates simulation
	 * @throws RuntimeException Always thrown as method is not supported
	 */
	@Override
	public double getDownloadDelay(int sourceDeviceId, int destDeviceId, Task task) {
		SimLogger.printLine("getDownloadDelay is not used in this scenario! Terminating simulation...");
		System.exit(1);
		return 0;
	}
	
	/**
	 * Legacy upload tracking method - not used in vehicular scenarios.
	 * Vehicular network model uses MMPP-based statistical tracking instead.
	 * 
	 * @param accessPointLocation Location of access point (not used)
	 * @param destDeviceId Destination device ID (not used)
	 * @throws RuntimeException Always thrown as method is not supported
	 */
	@Override
	public void uploadStarted(Location accessPointLocation, int destDeviceId) {
		SimLogger.printLine("uploadStarted is not used in this scenario! Terminating simulation...");
		System.exit(1);
	}

	/**
	 * Legacy upload tracking method - not used in vehicular scenarios.
	 * Vehicular network model uses MMPP-based statistical tracking instead.
	 * 
	 * @param accessPointLocation Location of access point (not used)
	 * @param destDeviceId Destination device ID (not used)
	 * @throws RuntimeException Always thrown as method is not supported
	 */
	@Override
	public void uploadFinished(Location accessPointLocation, int destDeviceId) {
		SimLogger.printLine("uploadFinished is not used in this scenario! Terminating simulation...");
		System.exit(1);
	}

	/**
	 * Legacy download tracking method - not used in vehicular scenarios.
	 * Vehicular network model uses MMPP-based statistical tracking instead.
	 * 
	 * @param accessPointLocation Location of access point (not used)
	 * @param sourceDeviceId Source device ID (not used)
	 * @throws RuntimeException Always thrown as method is not supported
	 */
	@Override
	public void downloadStarted(Location accessPointLocation, int sourceDeviceId) {
		SimLogger.printLine("downloadStarted is not used in this scenario! Terminating simulation...");
		System.exit(1);
	}

	/**
	 * Legacy download tracking method - not used in vehicular scenarios.
	 * Vehicular network model uses MMPP-based statistical tracking instead.
	 * 
	 * @param accessPointLocation Location of access point (not used)
	 * @param sourceDeviceId Source device ID (not used)
	 * @throws RuntimeException Always thrown as method is not supported
	 */
	@Override
	public void downloadFinished(Location accessPointLocation, int sourceDeviceId) {
		SimLogger.printLine("downloadFinished is not used in this scenario! Terminating simulation...");
		System.exit(1);
	}

	/**
	 * Estimates WLAN download delay for performance analysis and logging.
	 * This method provides delay estimation without affecting MMPP statistics.
	 * 
	 * @param apId Access point (RSU) ID for delay estimation
	 * @return Estimated WLAN download delay in seconds
	 */
	public double estimateWlanDownloadDelay(int apId){
		return getWlanDownloadDelay(0,apId,true);
	}

	/**
	 * Estimates WLAN upload delay for performance analysis and logging.
	 * This method provides delay estimation without affecting MMPP statistics.
	 * 
	 * @param apId Access point (RSU) ID for delay estimation
	 * @return Estimated WLAN upload delay in seconds
	 */
	public double estimateWlanUploadDelay(int apId){
		return getWlanUploadDelay(0,apId,true);
	}

	/**
	 * Estimates upload delay for specified network technology without updating statistics.
	 * Used for orchestration decisions and performance analysis.
	 * 
	 * @param delayType Network technology type (GSM, WLAN, MAN, WAN)
	 * @param task Task containing size and location information
	 * @return Estimated upload delay in seconds
	 */
	public double estimateUploadDelay(NETWORK_DELAY_TYPES delayType, Task task) {
		return getDelay(delayType, task, false, true);
	}

	/**
	 * Estimates download delay for specified network technology without updating statistics.
	 * Used for orchestration decisions and performance analysis.
	 * 
	 * @param delayType Network technology type (GSM, WLAN, MAN, WAN)
	 * @param task Task containing size and location information
	 * @return Estimated download delay in seconds
	 */
	public double estimateDownloadDelay(NETWORK_DELAY_TYPES delayType, Task task) {
		return getDelay(delayType, task, true, true);
	}

	/**
	 * Calculates actual upload delay and updates MMPP statistics.
	 * This method is called during actual task transmission for accurate modeling.
	 * 
	 * @param delayType Network technology type (GSM, WLAN, MAN, WAN)
	 * @param task Task containing size and location information
	 * @return Actual upload delay in seconds
	 */
	public double getUploadDelay(NETWORK_DELAY_TYPES delayType, Task task) {
		return getDelay(delayType, task, false, false);
	}

	/**
	 * Calculates actual download delay and updates MMPP statistics.
	 * This method is called during actual task transmission for accurate modeling.
	 * 
	 * @param delayType Network technology type (GSM, WLAN, MAN, WAN)
	 * @param task Task containing size and location information
	 * @return Actual download delay in seconds
	 */
	public double getDownloadDelay(NETWORK_DELAY_TYPES delayType, Task task) {
		return getDelay(delayType, task, true, false);
	}

	private double getDelay(NETWORK_DELAY_TYPES delayType, Task task, boolean forDownload, boolean justEstimate) {
		double delay = 0;

		if(delayType == NETWORK_DELAY_TYPES.GSM_DELAY){
			if(forDownload)
				delay = getGsmDownloadDelay(task.getCloudletOutputSize(), justEstimate);
			else
				delay = getGsmUploadDelay(task.getCloudletFileSize(), justEstimate);

			if(delay != 0)
				delay += SimSettings.getInstance().getGsmPropagationDelay();
		}
		else if(delayType == NETWORK_DELAY_TYPES.WLAN_DELAY){
			if(forDownload)
				delay = getWlanDownloadDelay(task.getCloudletOutputSize(), task.getSubmittedLocation().getServingWlanId(), justEstimate);
			else
				delay = getWlanUploadDelay(task.getCloudletFileSize(), task.getSubmittedLocation().getServingWlanId(),justEstimate);
		}
		else if(delayType == NETWORK_DELAY_TYPES.WAN_DELAY){
			if(forDownload)
				delay = getWanDownloadDelay(task.getCloudletOutputSize(), justEstimate);
			else
				delay = getWanUploadDelay(task.getCloudletFileSize(), justEstimate);

			if(delay != 0)
				delay += SimSettings.getInstance().getWanPropagationDelay();
		}
		else if(delayType == NETWORK_DELAY_TYPES.MAN_DELAY){
			if(forDownload)
				delay = getManDownloadDelay(task.getCloudletOutputSize(), justEstimate);
			else
				delay = getManUploadDelay(task.getCloudletFileSize(), justEstimate);

			if(delay != 0)
				delay += SimSettings.getInstance().getInternalLanDelay();
		}

		return delay;
	}

	private double calculateMM1(double taskSize, double bandwidth /*Kbps*/, MMPPWrapper mmppWrapper, boolean justEstimate){
		double mu=0, lamda=0;
		double PoissonMean = mmppWrapper.getPoissonMean();
		double avgTaskSize = mmppWrapper.getTaskSize(); /*KB*/

		if(!justEstimate)
			mmppWrapper.increaseMM1StatValues(taskSize);

		avgTaskSize = avgTaskSize * 8; //convert from KB to Kb

		lamda = ((double)1/(double)PoissonMean); //task per seconds
		mu = bandwidth /*Kbps*/ / avgTaskSize /*Kb*/; //task per seconds
		double result = (double)1 / (mu-lamda);

		return (result > 7.5 || result < 0 ) ? 0 : result;
	}

	private double getWlanDownloadDelay(double taskSize, int accessPointId, boolean justEstimate) {
		double bw = SimSettings.getInstance().getWlanBandwidth();

		double result = calculateMM1(taskSize, bw, wlanMMPPForDownload[accessPointId],justEstimate);

		if(maxWlanDelay < result)
			maxWlanDelay = result;

		return result;
	}

	private double getWlanUploadDelay(double taskSize, int accessPointId, boolean justEstimate) {
		double bw = SimSettings.getInstance().getWlanBandwidth();

		double result = calculateMM1(taskSize, bw, wlanMMPPForUpload[accessPointId], justEstimate);

		if(maxWlanDelay < result)
			maxWlanDelay = result;

		return result;
	}

	private double getManDownloadDelay(double taskSize, boolean justEstimate) {
		double bw = SimSettings.getInstance().getManBandwidth();

		double result = calculateMM1(taskSize, bw, manMMPPForDownload, justEstimate);

		return result;
	}

	private double getManUploadDelay(double taskSize, boolean justEstimate) {
		double bw = SimSettings.getInstance().getManBandwidth();

		double result = calculateMM1(taskSize, bw, manMMPPForUpload, justEstimate);

		return result;
	}

	private double getWanDownloadDelay(double taskSize, boolean justEstimate) {
		double bw = SimSettings.getInstance().getWanBandwidth();

		double result = calculateMM1(taskSize, bw, wanMMPPForDownload, justEstimate);

		if(maxWanDelay < result)
			maxWanDelay = result;

		return result;
	}

	private double getWanUploadDelay(double taskSize, boolean justEstimate) {
		double bw = SimSettings.getInstance().getWanBandwidth();

		double result = calculateMM1(taskSize, bw, wanMMPPForUpload, justEstimate);

		if(maxWanDelay < result)
			maxWanDelay = result;

		return result;
	}

	private double getGsmDownloadDelay(double taskSize, boolean justEstimate) {
		double bw = SimSettings.getInstance().getGsmBandwidth();

		double result = calculateMM1(taskSize, bw, gsmMMPPForDownload, justEstimate);

		if(maxGsmDelay < result)
			maxGsmDelay = result;

		return result;
	}

	private double getGsmUploadDelay(double taskSize, boolean justEstimate) {
		double bw = SimSettings.getInstance().getGsmBandwidth();

		double result = calculateMM1(taskSize, bw, gsmMMPPForUpload, justEstimate);

		if(maxGsmDelay < result)
			maxGsmDelay = result;

		return result;
	}

	/**
	 * Updates all MMPP/M/1 queue model parameters based on observed traffic during the measurement interval.
	 * This method is called periodically to adapt the vehicular network model to changing traffic patterns.
	 * It accounts for both task traffic and background control messages in vehicular infrastructure.
	 * 
	 * The update process includes:
	 * - Per-RSU WLAN queue parameter updates
	 * - MAN queue updates with orchestrator control message overhead
	 * - WAN and GSM queue parameter updates
	 * - Validation of queue stability for each network technology
	 */
	public void updateMM1QueeuModel(){
		int numOfAccessPoint = SimSettings.getInstance().getNumOfEdgeDatacenters();

		// Calculate time interval since last update for adaptive parameter estimation
		double lastInterval = CloudSim.clock() - lastMM1QueeuUpdateTime;
		lastMM1QueeuUpdateTime = CloudSim.clock();

		// Generate background traffic model for MAN network
		// Assumes periodic control message exchange between RSUs and edge orchestrator
		double numOfControlMessagePerInterval = lastInterval *
				(double)numberOfMobileDevices * MAN_CONTROL_MESSAGE_PER_SECONDS;

		double sizeOfControlMessages = (double)numberOfMobileDevices * MAN_CONTROL_MESSAGE_SIZE;

		// Update WLAN MMPP parameters for each RSU (V2I communication)
		for(int i = 0; i< numOfAccessPoint; i++){
			wlanMMPPForDownload[i].updateMM1Values(lastInterval, 0, 0);
			wlanMMPPForUpload[i].updateMM1Values(lastInterval, 0, 0);

			// Validate queue stability and update successful parameters
			if(getWlanDownloadDelay(0, i, true) != 0)
				wlanMMPPForDownload[i].updateLastSuccessfulMM1QueueValues();
			if(getWlanUploadDelay(0, i, true) != 0)
				wlanMMPPForUpload[i].updateLastSuccessfulMM1QueueValues();
		}

		// Update MAN MMPP parameters including orchestrator control overhead
		manMMPPForDownload.updateMM1Values(lastInterval, numOfControlMessagePerInterval, sizeOfControlMessages);
		manMMPPForUpload.updateMM1Values(lastInterval, numOfControlMessagePerInterval, sizeOfControlMessages);
		if(getManDownloadDelay(0, true) != 0)
			manMMPPForDownload.updateLastSuccessfulMM1QueueValues();
		if(getManUploadDelay(0, true) != 0)
			manMMPPForUpload.updateLastSuccessfulMM1QueueValues();

		// Update WAN MMPP parameters (RSU-to-cloud communication)
		wanMMPPForDownload.updateMM1Values(lastInterval, 0, 0);
		wanMMPPForUpload.updateMM1Values(lastInterval, 0, 0);
		if(getWanDownloadDelay(0, true) != 0)
			wanMMPPForDownload.updateLastSuccessfulMM1QueueValues();
		if(getWanUploadDelay(0, true) != 0)
			wanMMPPForUpload.updateLastSuccessfulMM1QueueValues();

		// Update GSM MMPP parameters (direct cellular communication)
		gsmMMPPForDownload.updateMM1Values(lastInterval, 0, 0);
		gsmMMPPForUpload.updateMM1Values(lastInterval, 0, 0);
		if(getGsmDownloadDelay(0, true) != 0)
			gsmMMPPForDownload.updateLastSuccessfulMM1QueueValues();
		if(getGsmUploadDelay(0, true) != 0)
			gsmMMPPForUpload.updateLastSuccessfulMM1QueueValues();

		//		for(int i = 0; i< numOfAccessPoint; i++){
		//			SimLogger.printLine(CloudSim.clock() + ": MM1 Queue Model is updated");
		//			SimLogger.printLine("WlanPoissonMeanForDownload[" + i + "] - avgWlanTaskOutputSize[" + i + "]: "
		//					+ String.format("%.3f", wlanMMPPForDownload[i].getPoissonMean()) + " - "
		//					+ String.format("%.3f", wlanMMPPForDownload[i].getTaskSize()));
		//			SimLogger.printLine("WlanPoissonMeanForUpload[" + i + "] - avgWlanTaskInputSize[" + i + "]: "
		//					+ String.format("%.3f", wlanMMPPForUpload[i].getPoissonMean()) + " - "
		//					+ String.format("%.3f", wlanMMPPForUpload[i].getTaskSize()));
		//		}
		//		SimLogger.printLine("ManPoissonMeanForDownload - avgManTaskOutputSize: "
		//				+ String.format("%.3f", manMMPPForDownload.getPoissonMean()) + " - "
		//				+ String.format("%.3f", manMMPPForDownload.getTaskSize()));
		//		SimLogger.printLine("ManPoissonMeanForUpload - avgManTaskInputSize: "
		//				+ String.format("%.3f", manMMPPForUpload.getPoissonMean()) + " - "
		//				+ String.format("%.3f", manMMPPForUpload.getTaskSize()));
		//		SimLogger.printLine("WanPoissonMeanForDownload - avgWanTaskOutputSize: "
		//				+ String.format("%.3f", wanMMPPForDownload.getPoissonMean()) + " - "
		//				+ String.format("%.3f", wanMMPPForDownload.getTaskSize()));
		//		SimLogger.printLine("WanPoissonMeanForUpload - avgWanTaskInputSize: "
		//				+ String.format("%.3f", wanMMPPForUpload.getPoissonMean()) + " - "
		//				+ String.format("%.3f", wanMMPPForUpload.getTaskSize()));
		//		SimLogger.printLine("GsmPoissonMeanForDownload - avgGsmTaskOutputSize: "
		//				+ String.format("%.3f", gsmMMPPForDownload.getPoissonMean()) + " - "
		//				+ String.format("%.3f", gsmMMPPForDownload.getTaskSize()));
		//		SimLogger.printLine("GsmPoissonMeanForUpload - avgGsmTaskInputSize: "
		//				+ String.format("%.3f", gsmMMPPForUpload.getPoissonMean()) + " - "
		//				+ String.format("%.3f", gsmMMPPForUpload.getTaskSize()));
		//		SimLogger.printLine("------------------------------------------------");

	}
}