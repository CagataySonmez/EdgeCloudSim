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

public class PoissonDistr {
	Poisson poisson;
	RandomEngine engine;

    /**
     * Creates a new exponential number generator.
     * 
     * @param mean the mean for the distribution.
     */
    public PoissonDistr(double mean) {
		engine = new MersenneTwister(new Date());
		poisson = new Poisson(mean, engine);
		
		//always sleep for some milliseconds in order not to have same seed for iterative PoissonDistr contruction
		try {
			TimeUnit.MILLISECONDS.sleep(10);
		} catch (InterruptedException e) {
	    	SimLogger.printLine("impossible is occured! Poisson random number cannot be created!");
			e.printStackTrace();
	    	System.exit(0);
		}
    }

    /**
     * Generate a new random number.
     * 
     * @return the next random number in the sequence
     */
        public double sample() {
        	return poisson.nextDouble();
    }
}
