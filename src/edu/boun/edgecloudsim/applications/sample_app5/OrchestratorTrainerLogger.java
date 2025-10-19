package edu.boun.edgecloudsim.applications.sample_app5;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.utils.SimLogger;

/**
 * Logger class for collecting machine learning training data during AI_TRAINER mode
 * Records task decisions, network delays, resource utilization, and outcomes
 * Generates CSV files for training AI-based orchestration models
 */
public class OrchestratorTrainerLogger {
	private static final double STAT_WINDOW = 1; // Statistics window size in seconds
	private static final String DELIMITER = ",";
	private Map<Integer, TrainerItem> trainerMap;
	private List<Double>[] TaskOffloadStats;

	private BufferedWriter learnerBW = null;

	/**
	 * Data container for training samples
	 * Stores decision context and network conditions at task arrival time
	 */
	class TrainerItem {
		int selectedDatacenter;        // Orchestrator's decision
		int numOffloadedTask;         // Number of tasks offloaded to this datacenter recently
		double avgEdgeUtilization;    // Average edge server utilization at decision time
		double wanUploadDelay;        // WAN upload delay estimate
		double wanDownloadDelay;      // WAN download delay estimate
		double gsmUploadDelay;        // GSM upload delay estimate
		double gsmDownloadDelay;      // GSM download delay estimate
		double wlanUploadDelay;       // WLAN upload delay estimate
		double wlanDownloadDelay;     // WLAN download delay estimate

		TrainerItem(int selectedDatacenter,
				int numOffloadedTask, double avgEdgeUtilization,
				double wanUploadDelay, double wanDownloadDelay,
				double gsmUploadDelay, double gsmDownloadDelay,
				double wlanUploadDelay, double wlanDownloadDelay)
		{
			this.selectedDatacenter = selectedDatacenter;
			this.avgEdgeUtilization = avgEdgeUtilization;
			this.numOffloadedTask = numOffloadedTask;
			this.wanUploadDelay = wanUploadDelay;
			this.wanDownloadDelay = wanDownloadDelay;
			this.gsmUploadDelay = gsmUploadDelay;
			this.gsmDownloadDelay = gsmDownloadDelay;
			this.wlanUploadDelay = wlanUploadDelay;
			this.wlanDownloadDelay = wlanDownloadDelay;
		}
	}

	/**
	 * Constructor initializes data structures for training data collection
	 */
	@SuppressWarnings("unchecked")
	public OrchestratorTrainerLogger() {
		// Map to store temporary training items until task completion
		trainerMap = new HashMap<Integer, TrainerItem>();

		// Statistics for each datacenter type (Edge, Cloud via RSU, Cloud via GSM)
		TaskOffloadStats = (ArrayList<Double>[])new ArrayList[3];
		TaskOffloadStats[0] = new ArrayList<Double>(); // Edge datacenter stats
		TaskOffloadStats[1] = new ArrayList<Double>(); // Cloud via RSU stats  
		TaskOffloadStats[2] = new ArrayList<Double>(); // Cloud via GSM stats
	}

