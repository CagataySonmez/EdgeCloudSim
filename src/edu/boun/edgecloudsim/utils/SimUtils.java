/* SPDX-License-Identifier: GPL-3.0-or-later */
/*
 * Title:        EdgeCloudSim - Simulation Utils
 *
 * Description:  Utility class providing helper functions
 *
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.utils;

import java.io.File;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Provides random-number, file-system, and elapsed-time utilities used by the
 * simulator.
 *
 * <p>The shared pseudorandom generator is deterministic when the
 * {@code edgecloudsim.seed} system property is set; otherwise it is initialized
 * from the current time. Callers that access {@link #RNG} directly must provide
 * any synchronization required by their execution model.</p>
 */
public class SimUtils {
	private static final String SEED_PROPERTY = "edgecloudsim.seed";
	private static long seed = readInitialSeed();

	/**
	 * Shared random number generator for all simulation randomness.
	 *
	 * <p>The initial seed is read from {@code edgecloudsim.seed}, when present,
	 * and otherwise from the current time. Prefer the synchronized helper methods
	 * when a generator is shared across threads.</p>
	 */
	public static final Random RNG = new Random(seed);

	/** Creates a utility object. Static access is preferred. */
	public SimUtils() {
	}

	private static long readInitialSeed() {
		String configuredSeed = System.getProperty(SEED_PROPERTY);
		if (configuredSeed == null || configuredSeed.trim().isEmpty()) {
			return System.currentTimeMillis();
		}
		try {
			return Long.parseLong(configuredSeed.trim());
		}
		catch (NumberFormatException exception) {
			throw new IllegalArgumentException(
					"System property " + SEED_PROPERTY + " must be a signed 64-bit integer",
					exception);
		}
	}

	/**
	 * Resets the shared pseudo-random generator to a known seed.
	 *
	 * @param newSeed seed used by subsequent random operations
	 */
	public static synchronized void setSeed(long newSeed) {
		seed = newSeed;
		RNG.setSeed(newSeed);
	}

	/**
	 * Returns the seed most recently supplied at initialization or via {@link #setSeed(long)}.
	 *
	 * @return current simulation seed
	 */
	public static synchronized long getSeed() {
		return seed;
	}

	/**
	 * Derives a deterministic seed for a component-specific random generator.
	 *
	 * @return the next seed in the shared deterministic sequence
	 */
	public static synchronized int nextSeed() {
		return RNG.nextInt();
	}

	/**
	 * Aborts the current simulation without terminating the hosting JVM.
	 *
	 * <p>This compatibility helper replaces historical calls to {@code System.exit}
	 * in reusable simulation components. Command-line entry points remain responsible
	 * for translating uncaught failures to a non-zero process status.</p>
	 *
	 * @throws IllegalStateException always
	 */
	public static void terminateSimulation() {
		throw new IllegalStateException("Simulation terminated because of the preceding error");
	}

	/**
	 * Generates a random integer within the specified range (inclusive).
	 *
	 * <p>Uses uniform distribution to select a random integer from the closed
	 * interval [start, end]. Both boundary values are included in the possible
	 * outcomes, making this suitable for discrete selection scenarios like
	 * device ID selection, task type assignment, and configuration indexing.</p>
	 *
	 * @param start the minimum value (inclusive)
	 * @param end the maximum value (inclusive)
	 * @return a random integer between start and end (both inclusive)
	 */
	public static int getRandomNumber(int start, int end) {
		if (start > end) {
			throw new IllegalArgumentException("Start value cannot be greater than end value");
		}
		long range = (long) end - start + 1;
		return (int) (start + nextLongBounded(range));
	}

	/**
	 * Generates a random double within the specified half-open range.
	 *
	 * <p>Uses uniform distribution to select a random double from the closed
	 * interval [start, end). Suitable for continuous value generation such as
	 * timing parameters, geographical coordinates, and probability thresholds.</p>
	 *
	 * @param start the minimum value (inclusive)
	 * @param end the maximum value (exclusive)
	 * @return a random double greater than or equal to start and less than end
	 */
	public static double getRandomDoubleNumber(double start, double end) {
		if (!Double.isFinite(start) || !Double.isFinite(end) || start > end) {
			throw new IllegalArgumentException("Random bounds must be finite and start cannot exceed end");
		}
		double range = end - start;
		double fraction = (range * RNG.nextDouble());
		return (fraction + start);
	}

	/**
	 * Generates a random long integer within the specified range (inclusive).
	 *
	 * <p>Uses uniform distribution to select a random long from the closed
	 * interval [start, end]. Useful for large-scale simulations requiring
	 * high-precision identifiers or when dealing with large numerical ranges.</p>
	 *
	 * @param start the minimum value (inclusive)
	 * @param end the maximum value (inclusive)
	 * @return a random long between start and end (both inclusive)
	 */
	public static long getRandomLongNumber(long start, long end) {
		if (start > end) {
			throw new IllegalArgumentException("Start value cannot be greater than end value");
		}
		long range = end - start + 1;
		if (range > 0) {
			return start + nextLongBounded(range);
		}
		// The interval is wider than Long.MAX_VALUE, so rejection sampling is used.
		long candidate;
		do {
			candidate = RNG.nextLong();
		} while (candidate < start || candidate > end);
		return candidate;
	}

