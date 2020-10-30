package edu.boun.edgecloudsim.applications.sample_app5;

public class GameTheoryHelper {

	private static double PRICING_FACTOR = 0.6;
	private double MAX_TASK_ARRIVAL_RATE;
	private double MIN_TASK_ARRIVAL_RATE;

	//the offloading probability vector p
	private double[] pVector;

	//the task arrival rate vector
	private double[] arrivalRateVector;

	public GameTheoryHelper(double minTaskArrivalRate, double maxTaskArrivalRate, int numOfVehicles) {
		MAX_TASK_ARRIVAL_RATE = maxTaskArrivalRate;
		MIN_TASK_ARRIVAL_RATE = minTaskArrivalRate;

		pVector = new double[numOfVehicles];
		for (int i = 0; i < pVector.length; i++)
			pVector[i] = 0.33;

		arrivalRateVector = new double[numOfVehicles];
		for (int i = 0; i < arrivalRateVector.length; i++)
			arrivalRateVector[i] = 0.5;
	}

	/**
	 * This method is used to find the best choice via Nash equilibrium strategy. 
	 * 
	 * @param vehicleID  vehicle id
	 * @param taskArrivalRate  task arrival rate
	 * @param expectedEdgeDelay  expected delay for edge offloading
	 * @param expectedCloudDelay  expected delay for cloud offloading
	 * @return double probability of offloading to cloud
	 */
	public synchronized double getPi(int vehicleID, double taskArrivalRate, double expectedEdgeDelay, double expectedCloudDelay, double maxDelay) {
		pVector[vehicleID] = (expectedEdgeDelay - expectedCloudDelay) / (2 * PRICING_FACTOR * maxDelay * (1 - multiplyArray(vehicleID)));
		arrivalRateVector[vehicleID] = normalizeTaskArrivalRate(taskArrivalRate);

		if(pVector[vehicleID] <= 0)
			pVector[vehicleID] = 0.01;
		else if(pVector[vehicleID] >= 1)
			pVector[vehicleID] = 0.99;

		//SimLogger.printLine("P" + vehicleID + ": " + pVector[vehicleID]);

		return pVector[vehicleID];
	}

	/**
	 * This method normalizes the task arrival rate between 0-1. 
	 * 
	 * @param taskArrivalRate  task arrival rate
	 * @return double normalized task length
	 */
	private double normalizeTaskArrivalRate(double taskArrivalRate) {
		double result = (taskArrivalRate - MIN_TASK_ARRIVAL_RATE) / (MAX_TASK_ARRIVAL_RATE - MIN_TASK_ARRIVAL_RATE);
		return Math.max(Math.min(result,1),0);
	}

	private double multiplyArray(int excludedIndex) {
		double pro = 1; 
		for (int i = 0; i < pVector.length && i != excludedIndex; i++)
			pro *= (1 - arrivalRateVector[i] * pVector[i]);

		if(pro == Double.POSITIVE_INFINITY)
			pro = 0.99;
		else if(pro == Double.NEGATIVE_INFINITY)
			pro = 0.01;

		return pro; 
	}
}
