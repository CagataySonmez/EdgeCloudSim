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

	public VehicularEdgeOrchestrator(int _numOfMobileDevices, String _policy, String _simScenario) {
		super(_policy, _simScenario);
		this.numOfMobileDevice = _numOfMobileDevices;
	}

	@Override
	public void initialize() {
		cloudVmCounter = 0;
		edgeVmCounter = 0;

		statisticLogger = new OrchestratorStatisticLogger();
		trainerLogger = new OrchestratorTrainerLogger();

		double lookupTable[][] = SimSettings.getInstance().getTaskLookUpTable();
		//assume the first app has the lowest and the last app has the highest task length value
		double minTaskLength = lookupTable[0][7];
		double maxTaskLength = lookupTable[lookupTable.length-1][7];
		MAB = new MultiArmedBanditHelper(minTaskLength, maxTaskLength);

		//assume the first app has the lowest and the last app has the highest task arrival rate
		//double minTaskArrivalRate = lookupTable[0][2];
		//double maxTaskArrivalRate = lookupTable[lookupTable.length-1][2];
		GTH = new GameTheoryHelper(0, 20, numOfMobileDevice);
	}

	@Override
	public int getDeviceToOffload(Task task) {
		int result = 0;

		double avgEdgeUtilization = SimManager.getInstance().getEdgeServerManager().getAvgUtilization();
		double avgCloudUtilization = SimManager.getInstance().getCloudServerManager().getAvgUtilization();

		VehicularNetworkModel networkModel = (VehicularNetworkModel)SimManager.getInstance().getNetworkModel();
		double wanUploadDelay = networkModel.estimateUploadDelay(NETWORK_DELAY_TYPES.WAN_DELAY, task);
		double wanDownloadDelay = networkModel.estimateDownloadDelay(NETWORK_DELAY_TYPES.WAN_DELAY, task);

		double gsmUploadDelay = networkModel.estimateUploadDelay(NETWORK_DELAY_TYPES.GSM_DELAY, task);
		double gsmDownloadDelay = networkModel.estimateDownloadDelay(NETWORK_DELAY_TYPES.GSM_DELAY, task);

		double wlanUploadDelay = networkModel.estimateUploadDelay(NETWORK_DELAY_TYPES.WLAN_DELAY, task);
		double wlanDownloadDelay =networkModel.estimateDownloadDelay(NETWORK_DELAY_TYPES.WLAN_DELAY, task);

		int options[] = {
				EDGE_DATACENTER,
				CLOUD_DATACENTER_VIA_RSU,
				CLOUD_DATACENTER_VIA_GSM
		};

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

		if (policy.equals("AI_BASED")) {
			WekaWrapper weka = WekaWrapper.getInstance();

			boolean predictedResultForEdge = weka.handleClassification(EDGE_DATACENTER,
					new double[] {trainerLogger.getOffloadStat(EDGE_DATACENTER-1),
							task.getCloudletLength(), wlanUploadDelay,
							wlanDownloadDelay, avgEdgeUtilization});

			boolean predictedResultForCloudViaRSU = weka.handleClassification(CLOUD_DATACENTER_VIA_RSU,
					new double[] {trainerLogger.getOffloadStat(CLOUD_DATACENTER_VIA_RSU-1),
							wanUploadDelay, wanDownloadDelay});

			boolean predictedResultForCloudViaGSM = weka.handleClassification(CLOUD_DATACENTER_VIA_GSM,
					new double[] {trainerLogger.getOffloadStat(CLOUD_DATACENTER_VIA_GSM-1),
							gsmUploadDelay, gsmDownloadDelay});

			double predictedServiceTimeForEdge = Double.MAX_VALUE;
			double predictedServiceTimeForCloudViaRSU = Double.MAX_VALUE;
			double predictedServiceTimeForCloudViaGSM = Double.MAX_VALUE;

			if(predictedResultForEdge)
				predictedServiceTimeForEdge = weka.handleRegression(EDGE_DATACENTER,
						new double[] {task.getCloudletLength(), avgEdgeUtilization});

			if(predictedResultForCloudViaRSU)
				predictedServiceTimeForCloudViaRSU = weka.handleRegression(CLOUD_DATACENTER_VIA_RSU,
						new double[] {task.getCloudletLength(), wanUploadDelay, wanDownloadDelay});

			if(predictedResultForCloudViaGSM)
				predictedServiceTimeForCloudViaGSM = weka.handleRegression(CLOUD_DATACENTER_VIA_GSM,
						new double[] {task.getCloudletLength(), gsmUploadDelay, gsmDownloadDelay});

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

			trainerLogger.addOffloadStat(result-1);
		}
		else if (policy.equals("AI_TRAINER")) {
			double probabilities[] = null;
			if(task.getTaskType() == 0)
				probabilities = new double[] {0.60, 0.23, 0.17};
			else if(task.getTaskType() == 1)
				probabilities = new double[] {0.30, 0.53, 0.17};
			else
				probabilities = new double[] {0.23, 0.60, 0.17};

			double randomNumber = SimUtils.getRandomDoubleNumber(0, 1);
			double lastPercentagte = 0;
			boolean resultFound = false;
			for(int i=0; i<probabilities.length; i++) {
				if(randomNumber <= probabilities[i] + lastPercentagte) {
					result = options[i];
					resultFound = true;

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
		else if (policy.equals("MAB")) {
			if(!MAB.isInitialized()){
				double expectedProcessingDealyOnCloud = task.getCloudletLength() /
						SimSettings.getInstance().getMipsForCloudVM();

				//All Edge VMs are identical, just get MIPS value from the first VM
				double expectedProcessingDealyOnEdge = task.getCloudletLength() /
						SimManager.getInstance().getEdgeServerManager().getVmList(0).get(0).getMips();

				double[] expectedDelays = {
						wlanUploadDelay + wlanDownloadDelay + expectedProcessingDealyOnEdge,
						wanUploadDelay + wanDownloadDelay + expectedProcessingDealyOnCloud,
						gsmUploadDelay + gsmDownloadDelay + expectedProcessingDealyOnCloud
				};

				MAB.initialize(expectedDelays, task.getCloudletLength());
			}

			result = options[MAB.runUCB(task.getCloudletLength())];
		}
		else if (policy.equals("GAME_THEORY")) {
			//All Edge VMs are identical, just get MIPS value from the first VM
			double expectedProcessingDealyOnEdge = task.getCloudletLength() /
					SimManager.getInstance().getEdgeServerManager().getVmList(0).get(0).getMips();

			expectedProcessingDealyOnEdge *= 100 / (100 - avgEdgeUtilization);

			double expectedEdgeDelay = expectedProcessingDealyOnEdge + 
					wlanUploadDelay + wlanDownloadDelay;


			double expectedProcessingDealyOnCloud = task.getCloudletLength() /
					SimSettings.getInstance().getMipsForCloudVM();

			expectedProcessingDealyOnCloud *= 100 / (100 - avgCloudUtilization);

			boolean isGsmFaster = SimUtils.getRandomDoubleNumber(0, 1) < 0.5;
			double expectedCloudDelay = expectedProcessingDealyOnCloud +	
					(isGsmFaster ? gsmUploadDelay : wanUploadDelay) +
					(isGsmFaster ? gsmDownloadDelay : wanDownloadDelay);

			double taskArrivalRate = SimSettings.getInstance().getTaskLookUpTable()[task.getTaskType()][2];
			double maxDelay = SimSettings.getInstance().getTaskLookUpTable()[task.getTaskType()][13] * (double)6;

			double Pi = GTH.getPi(task.getMobileDeviceId(), taskArrivalRate, expectedEdgeDelay, expectedCloudDelay, maxDelay);

			double randomNumber = SimUtils.getRandomDoubleNumber(0, 1);

			if(Pi < randomNumber)
				result = EDGE_DATACENTER;
			else
				result = (isGsmFaster ? CLOUD_DATACENTER_VIA_GSM : CLOUD_DATACENTER_VIA_RSU);
		}
		else if (policy.equals("PREDICTIVE")) {		
			//initial probability of different computing paradigms
			double probabilities[] = {0.34, 0.33, 0.33};

			//do not use predictive offloading during warm-up period
			if(CloudSim.clock() > SimSettings.getInstance().getWarmUpPeriod()) {
				/*
				 * failureRate_i = 100 * numOfFailedTask / (numOfFailedTask + numOfSuccessfulTask)
				 */
				double failureRates[] = {
						statisticLogger.getFailureRate(options[0]),
						statisticLogger.getFailureRate(options[1]),
						statisticLogger.getFailureRate(options[2])
				};

				double serviceTimes[] = {
						statisticLogger.getServiceTime(options[0]),
						statisticLogger.getServiceTime(options[1]),
						statisticLogger.getServiceTime(options[2])
				};

				double failureRateScores[] = {0, 0, 0};
				double serviceTimeScores[] = {0, 0, 0};

				//scores are calculated inversely by failure rate and service time
				//lower failure rate and service time is better
				for(int i=0; i<probabilities.length; i++) {
					/*
					 * failureRateScore_i = 1 / (failureRate_i / sum(failureRate))
					 * failureRateScore_i = sum(failureRate) / failureRate_i
					 */
					failureRateScores[i] = DoubleStream.of(failureRates).sum() / failureRates[i];
					/*
					 * serviceTimeScore_i = 1 / (serviceTime_i / sum(serviceTime))
					 * serviceTimeScore_i = sum(serviceTime) / serviceTime_i
					 */
					serviceTimeScores[i] = DoubleStream.of(serviceTimes).sum() / serviceTimes[i];
				}

				for(int i=0; i<probabilities.length; i++) {
					if(DoubleStream.of(failureRates).sum() > 0.3)
						probabilities[i] = failureRateScores[i] / DoubleStream.of(failureRateScores).sum();
					else
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

	@Override
	public Vm getVmToOffload(Task task, int deviceId) {
		Vm selectedVM = null;

		if (deviceId == CLOUD_DATACENTER_VIA_GSM || deviceId == CLOUD_DATACENTER_VIA_RSU) {
			int numOfCloudHosts = SimSettings.getInstance().getNumOfCloudHost();
			int hostIndex = (cloudVmCounter / numOfCloudHosts) % numOfCloudHosts;
			int vmIndex = cloudVmCounter % SimSettings.getInstance().getNumOfCloudVMsPerHost();;

			selectedVM = SimManager.getInstance().getCloudServerManager().getVmList(hostIndex).get(vmIndex);

			cloudVmCounter++;
			cloudVmCounter = cloudVmCounter % SimSettings.getInstance().getNumOfCloudVMs();

		}
		else if (deviceId == EDGE_DATACENTER) {
			int numOfEdgeVMs = SimSettings.getInstance().getNumOfEdgeVMs();
			int numOfEdgeHosts = SimSettings.getInstance().getNumOfEdgeHosts();
			int vmPerHost = numOfEdgeVMs / numOfEdgeHosts;

			int hostIndex = (edgeVmCounter / vmPerHost) % numOfEdgeHosts;
			int vmIndex = edgeVmCounter % vmPerHost;

			selectedVM = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex).get(vmIndex);

			edgeVmCounter++;
			edgeVmCounter = edgeVmCounter % numOfEdgeVMs;
		}
		else {
			SimLogger.printLine("Unknow device id! Terminating simulation...");
			System.exit(1);
		}
		return selectedVM;
	}

	@Override
	public void startEntity() {
		if(policy.equals("PREDICTIVE")) {
			schedule(getId(), SimSettings.CLIENT_ACTIVITY_START_TIME +
					OrchestratorStatisticLogger.PREDICTION_WINDOW_UPDATE_INTERVAL, 
					UPDATE_PREDICTION_WINDOW);
		}
	}

	@Override
	public void shutdownEntity() {
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

	public void taskCompleted(Task task, double serviceTime) {
		if(policy.equals("AI_TRAINER"))
			trainerLogger.addSuccessStat(task, serviceTime);

		if(policy.equals("PREDICTIVE"))
			statisticLogger.addSuccessStat(task, serviceTime);

		if(policy.equals("MAB"))
			MAB.updateUCB(task, serviceTime);
	}

	public void taskFailed(Task task) {
		if(policy.equals("AI_TRAINER"))
			trainerLogger.addFailStat(task);

		if(policy.equals("PREDICTIVE"))
			statisticLogger.addFailStat(task);

		if(policy.equals("MAB"))
			MAB.updateUCB(task, 0);
	}

	public void openTrainerOutputFile() {
		trainerLogger.openTrainerOutputFile();
	}

	public void closeTrainerOutputFile() {
		trainerLogger.closeTrainerOutputFile();
	}
}
