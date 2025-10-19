package edu.boun.edgecloudsim.applications.sample_app5;

import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.utils.SimLogger;

public class OrchestratorStatisticLogger {
	// Number of historical time windows to maintain for performance prediction
	public static final int NUMBER_OF_HISTORY_WINDOW = 4;
	
	// Time interval (in seconds) for updating prediction windows
	public static final double PREDICTION_WINDOW_UPDATE_INTERVAL = 0.125;

	// Array storing statistics from previous time windows for trend analysis
	private StatisticWrapper statForPreviousWindow[];
	
	// Current time window statistics being collected
	private StatisticWrapper statForCurrentWindow;

	/**
	 * Inner class to store performance statistics for a single datacenter type.
	 */
	class StatItem {
		long numOfCompletedTasks;     // Count of successfully completed tasks
		long numOfFailedTasks;        // Count of failed tasks
		double totalServiceTime;      // Cumulative service time for completed tasks

		/**
		 * Calculates the failure rate as a percentage.
		 * 
		 * @return failure rate percentage (0-100), or 0.1 as default if no failures
		 */
		double getFailureRate() {
			double failureRate = 0.1; // Default minimal failure rate
			if(numOfFailedTasks != 0)
				failureRate = ((double)100 * numOfFailedTasks) / (numOfCompletedTasks + numOfFailedTasks);

			return failureRate; 
		}

		/**
		 * Calculates the average service time per completed task.
		 * 
		 * @return average service time in seconds, or 0.01 as default if no completed tasks
		 */
		double getAvgServiceTime() {
			double serviceTime = 0.01; // Default minimal service time
			if(numOfCompletedTasks != 0)
				serviceTime = totalServiceTime/numOfCompletedTasks;

			return serviceTime;
		}
	}

	/**
	 * Wrapper class that holds statistics for all three datacenter types.
	 */
	class StatisticWrapper {
		StatItem edgeStat;           // Statistics for edge datacenter
		StatItem cloudViaRsuStat;    // Statistics for cloud via RSU
		StatItem cloudViaGsmStat;    // Statistics for cloud via GSM

		/**
		 * Gets the failure rate for a specific datacenter type.
		 * 
		 * @param targetDatacenter datacenter type identifier
		 * @return failure rate percentage for the specified datacenter
		 */
		double getFailureRate(int targetDatacenter) {
			double failureRate = 0;

			switch (targetDatacenter) {
			case VehicularEdgeOrchestrator.EDGE_DATACENTER:
				failureRate = edgeStat.getFailureRate();
				break;
			case VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_RSU:
				failureRate = cloudViaRsuStat.getFailureRate();
				break;
			case VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_GSM:
				failureRate = cloudViaGsmStat.getFailureRate();
				break;
			default:
				SimLogger.printLine("Unknown target datacenter in predictive orchestration policy! Terminating simulation...");
				System.exit(1);
				break;
			}

			return failureRate; 
		}

		/**
		 * Gets the average service time for a specific datacenter type.
		 * 
		 * @param targetDatacenter datacenter type identifier
		 * @return average service time in seconds for the specified datacenter
		 */
		double getAvgServiceTime(int targetDatacenter) {
			double serviceTime = 0.01; // Default minimal service time

			switch (targetDatacenter) {
			case VehicularEdgeOrchestrator.EDGE_DATACENTER:
				serviceTime = edgeStat.getAvgServiceTime();
				break;
			case VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_RSU:
				serviceTime = cloudViaRsuStat.getAvgServiceTime();
				break;
			case VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_GSM:
				serviceTime = cloudViaGsmStat.getAvgServiceTime();
				break;
			default:
				SimLogger.printLine("Unknown target datacenter in predictive orchestration policy! Terminating simulation...");
				System.exit(1);
				break;
			}

			return serviceTime;
		}
	}

	/**
	 * Constructor initializes the statistics logger with empty statistics windows.
	 * Creates current window and historical windows for trend analysis.
	 */
	public OrchestratorStatisticLogger() {
		// Initialize current statistics window
		statForCurrentWindow = new StatisticWrapper();		
		statForCurrentWindow.cloudViaRsuStat = new StatItem();
		statForCurrentWindow.cloudViaGsmStat = new StatItem();
		statForCurrentWindow.edgeStat = new StatItem();

		// Initialize array of previous statistics windows for historical analysis
		statForPreviousWindow = new StatisticWrapper[NUMBER_OF_HISTORY_WINDOW];
		for(int i = 0; i< NUMBER_OF_HISTORY_WINDOW; i++){
			statForPreviousWindow[i] = new StatisticWrapper();

			statForPreviousWindow[i].cloudViaRsuStat = new StatItem();
			statForPreviousWindow[i].cloudViaGsmStat = new StatItem();
			statForPreviousWindow[i].edgeStat = new StatItem();
		}
	}

	/**
	 * Records a successful task completion for statistical analysis.
	 * 
	 * @param task completed task
	 * @param serviceTime observed service time in seconds
	 */
	public synchronized void addSuccessStat(Task task, double serviceTime) {
		addStat(true, serviceTime, task.getAssociatedDatacenterId());
	}

	/**
	 * Records a failed task for statistical analysis.
	 * 
	 * @param task failed task
	 */
	public synchronized void addFailStat(Task task) {
		addStat(false, 0, task.getAssociatedDatacenterId());
	}

