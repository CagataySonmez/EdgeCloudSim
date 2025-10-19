/*
 * Title:        EdgeCloudSim - Edge Orchestrator implementation
 * 
 * Description: 
 * VehicularEdgeOrchestrator decides which tier (mobile, edge or cloud)
 * to offload and picks proper VM to execute incoming tasks
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app5;

import java.util.stream.DoubleStream;

import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.core.SimSettings.NETWORK_DELAY_TYPES;
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

public class VehicularEdgeOrchestrator extends EdgeOrchestrator {
	private static final int BASE = 100000; //start from base in order not to conflict cloudsim tag!
	private static final int UPDATE_PREDICTION_WINDOW = BASE+1;

	public static final int CLOUD_DATACENTER_VIA_GSM = 1;
	public static final int CLOUD_DATACENTER_VIA_RSU = 2;
	public static final int EDGE_DATACENTER = 3;

	private int cloudVmCounter;
	private int edgeVmCounter;
	private int numOfMobileDevice;

	private OrchestratorStatisticLogger statisticLogger;
	private OrchestratorTrainerLogger trainerLogger;

	private MultiArmedBanditHelper MAB;
	private GameTheoryHelper GTH;

	/**
	 * Constructor for vehicular edge orchestrator
	 * @param _numOfMobileDevices Number of mobile devices in simulation
	 * @param _policy Orchestration policy (AI_BASED, GAME_THEORY, MAB, etc.)
	 * @param _simScenario Simulation scenario type
	 */
	public VehicularEdgeOrchestrator(int _numOfMobileDevices, String _policy, String _simScenario) {
		super(_policy, _simScenario);
		this.numOfMobileDevice = _numOfMobileDevices;
	}

	@Override
	public void initialize() {
		// Initialize VM counters for load balancing
		cloudVmCounter = 0;
		edgeVmCounter = 0;

		// Initialize logging components
		statisticLogger = new OrchestratorStatisticLogger();
		trainerLogger = new OrchestratorTrainerLogger();

		// Initialize Multi-Armed Bandit helper with task length bounds
		double lookupTable[][] = SimSettings.getInstance().getTaskLookUpTable();
		// Assume first app has lowest and last app has highest task length
		double minTaskLength = lookupTable[0][7];
		double maxTaskLength = lookupTable[lookupTable.length-1][7];
		MAB = new MultiArmedBanditHelper(minTaskLength, maxTaskLength);

		// Initialize Game Theory helper for Nash equilibrium calculations
		// Parameters: min load (0), max load (20), number of players (mobile devices)
		GTH = new GameTheoryHelper(0, 20, numOfMobileDevice);
	}

	/**
	 * Determines which computing tier to offload task based on orchestration policy
	 * @param task Task to be offloaded
	 * @return Target device ID (EDGE_DATACENTER, CLOUD_DATACENTER_VIA_RSU, or CLOUD_DATACENTER_VIA_GSM)
	 */
	@Override
	public int getDeviceToOffload(Task task) {
		int result = 0;

		// Get current resource utilization metrics
		double avgEdgeUtilization = SimManager.getInstance().getEdgeServerManager().getAvgUtilization();
		double avgCloudUtilization = SimManager.getInstance().getCloudServerManager().getAvgUtilization();

		// Estimate network delays for different communication paths
		VehicularNetworkModel networkModel = (VehicularNetworkModel)SimManager.getInstance().getNetworkModel();
		double wanUploadDelay = networkModel.estimateUploadDelay(NETWORK_DELAY_TYPES.WAN_DELAY, task);
		double wanDownloadDelay = networkModel.estimateDownloadDelay(NETWORK_DELAY_TYPES.WAN_DELAY, task);

		double gsmUploadDelay = networkModel.estimateUploadDelay(NETWORK_DELAY_TYPES.GSM_DELAY, task);
		double gsmDownloadDelay = networkModel.estimateDownloadDelay(NETWORK_DELAY_TYPES.GSM_DELAY, task);

		double wlanUploadDelay = networkModel.estimateUploadDelay(NETWORK_DELAY_TYPES.WLAN_DELAY, task);
		double wlanDownloadDelay =networkModel.estimateDownloadDelay(NETWORK_DELAY_TYPES.WLAN_DELAY, task);

		// Available offloading options
		int options[] = {
				EDGE_DATACENTER,
				CLOUD_DATACENTER_VIA_RSU,
				CLOUD_DATACENTER_VIA_GSM
		};

		// Handle zero delays for AI-based and advanced algorithms
		// Replace zero delays with maximum values to indicate unavailability
		if(policy.startsWith("AI_") || policy.equals("MAB") || policy.equals("GAME_THEORY")) {
			if(wanUploadDelay == 0)
				wanUploadDelay = WekaWrapper.MAX_WAN_DELAY;

			if(wanDownloadDelay == 0)
				wanDownloadDelay = WekaWrapper.MAX_WAN_DELAY;

			if(gsmUploadDelay == 0)
				gsmUploadDelay = WekaWrapper.MAX_GSM_DELAY;

			if(gsmDownloadDelay == 0)
				gsmDownloadDelay = WekaWrapper.MAX_GSM_DELAY;

			if(wlanUploadDelay == 0)
				wlanUploadDelay = WekaWrapper.MAX_WLAN_DELAY;

			if(wlanDownloadDelay == 0)
				wlanDownloadDelay = WekaWrapper.MAX_WLAN_DELAY;
		}

		// AI-BASED: Use machine learning models to predict success and estimate service time
		if (policy.equals("AI_BASED")) {
			WekaWrapper weka = WekaWrapper.getInstance();

			// Classify success probability for edge datacenter
			boolean predictedResultForEdge = weka.handleClassification(EDGE_DATACENTER,
					new double[] {trainerLogger.getOffloadStat(EDGE_DATACENTER-1),
							task.getCloudletLength(), wlanUploadDelay,
							wlanDownloadDelay, avgEdgeUtilization});

			// Classify success probability for cloud via RSU
			boolean predictedResultForCloudViaRSU = weka.handleClassification(CLOUD_DATACENTER_VIA_RSU,
					new double[] {trainerLogger.getOffloadStat(CLOUD_DATACENTER_VIA_RSU-1),
							wanUploadDelay, wanDownloadDelay});

			// Classify success probability for cloud via GSM
			boolean predictedResultForCloudViaGSM = weka.handleClassification(CLOUD_DATACENTER_VIA_GSM,
					new double[] {trainerLogger.getOffloadStat(CLOUD_DATACENTER_VIA_GSM-1),
							gsmUploadDelay, gsmDownloadDelay});

			// Initialize service time predictions with maximum values (infeasible)
			double predictedServiceTimeForEdge = Double.MAX_VALUE;
			double predictedServiceTimeForCloudViaRSU = Double.MAX_VALUE;
			double predictedServiceTimeForCloudViaGSM = Double.MAX_VALUE;

			// Predict service times only for options classified as successful
			if(predictedResultForEdge)
				predictedServiceTimeForEdge = weka.handleRegression(EDGE_DATACENTER,
						new double[] {task.getCloudletLength(), avgEdgeUtilization});

			if(predictedResultForCloudViaRSU)
				predictedServiceTimeForCloudViaRSU = weka.handleRegression(CLOUD_DATACENTER_VIA_RSU,
						new double[] {task.getCloudletLength(), wanUploadDelay, wanDownloadDelay});

			if(predictedResultForCloudViaGSM)
				predictedServiceTimeForCloudViaGSM = weka.handleRegression(CLOUD_DATACENTER_VIA_GSM,
						new double[] {task.getCloudletLength(), gsmUploadDelay, gsmDownloadDelay});

			// Handle case where no option is predicted as successful - random selection
			if(!predictedResultForEdge && !predictedResultForCloudViaRSU && !predictedResultForCloudViaGSM) {
				double probabilities[] = {0.33, 0.34, 0.33};

				double randomNumber = SimUtils.getRandomDoubleNumber(0, 1);
				double lastPercentagte = 0;
				boolean resultFound = false;
				for(int i=0; i<probabilities.length; i++) {
					if(randomNumber <= probabilities[i] + lastPercentagte) {
						result = options[i];
						resultFound = true;
						break;
					}
					lastPercentagte += probabilities[i];
				}

				if(!resultFound) {
					SimLogger.printLine("Unexpected probability calculation! Terminating simulation...");
					System.exit(1);
				}
			}
			// Select option with minimum predicted service time
			else if(predictedServiceTimeForEdge <= Math.min(predictedServiceTimeForCloudViaRSU, predictedServiceTimeForCloudViaGSM))
				result = EDGE_DATACENTER;
			else if(predictedServiceTimeForCloudViaRSU <= Math.min(predictedServiceTimeForEdge, predictedServiceTimeForCloudViaGSM))
				result = CLOUD_DATACENTER_VIA_RSU;
			else if(predictedServiceTimeForCloudViaGSM <= Math.min(predictedServiceTimeForEdge, predictedServiceTimeForCloudViaRSU))
				result = CLOUD_DATACENTER_VIA_GSM;
			else{
				SimLogger.printLine("Impossible occurred in AI based algorithm! Terminating simulation...");
				System.exit(1);
			}

			// Update offload statistics for future predictions
			trainerLogger.addOffloadStat(result-1);
		}
		// AI_TRAINER: Generate training data with task-type-specific probability distributions
		else if (policy.equals("AI_TRAINER")) {
			double probabilities[] = null;
			// Different probabilities based on task type for diverse training data
			if(task.getTaskType() == 0)
				probabilities = new double[] {0.60, 0.23, 0.17}; // Edge-heavy for task type 0
			else if(task.getTaskType() == 1)
				probabilities = new double[] {0.30, 0.53, 0.17}; // Cloud-via-RSU heavy for task type 1
			else
				probabilities = new double[] {0.23, 0.60, 0.17}; // Cloud-via-GSM heavy for other types

			double randomNumber = SimUtils.getRandomDoubleNumber(0, 1);
			double lastPercentagte = 0;
			boolean resultFound = false;
			for(int i=0; i<probabilities.length; i++) {
				if(randomNumber <= probabilities[i] + lastPercentagte) {
					result = options[i];
					resultFound = true;

					// Record training data with network delays and decision
					trainerLogger.addStat(task.getCloudletId(), result,
							wanUploadDelay, wanDownloadDelay,
							gsmUploadDelay, gsmDownloadDelay,
							wlanUploadDelay, wlanDownloadDelay);

					break;
				}
				lastPercentagte += probabilities[i];
			}

			if(!resultFound) {
				SimLogger.printLine("Unexpected probability calculation for AI based orchestrator! Terminating simulation...");
				System.exit(1);
			}
		}
		// RANDOM: Uniform random selection among all options (baseline)
		else if(policy.equals("RANDOM")){
			double probabilities[] = {0.33, 0.33, 0.34};

			double randomNumber = SimUtils.getRandomDoubleNumber(0, 1);
			double lastPercentagte = 0;
			boolean resultFound = false;
			for(int i=0; i<probabilities.length; i++) {
				if(randomNumber <= probabilities[i] + lastPercentagte) {
					result = options[i];
					resultFound = true;
					break;
				}
				lastPercentagte += probabilities[i];
			}

			if(!resultFound) {
				SimLogger.printLine("Unexpected probability calculation for random orchestrator! Terminating simulation...");
				System.exit(1);
			}
		}
		// MAB: Multi-Armed Bandit with Upper Confidence Bound algorithm
		else if (policy.equals("MAB")) {
			// Initialize MAB with expected delays if not already done
			if(!MAB.isInitialized()){
				double expectedProcessingDealyOnCloud = task.getCloudletLength() /
						SimSettings.getInstance().getMipsForCloudVM();

				// All Edge VMs are identical, get MIPS from first VM
				double expectedProcessingDealyOnEdge = task.getCloudletLength() /
						SimManager.getInstance().getEdgeServerManager().getVmList(0).get(0).getMips();

				// Calculate total expected delays for each option
				double[] expectedDelays = {
						wlanUploadDelay + wlanDownloadDelay + expectedProcessingDealyOnEdge,
						wanUploadDelay + wanDownloadDelay + expectedProcessingDealyOnCloud,
						gsmUploadDelay + gsmDownloadDelay + expectedProcessingDealyOnCloud
				};

				MAB.initialize(expectedDelays, task.getCloudletLength());
			}

			// Use UCB algorithm to select arm (option) with highest upper confidence bound
			result = options[MAB.runUCB(task.getCloudletLength())];
		}
		// GAME_THEORY: Nash equilibrium-based decision using game theory
		else if (policy.equals("GAME_THEORY")) {
			// Calculate expected processing delay on edge considering current utilization
			double expectedProcessingDealyOnEdge = task.getCloudletLength() /
					SimManager.getInstance().getEdgeServerManager().getVmList(0).get(0).getMips();

			// Adjust for queueing delay based on utilization (M/M/1 queue approximation)
			expectedProcessingDealyOnEdge *= 100 / (100 - avgEdgeUtilization);

			double expectedEdgeDelay = expectedProcessingDealyOnEdge + 
					wlanUploadDelay + wlanDownloadDelay;

			// Calculate expected processing delay on cloud considering utilization
			double expectedProcessingDealyOnCloud = task.getCloudletLength() /
					SimSettings.getInstance().getMipsForCloudVM();

			expectedProcessingDealyOnCloud *= 100 / (100 - avgCloudUtilization);

			// Randomly choose between GSM and RSU for cloud access
			boolean isGsmFaster = SimUtils.getRandomDoubleNumber(0, 1) < 0.5;
			double expectedCloudDelay = expectedProcessingDealyOnCloud +	
					(isGsmFaster ? gsmUploadDelay : wanUploadDelay) +
					(isGsmFaster ? gsmDownloadDelay : wanDownloadDelay);

			// Get task-specific parameters for game theory calculation
			double taskArrivalRate = SimSettings.getInstance().getTaskLookUpTable()[task.getTaskType()][2];
			double maxDelay = SimSettings.getInstance().getTaskLookUpTable()[task.getTaskType()][13] * (double)6;

			// Calculate Nash equilibrium probability for this mobile device
			double Pi = GTH.getPi(task.getMobileDeviceId(), taskArrivalRate, expectedEdgeDelay, expectedCloudDelay, maxDelay);

			double randomNumber = SimUtils.getRandomDoubleNumber(0, 1);

			// Make decision based on Nash equilibrium probability
			if(Pi < randomNumber)
				result = EDGE_DATACENTER;
			else
				result = (isGsmFaster ? CLOUD_DATACENTER_VIA_GSM : CLOUD_DATACENTER_VIA_RSU);
		}
		// PREDICTIVE: Adaptive algorithm based on historical performance statistics
		else if (policy.equals("PREDICTIVE")) {		
			// Initial uniform probability distribution
			double probabilities[] = {0.34, 0.33, 0.33};

			// Use predictive logic only after warm-up period to collect statistics
			if(CloudSim.clock() > SimSettings.getInstance().getWarmUpPeriod()) {
				// Calculate failure rates for each option
				double failureRates[] = {
						statisticLogger.getFailureRate(options[0]),
						statisticLogger.getFailureRate(options[1]),
						statisticLogger.getFailureRate(options[2])
				};

				// Calculate average service times for each option
				double serviceTimes[] = {
						statisticLogger.getServiceTime(options[0]),
						statisticLogger.getServiceTime(options[1]),
						statisticLogger.getServiceTime(options[2])
				};

				double failureRateScores[] = {0, 0, 0};
				double serviceTimeScores[] = {0, 0, 0};

				// Calculate inverse scores (lower values get higher scores)
				for(int i=0; i<probabilities.length; i++) {
					// Inverse failure rate scoring: lower failure rate = higher score
					failureRateScores[i] = DoubleStream.of(failureRates).sum() / failureRates[i];
					// Inverse service time scoring: lower service time = higher score
					serviceTimeScores[i] = DoubleStream.of(serviceTimes).sum() / serviceTimes[i];
				}

				// Adapt probabilities based on performance metrics
				for(int i=0; i<probabilities.length; i++) {
					// Prioritize failure rate if overall failure rate is high (>30%)
					if(DoubleStream.of(failureRates).sum() > 0.3)
						probabilities[i] = failureRateScores[i] / DoubleStream.of(failureRateScores).sum();
					else
						// Otherwise prioritize service time optimization
						probabilities[i] = serviceTimeScores[i] / DoubleStream.of(serviceTimeScores).sum(); 
				}
			}

			double randomNumber = SimUtils.getRandomDoubleNumber(0.01, 0.99);
			double lastPercentagte = 0;
			boolean resultFound = false;
			for(int i=0; i<probabilities.length; i++) {
				if(randomNumber <= probabilities[i] + lastPercentagte) {
					result = options[i];
					resultFound = true;
					break;
				}
				lastPercentagte += probabilities[i];
			}

			if(!resultFound) {
				SimLogger.printLine("Unexpected probability calculation for predictive orchestrator! Terminating simulation...");
				System.exit(1);
			}
		}
		else {
			SimLogger.printLine("Unknow edge orchestrator policy! Terminating simulation...");
			System.exit(1);
		}

		return result;
	}

	/**
	 * Selects a specific VM within the chosen datacenter using round-robin load balancing
	 * @param task Task to be assigned to VM
	 * @param deviceId Target datacenter ID (edge or cloud)
	 * @return Selected VM instance for task execution
	 */
	@Override
	public Vm getVmToOffload(Task task, int deviceId) {
		Vm selectedVM = null;

		// Cloud VM selection (both GSM and RSU use same cloud infrastructure)
		if (deviceId == CLOUD_DATACENTER_VIA_GSM || deviceId == CLOUD_DATACENTER_VIA_RSU) {
			int numOfCloudHosts = SimSettings.getInstance().getNumOfCloudHost();
			int hostIndex = (cloudVmCounter / numOfCloudHosts) % numOfCloudHosts;
			int vmIndex = cloudVmCounter % SimSettings.getInstance().getNumOfCloudVMsPerHost();;

			selectedVM = SimManager.getInstance().getCloudServerManager().getVmList(hostIndex).get(vmIndex);

			// Round-robin cloud VM counter
			cloudVmCounter++;
			cloudVmCounter = cloudVmCounter % SimSettings.getInstance().getNumOfCloudVMs();

		}
		// Edge VM selection
		else if (deviceId == EDGE_DATACENTER) {
			int numOfEdgeVMs = SimSettings.getInstance().getNumOfEdgeVMs();
			int numOfEdgeHosts = SimSettings.getInstance().getNumOfEdgeHosts();
			int vmPerHost = numOfEdgeVMs / numOfEdgeHosts;

			int hostIndex = (edgeVmCounter / vmPerHost) % numOfEdgeHosts;
			int vmIndex = edgeVmCounter % vmPerHost;

			selectedVM = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex).get(vmIndex);

			// Round-robin edge VM counter
			edgeVmCounter++;
			edgeVmCounter = edgeVmCounter % numOfEdgeVMs;
		}
		else {
			SimLogger.printLine("Unknown device id! Terminating simulation...");
			System.exit(1);
		}
		return selectedVM;
	}

	@Override
	public void startEntity() {
		// Schedule periodic statistics window updates for predictive policy
		if(policy.equals("PREDICTIVE")) {
			schedule(getId(), SimSettings.CLIENT_ACTIVITY_START_TIME +
					OrchestratorStatisticLogger.PREDICTION_WINDOW_UPDATE_INTERVAL, 
					UPDATE_PREDICTION_WINDOW);
		}
	}

	@Override
	public void shutdownEntity() {
		// No cleanup required for vehicular orchestrator
	}


	@Override
	public void processEvent(SimEvent ev) {
		if (ev == null) {
			SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - an event is null! Terminating simulation...");
			System.exit(1);
			return;
		}

		switch (ev.getTag()) {
		case UPDATE_PREDICTION_WINDOW:
		{
			statisticLogger.switchNewStatWindow();
			schedule(getId(), OrchestratorStatisticLogger.PREDICTION_WINDOW_UPDATE_INTERVAL,
					UPDATE_PREDICTION_WINDOW);
			break;
		}
		default:
			SimLogger.printLine(getName() + ": unknown event type");
			break;
		}
	}

	public void processOtherEvent(SimEvent ev) {
		if (ev == null) {
			SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - an event is null! Terminating simulation...");
			System.exit(1);
			return;
		}
	}

	/**
	 * Records successful task completion for learning algorithms
	 * @param task Completed task
	 * @param serviceTime Total service time including network and processing delays
	 */
	public void taskCompleted(Task task, double serviceTime) {
		if(policy.equals("AI_TRAINER"))
			trainerLogger.addSuccessStat(task, serviceTime);

		if(policy.equals("PREDICTIVE"))
			statisticLogger.addSuccessStat(task, serviceTime);

		if(policy.equals("MAB"))
			MAB.updateUCB(task, serviceTime);
	}

	/**
	 * Records task failure for learning algorithms
	 * @param task Failed task
	 */
	public void taskFailed(Task task) {
		if(policy.equals("AI_TRAINER"))
			trainerLogger.addFailStat(task);

		if(policy.equals("PREDICTIVE"))
			statisticLogger.addFailStat(task);

		if(policy.equals("MAB"))
			MAB.updateUCB(task, 0);
	}

	/**
	 * Opens training data output file for AI trainer mode
	 */
	public void openTrainerOutputFile() {
		trainerLogger.openTrainerOutputFile();
	}

	/**
	 * Closes training data output file for AI trainer mode
	 */
	public void closeTrainerOutputFile() {
		trainerLogger.closeTrainerOutputFile();
	}
}