	/**
	 * Creates and opens CSV file for training data output
	 * Writes header row with feature names for machine learning
	 */
	public void openTrainerOutputFile() {
		try {
			int numOfMobileDevices = SimManager.getInstance().getNumOfMobileDevice();
			String learnerOutputFile = SimLogger.getInstance().getOutputFolder() +
					"/" + numOfMobileDevices + "_learnerOutputFile.cvs";
			File learnerFile = new File(learnerOutputFile);
			FileWriter learnerFW = new FileWriter(learnerFile);
			learnerBW = new BufferedWriter(learnerFW);

			// Create CSV header with all features used for ML training
			String line = "Decision"              // Target variable: orchestration decision
					+ DELIMITER + "Result"        // Outcome: success/fail
					+ DELIMITER + "ServiceTime"   // Total service time (target for regression)
					+ DELIMITER + "ProcessingTime" // Processing time only
					+ DELIMITER + "VehicleLocation" // Vehicle location (WLAN ID)
					+ DELIMITER + "SelectedHostID"  // Host where task was executed
					+ DELIMITER + "TaskLength"      // Computational demand (MI)
					+ DELIMITER + "TaskInput"       // Input data size (bytes)
					+ DELIMITER + "TaskOutput"      // Output data size (bytes)
					+ DELIMITER + "WANUploadDelay"  // Network delay features
					+ DELIMITER + "WANDownloadDelay"
					+ DELIMITER + "GSMUploadDelay"
					+ DELIMITER + "GSMDownloadDelay"
					+ DELIMITER + "WLANUploadDelay"
					+ DELIMITER + "WLANDownloadDelay"
					+ DELIMITER + "AvgEdgeUtilization" // Resource utilization feature
					+ DELIMITER + "NumOffloadedTask";  // Load balancing feature

			// Optional: Individual edge host utilization features
			//for(int i=1; i<=SimSettings.getInstance().getNumOfEdgeHosts(); i++)
			//	line += DELIMITER + "Avg Edge(" + i + ") Utilization";

			learnerBW.write(line);
			learnerBW.newLine();

		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	/**
	 * Closes the training data output file
	 */
	public void closeTrainerOutputFile() {
		try {
			learnerBW.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Writes a complete training sample to CSV file
	 * @param trainerItem Training context captured at task arrival
	 * @param task Completed or failed task
	 * @param result Success (true) or failure (false) outcome
	 * @param serviceTime Total service time including network and processing delays
	 */
	public void saveStat(TrainerItem trainerItem, Task task,
			boolean result, double serviceTime) {
		String line = "";

		// Convert datacenter ID to human-readable string
		switch(trainerItem.selectedDatacenter){
		case VehicularEdgeOrchestrator.EDGE_DATACENTER:
			line = "EDGE";
			break;
		case VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_RSU:
			line = "CLOUD_VIA_RSU";
			break;
		case VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_GSM:
			line = "CLOUD_VIA_GSM";
			break;
		default:
			SimLogger.printLine("Unknown datacenter type");
			System.exit(1);
			break;
		}

		// Extract task execution details
		int submittedLocation = task.getSubmittedLocation().getServingWlanId();
		Double processingTime = task.getFinishTime()-task.getExecStartTime();

		// Build complete training sample row
		line  +=  DELIMITER + (result == true ? "success" : "fail")
				+ DELIMITER + Double.toString(serviceTime)
				+ DELIMITER + Double.toString(processingTime)
				+ DELIMITER + Integer.toString(submittedLocation)
				+ DELIMITER + Integer.toString(task.getAssociatedHostId())
				+ DELIMITER + Long.toString(task.getCloudletLength())
				+ DELIMITER + Long.toString(task.getCloudletFileSize())
				+ DELIMITER + Long.toString(task.getCloudletOutputSize())
				+ DELIMITER + Double.toString(trainerItem.wanUploadDelay)
				+ DELIMITER + Double.toString(trainerItem.wanDownloadDelay)
				+ DELIMITER + Double.toString(trainerItem.gsmUploadDelay)
				+ DELIMITER + Double.toString(trainerItem.gsmDownloadDelay)
				+ DELIMITER + Double.toString(trainerItem.wlanUploadDelay)
				+ DELIMITER + Double.toString(trainerItem.wlanDownloadDelay)
				+ DELIMITER + Double.toString(trainerItem.avgEdgeUtilization)
				+ DELIMITER + Integer.toString(trainerItem.numOffloadedTask);

		// Optional: Per-host utilization features
		//for(int i=0; i<trainerItem.edgeUtilizations.length; i++)
		//	line += DELIMITER + Double.toString(trainerItem.edgeUtilizations[i]);

		try {
			learnerBW.write(line);
			learnerBW.newLine();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Records training context at task arrival time
	 * @param id Task ID for later matching with completion/failure
	 * @param selectedDatacenter Orchestrator's decision
	 * @param wanUploadDelay WAN upload delay estimate
	 * @param wanDownloadDelay WAN download delay estimate  
	 * @param gsmUploadDelay GSM upload delay estimate
	 * @param gsmDownloadDelay GSM download delay estimate
	 * @param wlanUploadDelay WLAN upload delay estimate
	 * @param wlanDownloadDelay WLAN download delay estimate
	 */
	public void addStat(int id, int selectedDatacenter,
			double wanUploadDelay, double wanDownloadDelay,
			double gsmUploadDelay, double gsmDownloadDelay,
			double wlanUploadDelay, double wlanDownloadDelay){

		// Update offload statistics for this datacenter type
		addOffloadStat(selectedDatacenter-1);
		int numOffloadedTasks = getOffloadStat(selectedDatacenter-1);

		// Calculate current edge utilization across all hosts
		int numberOfHost = SimSettings.getInstance().getNumOfEdgeHosts();
		double totalUtlization = 0;
		double[] edgeUtilizations = new double[numberOfHost];
		for(int hostIndex=0; hostIndex<numberOfHost; hostIndex++){
			List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex);

			double utilization=0;
			for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
				utilization += vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
			}
			totalUtlization += utilization;

			edgeUtilizations[hostIndex] = utilization / (double)(vmArray.size());
		}

		double avgEdgeUtilization = totalUtlization / SimSettings.getInstance().getNumOfEdgeVMs();

		// Store training context for later retrieval when task completes
		trainerMap.put(id,
				new TrainerItem(selectedDatacenter,
						numOffloadedTasks, avgEdgeUtilization,
						wanUploadDelay, wanDownloadDelay,
						gsmUploadDelay, gsmDownloadDelay,
						wlanUploadDelay, wlanDownloadDelay
						)
				);
	}

	/**
	 * Records successful task completion with training context
	 * @param task Successfully completed task
	 * @param serviceTime Total service time for regression training
	 */
	public synchronized void addSuccessStat(Task task, double serviceTime) {
		TrainerItem trainerItem = trainerMap.remove(task.getCloudletId());
		saveStat(trainerItem, task, true, serviceTime);
	}

	/**
	 * Records task failure with training context
	 * @param task Failed task
	 */
	public synchronized void addFailStat(Task task) {
		TrainerItem trainerItem = trainerMap.remove(task.getCloudletId());
		saveStat(trainerItem, task, false, 0);
	}

	/**
	 * Adds timestamp to offload statistics for load tracking
	 * @param datacenterIdx Index of datacenter (0=edge, 1=cloud via RSU, 2=cloud via GSM)
	 */
	public synchronized void addOffloadStat(int datacenterIdx) {
		double time = CloudSim.clock();
		// Remove old entries outside the statistics window
		for (Iterator<Double> iter = TaskOffloadStats[datacenterIdx].iterator(); iter.hasNext(); ) {
			if (iter.next() + STAT_WINDOW < time)
				iter.remove();
			else
				break;
		}
		TaskOffloadStats[datacenterIdx].add(time);
	}

	/**
	 * Returns number of recent offload decisions for a datacenter type
	 * @param datacenterIdx Index of datacenter type
	 * @return Number of tasks offloaded to this datacenter in recent time window
	 */
	public synchronized int getOffloadStat(int datacenterIdx) {
		return TaskOffloadStats[datacenterIdx].size();
	}
}
