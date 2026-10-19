/* SPDX-License-Identifier: GPL-3.0-or-later */
/*
 * Title:        EdgeCloudSim - Poisson Distribution
 *
 * Description:  Wrapper class for colt Poisson Distribution
 *
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.utils;

import cern.jet.random.Poisson;
import cern.jet.random.engine.MersenneTwister;
import cern.jet.random.engine.RandomEngine;

/**
 * Samples a Poisson distribution by using Colt's {@link Poisson}
 * implementation and a {@link MersenneTwister} engine.
 *
 * <p>Each instance receives a seed from {@link SimUtils#nextSeed()}. Therefore,
 * the sequence of samples is reproducible when the simulation seed and object
 * construction order are unchanged.</p>
 */
public class PoissonDistr {
	/** Colt library Poisson distribution generator */
	Poisson poisson;

	/** Random engine used by the Colt distribution. */
	RandomEngine engine;

	/**
	 * Creates a new Poisson distribution random number generator.
	 *
	 * @param mean positive, finite mean of the distribution
	 * @throws IllegalArgumentException if mean is not positive and finite
	 */
	public PoissonDistr(double mean) {
		if (!Double.isFinite(mean) || mean <= 0) {
			throw new IllegalArgumentException("Poisson mean must be a positive finite value");
		}
		engine = new MersenneTwister(SimUtils.nextSeed());
		poisson = new Poisson(mean, engine);
	}

	/**
	 * Returns the next non-negative Poisson-distributed sample.
	 *
	 * @return sample represented as a {@code double} by the Colt API
	 */
	public double sample() {
		return poisson.nextDouble();
	}
}
