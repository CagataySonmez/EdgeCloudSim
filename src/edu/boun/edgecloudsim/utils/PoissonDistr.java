/*
 * Title:        EdgeCloudSim - Poisson Distribution
 * 
 * Description:  Wrapper class for colt Poisson Distribution
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.utils;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import cern.jet.random.Poisson;
import cern.jet.random.engine.MersenneTwister;
import cern.jet.random.engine.RandomEngine;

/**
 * Wrapper class for Poisson distribution random number generation using the Colt library.
 * 
 * <p>This class provides a simplified interface to the Colt library's Poisson distribution
 * implementation, specifically designed for EdgeCloudSim's statistical modeling needs.
 * The Poisson distribution is commonly used in simulation for modeling discrete events
 * such as task arrivals, packet arrivals, and failure occurrences.</p>
 * 
 * <p><b>Poisson Distribution Characteristics:</b>
 * <ul>
 *   <li><b>Discrete Distribution:</b> Generates non-negative integer values</li>
 *   <li><b>Single Parameter:</b> Controlled by mean (λ) parameter</li>
 *   <li><b>Memoryless Property:</b> Events occur independently of past events</li>
 *   <li><b>Rate Process Modeling:</b> Suitable for modeling arrival rates and frequencies</li>
 * </ul></p>
 * 
 * <p><b>EdgeCloudSim Use Cases:</b>
 * <ul>
 *   <li>Task arrival modeling in load generation</li>
 *   <li>Network packet arrival simulation</li>
 *   <li>Failure event frequency modeling</li>
 *   <li>Resource request pattern generation</li>
 * </ul></p>
 * 
 * <p>The implementation uses the Mersenne Twister algorithm for high-quality
 * pseudorandom number generation and includes automatic seed diversification
 * to prevent correlation between multiple instances created in rapid succession.</p>
 * 
 * @see edu.boun.edgecloudsim.task_generator.LoadGeneratorModel
 * @see cern.jet.random.Poisson
 */
public class PoissonDistr {
	/** Colt library Poisson distribution generator */
	Poisson poisson;
	
	/** Mersenne Twister random engine for high-quality randomness */
	RandomEngine engine;

	/**
	 * Creates a new Poisson distribution random number generator.
	 * 
	 * <p>Initializes a Poisson distribution generator with the specified mean parameter
	 * using the Mersenne Twister algorithm for high-quality pseudorandom number generation.
	 * The constructor includes automatic seed diversification to ensure different random
	 * sequences when multiple instances are created in rapid succession.</p>
	 * 
	 * <p><b>Mathematical Properties:</b>
	 * <ul>
	 *   <li><b>Mean:</b> λ (lambda parameter)</li>
	 *   <li><b>Variance:</b> λ (equal to mean)</li>
	 *   <li><b>Support:</b> Non-negative integers {0, 1, 2, 3, ...}</li>
	 *   <li><b>PMF:</b> P(X = k) = (λ^k * e^(-λ)) / k!</li>
	 * </ul></p>
	 * 
	 * <p><b>Implementation Notes:</b>
	 * The constructor includes a 10ms sleep to prevent seed correlation when multiple
	 * generators are created rapidly, ensuring statistical independence between instances.</p>
	 * 
	 * @param mean the mean (λ) parameter of the Poisson distribution (must be positive)
	 * @throws InterruptedException if thread sleep is interrupted during seed diversification
	 * @throws System.exit(1) if random number generator cannot be properly initialized
	 */
	public PoissonDistr(double mean) {
		// Initialize Mersenne Twister with current timestamp seed
		engine = new MersenneTwister(new Date());
		poisson = new Poisson(mean, engine);
		
		// Sleep briefly to ensure different seeds for rapid successive instantiation
		try {
			TimeUnit.MILLISECONDS.sleep(10);
		} catch (InterruptedException e) {
			SimLogger.printLine("Critical Error: Poisson random number generator initialization failed!");
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Generates a new random sample from the Poisson distribution.
	 * 
	 * <p>Returns a randomly generated value following the Poisson distribution
	 * with the mean parameter specified during construction. Each call produces
	 * an independent sample suitable for modeling discrete event arrivals or
	 * count-based phenomena in simulation scenarios.</p>
	 * 
	 * <p><b>Output Characteristics:</b>
	 * <ul>
	 *   <li><b>Type:</b> Double representation of discrete integer values</li>
	 *   <li><b>Range:</b> Non-negative numbers (≥ 0)</li>
	 *   <li><b>Distribution:</b> Follows P(X = k) = (λ^k * e^(-λ)) / k!</li>
	 *   <li><b>Independence:</b> Each sample is statistically independent</li>
	 * </ul></p>
	 * 
	 * <p><b>Usage Examples in EdgeCloudSim:</b>
	 * <ul>
	 *   <li>Number of tasks arriving in a time interval</li>
	 *   <li>Number of network packets in a transmission burst</li>
	 *   <li>Count of simultaneous service requests</li>
	 * </ul></p>
	 * 
	 * @return the next random sample from the Poisson distribution as a double value
	 */
	public double sample() {
		return poisson.nextDouble();
	}
}
