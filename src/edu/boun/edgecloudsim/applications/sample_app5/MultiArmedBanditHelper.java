package edu.boun.edgecloudsim.applications.sample_app5;

import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.utils.SimLogger;

public class MultiArmedBanditHelper {
	private static final double Beta = 1;

	private double MAX_TASK_LENGTH;
	private double MIN_TASK_LENGTH;

	private int[] K_tn = {0, 0, 0}; //number of task offloaded to EDGE_DATACENTER, CLOUD_DATACENTER_VIA_RSU, CLOUD_DATACENTER_VIA_GSM
	private double[] U_tn = {0, 0, 0}; //empirical delay performance of the related datacenters
	private int t = 1; //round
	private boolean isInitialized;

	public MultiArmedBanditHelper(double minTaskLength, double maxTaskLength) {
		MIN_TASK_LENGTH = minTaskLength;
		MAX_TASK_LENGTH = maxTaskLength;
		isInitialized = false;
	}

	public synchronized boolean isInitialized(){
		return isInitialized;
	}

	/**
	 * @param expectedDelays expected delay for each WLAN, WAN and GSM
	 * @param taskLength  task length
	 */
	public synchronized void initialize(double[] expectedDelays, double taskLength) {
		for(int i=0; i<K_tn.length; i++) {
			K_tn[i] = 1;
			U_tn[i] = expectedDelays[i] / taskLength;
		}
		isInitialized = true;
	}

	/**
	 * This method is used to find the best choice via Upper Confidence Bound algorithm. 
	 * 
	 * @param taskLength  task length
	 * @return int selected datacenter type; 0=EDGE_DATACENTER, 1=CLOUD_DATACENTER_VIA_RSU, 2=CLOUD_DATACENTER_VIA_GSM
	 */
	public synchronized int runUCB(double taskLength) {
		int result = 0;
		double minUtilityFunctionValue = Double.MAX_VALUE;

		for(int i=0; i<K_tn.length; i++) {
			double U_t = U_tn[i] - Math.sqrt((Beta * (1-normalizeTaskLength(taskLength)) * Math.log(t)) / K_tn[i]);
			if(U_t < minUtilityFunctionValue){
				minUtilityFunctionValue = U_t;
				result = i;
			}
		}

		return result;
	}

	/**
	 * This method is used to find the best choice via Upper Confidence Bound algorithm. 
	 * 
	 * @param task offloaded task
	 * @param serviceTime  observed delay
	 */
	public synchronized void updateUCB(Task task, double serviceTime) {
		double taskLength = task.getCloudletLength();
		int choice = 0;
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

		if(serviceTime == 0) {
			if(task.getTaskType() == 0)
				serviceTime =  1.25;
			else if(task.getTaskType() == 1)
				serviceTime =  2;
			else
				serviceTime =  2.75;
		}

		U_tn[choice] = (U_tn[choice] * K_tn[choice] + (serviceTime / taskLength)) / (K_tn[choice]);
		K_tn[choice] = K_tn[choice] + 1;

		if(U_tn[choice] == Double.POSITIVE_INFINITY) {
			SimLogger.printLine("Unexpected MAB calculation! Utility function goes to infinity. Terminating simulation...");
			System.exit(1);
		}

		t++;
	}

	/**
	 * This method normalizes the task length between 0-1. 
	 * 
	 * @param taskLength  task length
	 * @return double normalized task length
	 */
	private double normalizeTaskLength(double taskLength) {
		double result = (taskLength - MIN_TASK_LENGTH) / (MAX_TASK_LENGTH - MIN_TASK_LENGTH);
		return Math.max(Math.min(result,1),0);
	}
}
