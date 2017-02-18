/*
 * Title:        EdgeCloudSim - Poisson Distribution
 * 
 * Description:  Poisson Distribution implementation
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.utils;

import java.util.Random;

public class PoissonDistr {
    /** The num gen. */
    private final Random numGen;

    /** The mean. */
    private final double mean;

    /**
     * Creates a new exponential number generator.
     * 
     * @param seed the seed to be used.
     * @param mean the mean for the distribution.
     */
    public PoissonDistr(long seed, double mean) {
        if (mean <= 0.0) {
            throw new IllegalArgumentException("Mean must be greater than 0.0");
        }
        numGen = new Random(seed);
        this.mean = mean;
    }

    /**
     * Creates a new exponential number generator.
     * 
     * @param mean the mean for the distribution.
     */
    public PoissonDistr(double mean) {
        if (mean <= 0.0) {
            throw new IllegalArgumentException("Mean must be greated than 0.0");
        }
        numGen = new Random(System.currentTimeMillis());
        this.mean = mean;
    }

    /**
     * Generate a new random number.
     * 
     * @return the next random number in the sequence
     */
        public double sample() {
            double L = Math.exp(-mean);
            int k = 0;
            double p = 1.0;
            do {
                p = p * numGen.nextDouble();
                k++;
            } while (p > L);
            return k - 1;
    }
}
