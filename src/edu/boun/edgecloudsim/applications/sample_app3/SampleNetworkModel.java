/*
 * Title:        EdgeCloudSim - Network Model
 * 
 * Description: 
 * SampleNetworkModel uses
 * the result of an empirical study for the WLAN delay
 * The experimental network model is developed
 * by taking measurements from the real life deployments.
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app3;

import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;

public class SampleNetworkModel extends NetworkModel {
	private int[] wlanClients;
	
	public static final double[] experimentalWlanDelay = {
		/*1 Client*/ 88040.279 /*(Kbps)*/,
		/*2 Clients*/ 45150.982 /*(Kbps)*/,
		/*3 Clients*/ 30303.641 /*(Kbps)*/,
		/*4 Clients*/ 27617.211 /*(Kbps)*/,
		/*5 Clients*/ 24868.616 /*(Kbps)*/,
		/*6 Clients*/ 22242.296 /*(Kbps)*/,
		/*7 Clients*/ 20524.064 /*(Kbps)*/,
		/*8 Clients*/ 18744.889 /*(Kbps)*/,
		/*9 Clients*/ 17058.827 /*(Kbps)*/,
		/*10 Clients*/ 15690.455 /*(Kbps)*/,
		/*11 Clients*/ 14127.744 /*(Kbps)*/,
		/*12 Clients*/ 13522.408 /*(Kbps)*/,
		/*13 Clients*/ 13177.631 /*(Kbps)*/,
		/*14 Clients*/ 12811.330 /*(Kbps)*/,
		/*15 Clients*/ 12584.387 /*(Kbps)*/,
		/*15 Clients*/ 12135.161 /*(Kbps)*/,
		/*16 Clients*/ 11705.638 /*(Kbps)*/,
		/*17 Clients*/ 11276.116 /*(Kbps)*/,
		/*18 Clients*/ 10846.594 /*(Kbps)*/,
		/*19 Clients*/ 10417.071 /*(Kbps)*/,
		/*20 Clients*/ 9987.549 /*(Kbps)*/,
		/*21 Clients*/ 9367.587 /*(Kbps)*/,
		/*22 Clients*/ 8747.625 /*(Kbps)*/,
		/*23 Clients*/ 8127.663 /*(Kbps)*/,
		/*24 Clients*/ 7907.701 /*(Kbps)*/,
		/*25 Clients*/ 7887.739 /*(Kbps)*/,
		/*26 Clients*/ 7690.831 /*(Kbps)*/,
		/*27 Clients*/ 7393.922 /*(Kbps)*/,
		/*28 Clients*/ 7297.014 /*(Kbps)*/,
		/*29 Clients*/ 7100.106 /*(Kbps)*/,
		/*30 Clients*/ 6903.197 /*(Kbps)*/,
		/*31 Clients*/ 6701.986 /*(Kbps)*/,
		/*32 Clients*/ 6500.776 /*(Kbps)*/,
		/*33 Clients*/ 6399.565 /*(Kbps)*/,
		/*34 Clients*/ 6098.354 /*(Kbps)*/,
		/*35 Clients*/ 5897.143 /*(Kbps)*/,
		/*36 Clients*/ 5552.127 /*(Kbps)*/,
		/*37 Clients*/ 5207.111 /*(Kbps)*/,
		/*38 Clients*/ 4862.096 /*(Kbps)*/,
		/*39 Clients*/ 4517.080 /*(Kbps)*/,
		/*40 Clients*/ 4172.064 /*(Kbps)*/,
		/*41 Clients*/ 4092.922 /*(Kbps)*/,
		/*42 Clients*/ 4013.781 /*(Kbps)*/,
		/*43 Clients*/ 3934.639 /*(Kbps)*/,
		/*44 Clients*/ 3855.498 /*(Kbps)*/,
		/*45 Clients*/ 3776.356 /*(Kbps)*/,
		/*46 Clients*/ 3697.215 /*(Kbps)*/,
		/*47 Clients*/ 3618.073 /*(Kbps)*/,
		/*48 Clients*/ 3538.932 /*(Kbps)*/,
		/*49 Clients*/ 3459.790 /*(Kbps)*/,
		/*50 Clients*/ 3380.649 /*(Kbps)*/,
		/*51 Clients*/ 3274.611 /*(Kbps)*/,
		/*52 Clients*/ 3168.573 /*(Kbps)*/,
		/*53 Clients*/ 3062.536 /*(Kbps)*/,
		/*54 Clients*/ 2956.498 /*(Kbps)*/,
		/*55 Clients*/ 2850.461 /*(Kbps)*/,
		/*56 Clients*/ 2744.423 /*(Kbps)*/,
		/*57 Clients*/ 2638.386 /*(Kbps)*/,
		/*58 Clients*/ 2532.348 /*(Kbps)*/,
		/*59 Clients*/ 2426.310 /*(Kbps)*/,
		/*60 Clients*/ 2320.273 /*(Kbps)*/,
		/*61 Clients*/ 2283.828 /*(Kbps)*/,
		/*62 Clients*/ 2247.383 /*(Kbps)*/,
		/*63 Clients*/ 2210.939 /*(Kbps)*/,
		/*64 Clients*/ 2174.494 /*(Kbps)*/,
		/*65 Clients*/ 2138.049 /*(Kbps)*/,
		/*66 Clients*/ 2101.604 /*(Kbps)*/,
		/*67 Clients*/ 2065.160 /*(Kbps)*/,
		/*68 Clients*/ 2028.715 /*(Kbps)*/,
		/*69 Clients*/ 1992.270 /*(Kbps)*/,
		/*70 Clients*/ 1955.825 /*(Kbps)*/,
		/*71 Clients*/ 1946.788 /*(Kbps)*/,
		/*72 Clients*/ 1937.751 /*(Kbps)*/,
		/*73 Clients*/ 1928.714 /*(Kbps)*/,
		/*74 Clients*/ 1919.677 /*(Kbps)*/,
		/*75 Clients*/ 1910.640 /*(Kbps)*/,
		/*76 Clients*/ 1901.603 /*(Kbps)*/,
		/*77 Clients*/ 1892.566 /*(Kbps)*/,
		/*78 Clients*/ 1883.529 /*(Kbps)*/,
		/*79 Clients*/ 1874.492 /*(Kbps)*/,
		/*80 Clients*/ 1865.455 /*(Kbps)*/,
		/*81 Clients*/ 1833.185 /*(Kbps)*/,
		/*82 Clients*/ 1800.915 /*(Kbps)*/,
		/*83 Clients*/ 1768.645 /*(Kbps)*/,
		/*84 Clients*/ 1736.375 /*(Kbps)*/,
		/*85 Clients*/ 1704.106 /*(Kbps)*/,
		/*86 Clients*/ 1671.836 /*(Kbps)*/,
		/*87 Clients*/ 1639.566 /*(Kbps)*/,
		/*88 Clients*/ 1607.296 /*(Kbps)*/,
		/*89 Clients*/ 1575.026 /*(Kbps)*/,
		/*90 Clients*/ 1542.756 /*(Kbps)*/,
		/*91 Clients*/ 1538.544 /*(Kbps)*/,
		/*92 Clients*/ 1534.331 /*(Kbps)*/,
		/*93 Clients*/ 1530.119 /*(Kbps)*/,
		/*94 Clients*/ 1525.906 /*(Kbps)*/,
		/*95 Clients*/ 1521.694 /*(Kbps)*/,
		/*96 Clients*/ 1517.481 /*(Kbps)*/,
		/*97 Clients*/ 1513.269 /*(Kbps)*/,
		/*98 Clients*/ 1509.056 /*(Kbps)*/,
		/*99 Clients*/ 1504.844 /*(Kbps)*/,
		/*100 Clients*/ 1500.631 /*(Kbps)*/
	};
	
	public SampleNetworkModel(int _numberOfMobileDevices, String _simScenario) {
		super(_numberOfMobileDevices, _simScenario);
	}

	@Override
	public void initialize() {
		wlanClients = new int[SimSettings.getInstance().getNumOfEdgeDatacenters()];  //we have one access point for each datacenter
	}

    /**
    * source device is always mobile device in our simulation scenarios!
    */
	@Override
	public double getUploadDelay(int sourceDeviceId, int destDeviceId, Task task) {
		double delay = 0;

		//mobile device to edge device (wifi access point)
		if (destDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID) {
			delay = getWlanUploadDelay(task.getSubmittedLocation(), task.getCloudletFileSize());
		}
		else {
			SimLogger.printLine("Error - unknown device id in getUploadDelay(). Terminating simulation...");
			System.exit(0);
		}
		return delay;
	}

    /**
    * destination device is always mobile device in our simulation scenarios!
    */
	@Override
	public double getDownloadDelay(int sourceDeviceId, int destDeviceId, Task task) {
		double delay = 0;
		
		Location accessPointLocation = SimManager.getInstance().getMobilityModel().getLocation(destDeviceId,CloudSim.clock());
		
		//edge device (wifi access point) to mobile device
		if (sourceDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID) {
			delay = getWlanDownloadDelay(accessPointLocation, task.getCloudletOutputSize());
		}
		else {
			SimLogger.printLine("Error - unknown device id in getDownloadDelay(). Terminating simulation...");
			System.exit(0);
		}
		
		return delay;
	}

	@Override
	public void uploadStarted(Location accessPointLocation, int destDeviceId) {
		if (destDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID) {
			wlanClients[accessPointLocation.getServingWlanId()]++;
		}
		else {
			SimLogger.printLine("Error - unknown device id in uploadStarted(). Terminating simulation...");
			System.exit(0);
		}
	}

	@Override
	public void uploadFinished(Location accessPointLocation, int destDeviceId) {
		 if (destDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID) {
			wlanClients[accessPointLocation.getServingWlanId()]--;
		 }
		else {
			SimLogger.printLine("Error - unknown device id in uploadFinished(). Terminating simulation...");
			System.exit(0);
		}
	}

	@Override
	public void downloadStarted(Location accessPointLocation, int sourceDeviceId) {
		if(sourceDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID) {
			wlanClients[accessPointLocation.getServingWlanId()]++;
		}
		else {
			SimLogger.printLine("Error - unknown device id in downloadStarted(). Terminating simulation...");
			System.exit(0);
		}
	}

	@Override
	public void downloadFinished(Location accessPointLocation, int sourceDeviceId) {
		if(sourceDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID) {
			wlanClients[accessPointLocation.getServingWlanId()]--;
		}
		else {
			SimLogger.printLine("Error - unknown device id in downloadFinished(). Terminating simulation...");
			System.exit(0);
		}
	}

	private double getWlanDownloadDelay(Location accessPointLocation, double dataSize) {
		int numOfWlanUser = wlanClients[accessPointLocation.getServingWlanId()];
		double taskSizeInKb = dataSize * (double)8; //KB to Kb
		double result=0;
		
		if(numOfWlanUser < 0)
			System.out.println("--> ");
		
		if(numOfWlanUser < experimentalWlanDelay.length)
			result = taskSizeInKb /*Kb*/ / (experimentalWlanDelay[numOfWlanUser] * (double) 3 ) /*Kbps*/; //802.11ac is around 3 times faster than 802.11n

		//System.out.println("--> " + numOfWlanUser + " user, " + taskSizeInKb + " KB, " +result + " sec");
		return result;
	}
	
	//wlan upload and download delay is symmetric in this model
	private double getWlanUploadDelay(Location accessPointLocation, double dataSize) {
		return getWlanDownloadDelay(accessPointLocation, dataSize);
	}
}
