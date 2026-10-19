/* SPDX-License-Identifier: GPL-3.0-or-later */
package edu.boun.edgecloudsim.applications.sample_app5;

/** Computes the cloud-offloading probability used by the game-theory policy. */
public class GameTheoryHelper {

	// Pricing factor used in Nash equilibrium calculations
	private static final double PRICING_FACTOR = 0.6;
	private final double maxTaskArrivalRate;
	private final double minTaskArrivalRate;

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
		if (!Double.isFinite(minTaskArrivalRate) || !Double.isFinite(maxTaskArrivalRate)
				|| maxTaskArrivalRate <= minTaskArrivalRate) {
			throw new IllegalArgumentException("Maximum task arrival rate must be greater than the minimum rate");
		}
		if (numOfVehicles <= 0) {
			throw new IllegalArgumentException("Number of vehicles must be positive");
		}
		this.maxTaskArrivalRate = maxTaskArrivalRate;
		this.minTaskArrivalRate = minTaskArrivalRate;

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
	 * Calculates the bounded cloud-offloading probability defined by the sample policy.
	 *
	 * @param vehicleID vehicle identifier
	 * @param taskArrivalRate current task arrival rate for the vehicle
	 * @param expectedEdgeDelay expected processing delay at edge server
	 * @param expectedCloudDelay expected processing delay at cloud server
	 * @param maxDelay maximum acceptable delay constraint
	 * @return probability of offloading to cloud (between 0.01 and 0.99)
	 */
	public synchronized double getPi(int vehicleID, double taskArrivalRate, double expectedEdgeDelay, double expectedCloudDelay, double maxDelay) {
		if (vehicleID < 0 || vehicleID >= pVector.length) {
			throw new IllegalArgumentException("Vehicle ID is outside the configured range: " + vehicleID);
		}
		if (!Double.isFinite(maxDelay) || maxDelay <= 0) {
			throw new IllegalArgumentException("Maximum delay must be a positive finite value");
		}
		// Evaluate the probability formula used by the game-theory policy.
		double denominator = 2 * PRICING_FACTOR * maxDelay * (1 - multiplyArray(vehicleID));
		pVector[vehicleID] = denominator == 0
				? 0.99
				: (expectedEdgeDelay - expectedCloudDelay) / denominator;
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
		double result = (taskArrivalRate - minTaskArrivalRate) / (maxTaskArrivalRate - minTaskArrivalRate);
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
		for (int i = 0; i < pVector.length; i++) {
			if (i == excludedIndex) {
				continue;
			}
			product *= (1 - arrivalRateVector[i] * pVector[i]);
		}

		// Handle edge cases to prevent infinite values
		if (!Double.isFinite(product)) {
			throw new IllegalStateException("Non-finite game-theory product");
		}

		return product;
	}
}
