package edu.boun.edgecloudsim.applications.sample_app5;

public class GameTheoryHelper {

	// Pricing factor used in Nash equilibrium calculations
	private static double PRICING_FACTOR = 0.6;
	private double MAX_TASK_ARRIVAL_RATE;
	private double MIN_TASK_ARRIVAL_RATE;

	// The offloading probability vector p for each vehicle
	private double[] pVector;

	// The normalized task arrival rate vector for each vehicle
	private double[] arrivalRateVector;

	/**
	 * Constructor to initialize game theory helper for vehicular edge computing.
	 * 
	 * @param minTaskArrivalRate minimum task arrival rate for normalization
	 * @param maxTaskArrivalRate maximum task arrival rate for normalization
	 * @param numOfVehicles number of vehicles participating in the game
	 */
	public GameTheoryHelper(double minTaskArrivalRate, double maxTaskArrivalRate, int numOfVehicles) {
		MAX_TASK_ARRIVAL_RATE = maxTaskArrivalRate;
		MIN_TASK_ARRIVAL_RATE = minTaskArrivalRate;

		// Initialize probability vector with default value of 0.33 for each vehicle
		pVector = new double[numOfVehicles];
		for (int i = 0; i < pVector.length; i++)
			pVector[i] = 0.33;

		// Initialize arrival rate vector with default value of 0.5 for each vehicle
		arrivalRateVector = new double[numOfVehicles];
		for (int i = 0; i < arrivalRateVector.length; i++)
			arrivalRateVector[i] = 0.5;
	}

	/**
	 * Calculates the optimal cloud offloading probability using Nash equilibrium strategy.
	 * This method implements a game-theoretic approach where vehicles compete for resources.
	 * 
	 * @param vehicleID vehicle identifier
	 * @param taskArrivalRate current task arrival rate for the vehicle
	 * @param expectedEdgeDelay expected processing delay at edge server
	 * @param expectedCloudDelay expected processing delay at cloud server
	 * @param maxDelay maximum acceptable delay constraint
	 * @return probability of offloading to cloud (between 0.01 and 0.99)
	 */
	public synchronized double getPi(int vehicleID, double taskArrivalRate, double expectedEdgeDelay, double expectedCloudDelay, double maxDelay) {
		// Calculate optimal probability based on Nash equilibrium formula
		pVector[vehicleID] = (expectedEdgeDelay - expectedCloudDelay) / (2 * PRICING_FACTOR * maxDelay * (1 - multiplyArray(vehicleID)));
		arrivalRateVector[vehicleID] = normalizeTaskArrivalRate(taskArrivalRate);

		// Ensure probability stays within valid bounds [0.01, 0.99]
		if(pVector[vehicleID] <= 0)
			pVector[vehicleID] = 0.01;
		else if(pVector[vehicleID] >= 1)
			pVector[vehicleID] = 0.99;

		return pVector[vehicleID];
	}

	/**
	 * Normalizes the task arrival rate to a value between 0 and 1.
	 * 
	 * @param taskArrivalRate raw task arrival rate
	 * @return normalized task arrival rate in range [0,1]
	 */
	private double normalizeTaskArrivalRate(double taskArrivalRate) {
		double result = (taskArrivalRate - MIN_TASK_ARRIVAL_RATE) / (MAX_TASK_ARRIVAL_RATE - MIN_TASK_ARRIVAL_RATE);
		return Math.max(Math.min(result,1),0);
	}

	/**
	 * Calculates the product of (1 - λᵢ * pᵢ) for all vehicles except the excluded one.
	 * This represents the probability that other vehicles don't interfere with resource allocation.
	 * 
	 * @param excludedIndex index of the vehicle to exclude from calculation
	 * @return product value used in Nash equilibrium calculation
	 */
	private double multiplyArray(int excludedIndex) {
		double product = 1.0; 
		
		// Calculate product for all vehicles except the excluded one
		for (int i = 0; i < pVector.length && i != excludedIndex; i++)
			product *= (1 - arrivalRateVector[i] * pVector[i]);

		// Handle edge cases to prevent infinite values
		if(product == Double.POSITIVE_INFINITY)
			product = 0.99;
		else if(product == Double.NEGATIVE_INFINITY)
			product = 0.01;

		return product; 
	}
}
