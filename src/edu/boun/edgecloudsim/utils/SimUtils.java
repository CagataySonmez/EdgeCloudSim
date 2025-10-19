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
 * Utility class providing common helper functions for EdgeCloudSim operations.
 * 
 * <p>SimUtils contains a collection of static utility methods that support various
 * simulation operations including random number generation, file system management,
 * and time calculations. These utilities are designed to be lightweight, thread-safe,
 * and commonly needed across different simulation components.</p>
 * 
 * <p><b>Key Functionality Areas:</b>
 * <ul>
 *   <li><b>Random Number Generation:</b> Uniform distribution sampling for various data types</li>
 *   <li><b>File System Operations:</b> Output directory management and cleanup</li>
 *   <li><b>Time Calculations:</b> Human-readable duration formatting and conversions</li>
 * </ul></p>
 * 
 * <p>All methods are static and thread-safe, making them suitable for use in
 * multi-threaded simulation environments. The random number generator is seeded
 * with the current system time to ensure different sequences across simulation runs.</p>
 * 
 * @see edu.boun.edgecloudsim.core.SimManager
 * @see edu.boun.edgecloudsim.utils.SimLogger
 */
public class SimUtils {

	/** 
	 * Shared random number generator for all simulation randomness.
	 * 
	 * <p>Uses system time as seed to ensure different random sequences
	 * across simulation runs while maintaining reproducibility within
	 * a single simulation execution.</p>
	 */
	public static final Random RNG = new Random(System.currentTimeMillis());

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
		long range = (long)end - (long)start + 1;
		long fraction = (long)(range * RNG.nextDouble());
		return (int)(fraction + start);
	}

	/**
	 * Generates a random double within the specified range (inclusive).
	 * 
	 * <p>Uses uniform distribution to select a random double from the closed
	 * interval [start, end]. Suitable for continuous value generation such as
	 * timing parameters, geographical coordinates, and probability thresholds.</p>
	 * 
	 * @param start the minimum value (inclusive)
	 * @param end the maximum value (inclusive)
	 * @return a random double between start and end (both inclusive)
	 */
	public static double getRandomDoubleNumber(double start, double end) {
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
		long range = (long)end - (long)start + 1;
		long fraction = (long)(range * RNG.nextDouble());
		return (fraction + start); 
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
	 * </ul></p>
	 * 
	 * <p><b>Warning:</b> This method will permanently delete all files in the
	 * specified directory. Use with caution in production environments.</p>
	 * 
	 * @param outputFolder absolute path to the output directory to clean
	 * @throws System.exit(1) if directory is inaccessible or file deletion fails
	 */
	public static void cleanOutputFolder(String outputFolder){
		// Validate and clean the output directory for fresh simulation results
		File dir = new File(outputFolder);
		if(dir.exists() && dir.isDirectory())
		{
			// Remove all existing files (preserve subdirectories)
			for (File f: dir.listFiles())
			{
				if (f.exists() && f.isFile())
				{
					if(!f.delete())
					{
						SimLogger.printLine("Critical Error: Cannot delete file " + f.getAbsolutePath());
						System.exit(1);
					}
				}
			}
		}
		else {
			SimLogger.printLine("Critical Error: Output folder not accessible: " + outputFolder);
			System.exit(1);
		}
	}

	/**
	 * Calculates and formats the time difference between two dates in human-readable form.
	 * 
	 * <p>Converts the duration between start and end dates into a comprehensive
	 * string representation showing days, hours, minutes, seconds, and milliseconds
	 * as appropriate. The output format is designed for logging and user-friendly
	 * simulation progress reporting.</p>
	 * 
	 * <p><b>Output Format Examples:</b>
	 * <ul>
	 *   <li>"2 Days 3 Hours 45 Minutes 12 Seconds" - for long simulations</li>
	 *   <li>"15 Minutes 30 Seconds" - for medium simulations</li>
	 *   <li>"250 Milli Seconds" - for very short operations</li>
	 * </ul></p>
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
		if(diffInHours>0)
			result += diffInHours % 24 + ((diffInHours>1 == true) ? " Hours " : " Hour ");
		if(diffInMinutes>0)
			result += diffInMinutes % 60 + ((diffInMinutes>1 == true) ? " Minutes " : " Minute ");
		if(diffInSeconds>0)
			result += diffInSeconds % 60 + ((diffInSeconds>1 == true) ? " Seconds" : " Second");
		if(diffInMilli>0 && result.isEmpty())
			result += diffInMilli + ((diffInMilli>1 == true) ? " Milli Seconds" : " Milli Second");

		return result;
	}
}