	private synchronized void addStat(boolean isCompleted, double serviceTime, int targetDatacenter) {
		StatItem statItem = null;

		switch (targetDatacenter) {
		case VehicularEdgeOrchestrator.EDGE_DATACENTER:
			statItem = statForCurrentWindow.edgeStat;
			break;
		case VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_RSU:
			statItem = statForCurrentWindow.cloudViaRsuStat;
			break;
		case VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_GSM:
			statItem = statForCurrentWindow.cloudViaGsmStat;
			break;
		default:
			SimLogger.printLine("Unknow target datacenter in predictive orchestration policy! Terminating simulation...");
			System.exit(1);
			break;
		}

		if(isCompleted) {
			statItem.numOfCompletedTasks++;
			statItem.totalServiceTime += serviceTime;
		}
		else {
			statItem.numOfFailedTasks++;
		}
	}

	public double getFailureRate(int targetDatacenter){
		double result = 0;

		for (int i=0; i<NUMBER_OF_HISTORY_WINDOW; i++) {
			result += (statForPreviousWindow[i].getFailureRate(targetDatacenter) * (double)(NUMBER_OF_HISTORY_WINDOW - i));
		}

		return result;
	}

	public double getServiceTime(int targetDatacenter){
		double result = 0;

		for (int i=0; i<NUMBER_OF_HISTORY_WINDOW; i++) {
			result += (statForPreviousWindow[i].getAvgServiceTime(targetDatacenter) * (double)(NUMBER_OF_HISTORY_WINDOW - i));
		}

		return result;
	}

	public void switchNewStatWindow() {
		for(int i = NUMBER_OF_HISTORY_WINDOW -2; i>=0; i--){
			statForPreviousWindow[i+1].cloudViaRsuStat.numOfCompletedTasks = statForPreviousWindow[i].cloudViaRsuStat.numOfCompletedTasks;
			statForPreviousWindow[i+1].cloudViaRsuStat.numOfFailedTasks = statForPreviousWindow[i].cloudViaRsuStat.numOfFailedTasks;
			statForPreviousWindow[i+1].cloudViaRsuStat.totalServiceTime = statForPreviousWindow[i].cloudViaRsuStat.totalServiceTime;

			statForPreviousWindow[i+1].cloudViaGsmStat.numOfCompletedTasks = statForPreviousWindow[i].cloudViaGsmStat.numOfCompletedTasks;
			statForPreviousWindow[i+1].cloudViaGsmStat.numOfFailedTasks = statForPreviousWindow[i].cloudViaGsmStat.numOfFailedTasks;
			statForPreviousWindow[i+1].cloudViaGsmStat.totalServiceTime = statForPreviousWindow[i].cloudViaGsmStat.totalServiceTime;

			statForPreviousWindow[i+1].edgeStat.numOfCompletedTasks = statForPreviousWindow[i].edgeStat.numOfCompletedTasks;
			statForPreviousWindow[i+1].edgeStat.numOfFailedTasks = statForPreviousWindow[i].edgeStat.numOfFailedTasks;
			statForPreviousWindow[i+1].edgeStat.totalServiceTime = statForPreviousWindow[i].edgeStat.totalServiceTime;
		}

		statForPreviousWindow[0].cloudViaRsuStat.numOfCompletedTasks = statForCurrentWindow.cloudViaRsuStat.numOfCompletedTasks;
		statForPreviousWindow[0].cloudViaRsuStat.numOfFailedTasks = statForCurrentWindow.cloudViaRsuStat.numOfFailedTasks;
		statForPreviousWindow[0].cloudViaRsuStat.totalServiceTime = statForCurrentWindow.cloudViaRsuStat.totalServiceTime;
		statForCurrentWindow.cloudViaRsuStat.numOfCompletedTasks = 0;
		statForCurrentWindow.cloudViaRsuStat.numOfFailedTasks = 0;
		statForCurrentWindow.cloudViaRsuStat.totalServiceTime = 0;

		statForPreviousWindow[0].cloudViaGsmStat.numOfCompletedTasks = statForCurrentWindow.cloudViaGsmStat.numOfCompletedTasks;
		statForPreviousWindow[0].cloudViaGsmStat.numOfFailedTasks = statForCurrentWindow.cloudViaGsmStat.numOfFailedTasks;
		statForPreviousWindow[0].cloudViaGsmStat.totalServiceTime = statForCurrentWindow.cloudViaGsmStat.totalServiceTime;
		statForCurrentWindow.cloudViaGsmStat.numOfCompletedTasks = 0;
		statForCurrentWindow.cloudViaGsmStat.numOfFailedTasks = 0;
		statForCurrentWindow.cloudViaGsmStat.totalServiceTime = 0;

		statForPreviousWindow[0].edgeStat.numOfCompletedTasks = statForCurrentWindow.edgeStat.numOfCompletedTasks;
		statForPreviousWindow[0].edgeStat.numOfFailedTasks = statForCurrentWindow.edgeStat.numOfFailedTasks;
		statForPreviousWindow[0].edgeStat.totalServiceTime = statForCurrentWindow.edgeStat.totalServiceTime;
		statForCurrentWindow.edgeStat.numOfCompletedTasks = 0;
		statForCurrentWindow.edgeStat.numOfFailedTasks = 0;
		statForCurrentWindow.edgeStat.totalServiceTime = 0;
	}
}
