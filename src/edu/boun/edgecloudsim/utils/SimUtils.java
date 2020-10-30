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

public class SimUtils {

	public static final Random RNG = new Random(System.currentTimeMillis());

	public static int getRandomNumber(int start, int end) {
		//return pd.sample();
		long range = (long)end - (long)start + 1;
		long fraction = (long)(range * RNG.nextDouble());
		return (int)(fraction + start);
	}

	public static double getRandomDoubleNumber(double start, double end) {
		//return pd.sample();
		double range = end - start;
		double fraction = (range * RNG.nextDouble());
		return (fraction + start); 
	}

	public static long getRandomLongNumber(long start, long end) {
		//return pd.sample();
		long range = (long)end - (long)start + 1;
		long fraction = (long)(range * RNG.nextDouble());
		return (fraction + start); 
	}

	public static void cleanOutputFolder(String outputFolder){
		//clean the folder where the result files will be saved
		File dir = new File(outputFolder);
		if(dir.exists() && dir.isDirectory())
		{
			for (File f: dir.listFiles())
			{
				if (f.exists() && f.isFile())
				{
					if(!f.delete())
					{
						SimLogger.printLine("file cannot be cleared: " + f.getAbsolutePath());
						System.exit(1);
					}
				}
			}
		}
		else {
			SimLogger.printLine("Output folder is not available: " + outputFolder);
			System.exit(1);
		}
	}

	public static String getTimeDifference(Date startDate, Date endDate){
		String result = "";
		long duration  = endDate.getTime() - startDate.getTime();

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
