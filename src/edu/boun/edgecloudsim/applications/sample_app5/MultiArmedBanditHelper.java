package edu.boun.edgecloudsim.applications.sample_app5;

import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.utils.SimLogger;

public class MultiArmedBanditHelper {
	// Confidence parameter for Upper Confidence Bound algorithm
	private static final double Beta = 1;

	private double MAX_TASK_LENGTH;
	private double MIN_TASK_LENGTH;

	// Number of tasks offloaded to each datacenter type: [EDGE, CLOUD_VIA_RSU, CLOUD_VIA_GSM]
	private int[] K_tn = {0, 0, 0};
	
	// Empirical delay performance (utility) for each datacenter type
	private double[] U_tn = {0, 0, 0};
	
	// Current round/iteration number for UCB algorithm
	private int t = 1;
	
	// Flag indicating whether the helper has been initialized with initial estimates
	private boolean isInitialized;

	/**
	 * Constructor to initialize Multi-Armed Bandit helper for datacenter selection.
	 * 
	 * @param minTaskLength minimum task length for normalization
	 * @param maxTaskLength maximum task length for normalization
	 */
	public MultiArmedBanditHelper(double minTaskLength, double maxTaskLength) {
		MIN_TASK_LENGTH = minTaskLength;
		MAX_TASK_LENGTH = maxTaskLength;
		isInitialized = false;
	}

	/**
	 * Checks if the MAB helper has been initialized with initial performance estimates.
	 * 
	 * @return true if initialized, false otherwise
	 */
	public synchronized boolean isInitialized(){
		return isInitialized;
	}

	/**
	 * Initializes the MAB algorithm with initial delay estimates for each datacenter type.
	 * Sets initial utility values and marks the helper as initialized.
	 * 
	 * @param expectedDelays array of expected delays for [EDGE, CLOUD_VIA_RSU, CLOUD_VIA_GSM]
	 * @param taskLength current task length for normalization
	 */
	public synchronized void initialize(double[] expectedDelays, double taskLength) {
		for(int i=0; i<K_tn.length; i++) {
			// Initialize each arm with one virtual observation
			K_tn[i] = 1;
			// Calculate initial utility as delay per unit task length
			U_tn[i] = expectedDelays[i] / taskLength;
		}
		isInitialized = true;
	}

	/**
	 * Selects the best datacenter using Upper Confidence Bound (UCB) algorithm.
	 * The algorithm balances exploitation of best-known options with exploration of uncertain ones.
	 * 
	 * @param taskLength current task length (used for confidence interval calculation)
	 * @return selected datacenter type: 0=EDGE_DATACENTER, 1=CLOUD_DATACENTER_VIA_RSU, 2=CLOUD_DATACENTER_VIA_GSM
	 */
	public synchronized int runUCB(double taskLength) {
		int result = 0;
		double minUtilityFunctionValue = Double.MAX_VALUE;

		// Evaluate each datacenter option using UCB formula
		for(int i=0; i<K_tn.length; i++) {
			// UCB formula: μ - confidence_interval (lower bound for minimization)
			double U_t = U_tn[i] - Math.sqrt((Beta * (1-normalizeTaskLength(taskLength)) * Math.log(t)) / K_tn[i]);
			
			// Select the datacenter with minimum expected delay (including confidence interval)
			if(U_t < minUtilityFunctionValue){
				minUtilityFunctionValue = U_t;
				result = i;
			}
		}

		return result;
	}

	/**
	 * Updates the UCB algorithm with observed performance from a completed task.
	 * This method implements online learning by updating utility estimates.
	 * 
	 * @param task completed task with datacenter assignment information
	 * @param serviceTime observed service time (delay) for the task
	 */
	public synchronized void updateUCB(Task task, double serviceTime) {
		double taskLength = task.getCloudletLength();
		int choice = 0;
		
		// Map datacenter ID to array index
		switch (task.getAssociatedDatacenterId()) {
		case VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_GSM:
			choice = 2;
			break;
		case VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_RSU:
			choice = 1;
			break;
		case VehicularEdgeOrchestrator.EDGE_DATACENTER:
			choice = 0;
			break;
		default:
			SimLogger.printLine("Unknown datacenter id. Terminating simulation...");
			System.exit(1);
			break;
		}

		// Handle cases where service time is zero (task failed or no measurement)
		if(serviceTime == 0) {
			// Use default values based on task type as fallback
			if(task.getTaskType() == 0)
				serviceTime =  1.25;
			else if(task.getTaskType() == 1)
				serviceTime =  2;
			else
				serviceTime =  2.75;
		}

		// Update utility using incremental average: new_avg = (old_avg * n + new_value) / n
		U_tn[choice] = (U_tn[choice] * K_tn[choice] + (serviceTime / taskLength)) / (K_tn[choice]);
		K_tn[choice] = K_tn[choice] + 1;

		// Sanity check for numerical stability
		if(U_tn[choice] == Double.POSITIVE_INFINITY) {
			SimLogger.printLine("Unexpected MAB calculation! Utility function goes to infinity. Terminating simulation...");
			System.exit(1);
		}

		// Increment round counter for confidence interval calculation
		t++;
	}

	/**
	 * Normalizes the task length to a value between 0 and 1.
	 * Used in UCB confidence interval calculation to weight exploration vs exploitation.
	 * 
	 * @param taskLength raw task length
	 * @return normalized task length in range [0,1]
	 */
	private double normalizeTaskLength(double taskLength) {
		double result = (taskLength - MIN_TASK_LENGTH) / (MAX_TASK_LENGTH - MIN_TASK_LENGTH);
		return Math.max(Math.min(result,1),0);
	}
}