	private static long nextLongBounded(long bound) {
		if (bound <= 0) {
			throw new IllegalArgumentException("Bound must be positive");
		}
		long mask = bound - 1;
		long random = RNG.nextLong();
		if ((bound & mask) == 0) {
			return random & mask;
		}
		long candidate = random >>> 1;
		long result = candidate % bound;
		while (candidate + mask - result < 0) {
			candidate = RNG.nextLong() >>> 1;
			result = candidate % bound;
		}
		return result;
	}

	/**
	 * Cleans the specified output folder by removing all existing files.
	 *
	 * <p>This method ensures a clean state for simulation output by removing
	 * any existing result files from previous simulation runs. It performs
	 * validation to ensure the target directory exists and is accessible
	 * before attempting cleanup operations.</p>
	 *
	 * <p><b>Safety Features:</b>
	 * <ul>
	 *   <li>Validates directory existence and accessibility</li>
	 *   <li>Only removes files (preserves subdirectories)</li>
	 *   <li>Provides error logging for failed operations</li>
	 *   <li>Terminates execution on critical failures</li>
	 * </ul>
	 *
	 * <p><b>Warning:</b> This method will permanently delete all files in the
	 * specified directory. Use with caution in production environments.</p>
	 *
	 * @param outputFolder path to the output directory to clean
	 * @throws IllegalStateException if the directory cannot be listed or a file cannot be deleted
	 */
	public static void cleanOutputFolder(String outputFolder){
		// Validate and clean the output directory for fresh simulation results
		File dir = new File(outputFolder);
		if(dir.exists() && dir.isDirectory())
		{
			// Remove all existing files (preserve subdirectories)
			File[] files = dir.listFiles();
			if (files == null) {
				throw new IllegalStateException("Cannot list output folder: " + outputFolder);
			}
			for (File f: files)
			{
				if (f.exists() && f.isFile())
				{
					if(!f.delete())
					{
						SimLogger.printLine("Critical Error: Cannot delete file " + f.getAbsolutePath());
						throw new IllegalStateException("Cannot delete output file: " + f.getAbsolutePath());
					}
				}
			}
		}
		else {
			SimLogger.printLine("Critical Error: Output folder not accessible: " + outputFolder);
			throw new IllegalStateException("Output folder is not accessible: " + outputFolder);
		}
	}

	/**
	 * Calculates and formats the time difference between two dates in human-readable form.
	 *
	 * <p>Converts the duration between start and end dates into a
	 * string representation showing days, hours, minutes, seconds, and milliseconds
	 * as appropriate. The output format is designed for logging and user-friendly
	 * simulation progress reporting.</p>
	 *
	 * <p><b>Output Format Examples:</b>
	 * <ul>
	 *   <li>"2 Days 3 Hours 45 Minutes 12 Seconds" - for long simulations</li>
	 *   <li>"15 Minutes 30 Seconds" - for medium simulations</li>
	 *   <li>"250 Milli Seconds" - for very short operations</li>
	 * </ul>
	 *
	 * <p>The method uses proper pluralization and only includes non-zero time units
	 * in the output. For durations less than one second, milliseconds are shown
	 * to provide precise timing information for performance analysis.</p>
	 *
	 * @param startDate the beginning timestamp
	 * @param endDate the ending timestamp
	 * @return formatted string describing the elapsed time duration
	 */
	public static String getTimeDifference(Date startDate, Date endDate){
		String result = "";
		long duration = endDate.getTime() - startDate.getTime();

		// Convert duration to different time units
		long diffInMilli = TimeUnit.MILLISECONDS.toMillis(duration);
		long diffInSeconds = TimeUnit.MILLISECONDS.toSeconds(duration);
		long diffInMinutes = TimeUnit.MILLISECONDS.toMinutes(duration);
		long diffInHours = TimeUnit.MILLISECONDS.toHours(duration);
		long diffInDays = TimeUnit.MILLISECONDS.toDays(duration);

		if(diffInDays>0)
			result += diffInDays + ((diffInDays>1 == true) ? " Days " : " Day ");
		long hours = diffInHours % 24;
		long minutes = diffInMinutes % 60;
		long seconds = diffInSeconds % 60;
		if(hours>0)
			result += hours + ((hours>1) ? " Hours " : " Hour ");
		if(minutes>0)
			result += minutes + ((minutes>1) ? " Minutes " : " Minute ");
		if(seconds>0)
			result += seconds + ((seconds>1) ? " Seconds" : " Second");
		if(diffInMilli>0 && result.isEmpty())
			result += diffInMilli + ((diffInMilli>1 == true) ? " Milli Seconds" : " Milli Second");

		return result;
	}
}
