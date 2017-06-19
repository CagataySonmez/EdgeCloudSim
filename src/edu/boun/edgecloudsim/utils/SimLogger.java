/*
 * Title:        EdgeCloudSim - Simulation Logger
 * 
 * Description: 
 * SimLogger is responsible for storing simulation events/results
 * in to the files in a specific format.
 * Format is decided in a way to use results in matlab efficiently.
 * If you need more results or another file format, you should modify
 * this class.
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;

public class SimLogger {
	public static enum TASK_STATUS {
		CREATED, UPLOADING, PROCESSING, DOWNLOADING, COMLETED, REJECTED_DUE_TO_VM_CAPACITY, REJECTED_DUE_TO_BANDWIDTH, UNFINISHED_DUE_TO_BANDWIDTH, UNFINISHED_DUE_TO_MOBILITY
	}

	private static boolean fileLogEnabled;
	private static boolean printLogEnabled;
	private String filePrefix;
	private String outputFolder;
	private Map<Integer, LogItem> taskMap;
	private LinkedList<VmLoadLogItem> vmLoadList;

	private static SimLogger singleton = new SimLogger();

	/*
	 * A private Constructor prevents any other class from instantiating.
	 */
	private SimLogger() {
		fileLogEnabled = false;
		printLogEnabled = false;
	}

	/* Static 'instance' method */
	public static SimLogger getInstance() {
		return singleton;
	}

	public static void enableFileLog() {
		fileLogEnabled = true;
	}

	public static void enablePrintLog() {
		printLogEnabled = true;
	}

	public static boolean isFileLogEnabled() {
		return fileLogEnabled;
	}

	public static void disablePrintLog() {
		printLogEnabled = false;
	}

	private void appendToFile(BufferedWriter bw, String line) throws IOException {
		bw.write(line);
		bw.newLine();
	}

	public static void printLine(String msg) {
		if (printLogEnabled)
			System.out.println(msg);
	}

	public static void print(String msg) {
		if (printLogEnabled)
			System.out.print(msg);
	}

	public void simStarted(String outFolder, String fileName) {
		filePrefix = fileName;
		outputFolder = outFolder;
		taskMap = new HashMap<Integer, LogItem>();
		vmLoadList = new LinkedList<VmLoadLogItem>();
	}

	public void addLog(double taskStartTime, int taskId, int taskType, int taskLenght, int taskInputType,
			int taskOutputSize) {
		// printLine(taskId+"->"+taskStartTime);
		taskMap.put(taskId, new LogItem(taskStartTime, taskType, taskLenght, taskInputType, taskOutputSize));
	}

	public void uploadStarted(int taskId, double taskUploadTime) {
		taskMap.get(taskId).taskUploadStarted(taskUploadTime);
	}

	public void uploaded(int taskId, int datacenterId, int hostId, int vmId, int vmType) {
		taskMap.get(taskId).taskUploaded(datacenterId, hostId, vmId, vmType);
	}

	public void downloadStarted(int taskId, double taskDownloadTime) {
		taskMap.get(taskId).taskDownloadStarted(taskDownloadTime);
	}

	public void downloaded(int taskId, double taskEndTime) {
		taskMap.get(taskId).taskDownloaded(taskEndTime);
	}

	public void rejectedDueToVMCapacity(int taskId, double taskRejectTime) {
		taskMap.get(taskId).taskRejectedDueToVMCapacity(taskRejectTime);
	}

	public void rejectedDueToBandwidth(int taskId, double taskRejectTime, int vmType) {
		taskMap.get(taskId).taskRejectedDueToBandwidth(taskRejectTime, vmType);
	}

	public void failedDueToBandwidth(int taskId, double taskRejectTime) {
		taskMap.get(taskId).taskFailedDueToBandwidth(taskRejectTime);
	}

	public void failedDueToMobility(int taskId, double time) {
		taskMap.get(taskId).taskFailedDueToMobility(time);
	}

	public void addVmUtilizationLog(double time, double load) {
		vmLoadList.add(new VmLoadLogItem(time, load));
	}

	public void simStopped() throws IOException {
		int numOfAppTypes = SimSettings.getInstance().getTaskLookUpTable().length;

		File successFile = null, failFile = null, vmLoadFile = null, locationFile = null;
		FileWriter successFW = null, failFW = null, vmLoadFW = null, locationFW = null;
		BufferedWriter successBW = null, failBW = null, vmLoadBW = null, locationBW = null;

		// Save generic results to file for each app type. last index is average
		// of all app types
		File[] genericFiles = new File[numOfAppTypes + 1];
		FileWriter[] genericFWs = new FileWriter[numOfAppTypes + 1];
		BufferedWriter[] genericBWs = new BufferedWriter[numOfAppTypes + 1];

		// extract following values for each app type. last index is average of
		// all app types
		int[] uncompletedTask = new int[numOfAppTypes + 1];
		int[] uncompletedTaskOnCloud = new int[numOfAppTypes + 1];
		int[] uncompletedTaskOnCloudlet = new int[numOfAppTypes + 1];

		int[] completedTask = new int[numOfAppTypes + 1];
		int[] completedTaskOnCloud = new int[numOfAppTypes + 1];
		int[] completedTaskOnCloudlet = new int[numOfAppTypes + 1];

		int[] failedTask = new int[numOfAppTypes + 1];
		int[] failedTaskOnCloud = new int[numOfAppTypes + 1];
		int[] failedTaskOnCloudlet = new int[numOfAppTypes + 1];

		double[] networkDelay = new double[numOfAppTypes + 1];
		double[] wanDelay = new double[numOfAppTypes + 1];
		double[] lanDelay = new double[numOfAppTypes + 1];

		double[] serviceTime = new double[numOfAppTypes + 1];
		double[] serviceTimeOnCloud = new double[numOfAppTypes + 1];
		double[] serviceTimeOnCloudlet = new double[numOfAppTypes + 1];

		double[] processingTime = new double[numOfAppTypes + 1];
		double[] processingTimeOnCloud = new double[numOfAppTypes + 1];
		double[] processingTimeOnCloudlet = new double[numOfAppTypes + 1];

		double[] cost = new double[numOfAppTypes + 1];
		int[] failedTaskDuetoBw = new int[numOfAppTypes + 1];
		int[] failedTaskDuetoLanBw = new int[numOfAppTypes + 1];
		int[] failedTaskDuetoWanBw = new int[numOfAppTypes + 1];
		int[] failedTaskDuetoMobility = new int[numOfAppTypes + 1];
		int[] rejectedTaskDoToVmCapacity = new int[numOfAppTypes + 1];

		// open all files and prepare them for write
		if (fileLogEnabled) {
			if (SimSettings.getInstance().getDeepFileLoggingEnabled()) {
				successFile = new File(outputFolder, filePrefix + "_SUCCESS.log");
				successFW = new FileWriter(successFile, true);
				successBW = new BufferedWriter(successFW);

				failFile = new File(outputFolder, filePrefix + "_FAIL.log");
				failFW = new FileWriter(failFile, true);
				failBW = new BufferedWriter(failFW);
			}

			vmLoadFile = new File(outputFolder, filePrefix + "_VM_LOAD.log");
			vmLoadFW = new FileWriter(vmLoadFile, true);
			vmLoadBW = new BufferedWriter(vmLoadFW);

			locationFile = new File(outputFolder, filePrefix + "_LOCATION.log");
			locationFW = new FileWriter(locationFile, true);
			locationBW = new BufferedWriter(locationFW);

			for (int i = 0; i < numOfAppTypes + 1; i++) {
				String fileName = "ALL_APPS_GENERIC.log";

				if (i < numOfAppTypes) {
					// if related app is not used in this simulation, just
					// discard it
					if (SimSettings.getInstance().getTaskLookUpTable()[i][0] == 0)
						continue;

					fileName = SimSettings.APP_TYPES.values()[i] + "_GENERIC.log";
				}

				genericFiles[i] = new File(outputFolder, filePrefix + "_" + fileName);
				genericFWs[i] = new FileWriter(genericFiles[i], true);
				genericBWs[i] = new BufferedWriter(genericFWs[i]);
				appendToFile(genericBWs[i], "#auto generated file!");
			}

			if (SimSettings.getInstance().getDeepFileLoggingEnabled()) {
				appendToFile(successBW, "#auto generated file!");
				appendToFile(failBW, "#auto generated file!");
			}

			appendToFile(vmLoadBW, "#auto generated file!");
			appendToFile(locationBW, "#auto generated file!");
		}

		// extract the result of each task and write it to the file if required
		for (Map.Entry<Integer, LogItem> entry : taskMap.entrySet()) {
			Integer key = entry.getKey();
			LogItem value = entry.getValue();

			if (value.isInWarmUpPeriod())
				continue;

			if (value.getStatus() == SimLogger.TASK_STATUS.COMLETED) {
				completedTask[value.getTaskType()]++;

				if (value.getVmType() == SimSettings.VM_TYPES.CLOUD_VM.ordinal())
					completedTaskOnCloud[value.getTaskType()]++;
				else
					completedTaskOnCloudlet[value.getTaskType()]++;
			} else {
				failedTask[value.getTaskType()]++;

				if (value.getVmType() == SimSettings.VM_TYPES.CLOUD_VM.ordinal())
					failedTaskOnCloud[value.getTaskType()]++;
				else
					failedTaskOnCloudlet[value.getTaskType()]++;
			}

			if (value.getStatus() == SimLogger.TASK_STATUS.COMLETED) {
				cost[value.getTaskType()] += value.getCost();
				serviceTime[value.getTaskType()] += value.getServiceTime();
				networkDelay[value.getTaskType()] += value.getNetworkDelay();
				processingTime[value.getTaskType()] += (value.getServiceTime() - value.getNetworkDelay());

				if (value.getVmType() == SimSettings.VM_TYPES.CLOUD_VM.ordinal()) {
					wanDelay[value.getTaskType()] += value.getNetworkDelay();
					serviceTimeOnCloud[value.getTaskType()] += value.getServiceTime();
					processingTimeOnCloud[value.getTaskType()] += (value.getServiceTime() - value.getNetworkDelay());
				} else {
					lanDelay[value.getTaskType()] += value.getNetworkDelay();
					serviceTimeOnCloudlet[value.getTaskType()] += value.getServiceTime();
					processingTimeOnCloudlet[value.getTaskType()] += (value.getServiceTime() - value.getNetworkDelay());
				}

				if (fileLogEnabled && SimSettings.getInstance().getDeepFileLoggingEnabled())
					appendToFile(successBW, value.toString(key));
			} else if (value.getStatus() == SimLogger.TASK_STATUS.REJECTED_DUE_TO_VM_CAPACITY) {
				rejectedTaskDoToVmCapacity[value.getTaskType()]++;
				if (value.getVmType() == SimSettings.VM_TYPES.CLOUD_VM.ordinal())
					if (fileLogEnabled && SimSettings.getInstance().getDeepFileLoggingEnabled())
						appendToFile(failBW, value.toString(key));
			} else if (value.getStatus() == SimLogger.TASK_STATUS.REJECTED_DUE_TO_BANDWIDTH
					|| value.getStatus() == SimLogger.TASK_STATUS.UNFINISHED_DUE_TO_BANDWIDTH) {
				failedTaskDuetoBw[value.getTaskType()]++;
				if (value.getVmType() == SimSettings.VM_TYPES.CLOUD_VM.ordinal())
					failedTaskDuetoWanBw[value.getTaskType()]++;
				else
					failedTaskDuetoLanBw[value.getTaskType()]++;

				if (fileLogEnabled && SimSettings.getInstance().getDeepFileLoggingEnabled())
					appendToFile(failBW, value.toString(key));
			} else if (value.getStatus() == SimLogger.TASK_STATUS.UNFINISHED_DUE_TO_MOBILITY) {
				failedTaskDuetoMobility[value.getTaskType()]++;
				if (fileLogEnabled && SimSettings.getInstance().getDeepFileLoggingEnabled())
					appendToFile(failBW, value.toString(key));
			} else {
				uncompletedTask[value.getTaskType()]++;
				if (value.getVmType() == SimSettings.VM_TYPES.CLOUD_VM.ordinal())
					uncompletedTaskOnCloud[value.getTaskType()]++;
				else
					uncompletedTaskOnCloudlet[value.getTaskType()]++;
			}
		}

		// calculate total values
		uncompletedTask[numOfAppTypes] = IntStream.of(uncompletedTask).sum();
		uncompletedTaskOnCloud[numOfAppTypes] = IntStream.of(uncompletedTaskOnCloud).sum();
		uncompletedTaskOnCloudlet[numOfAppTypes] = IntStream.of(uncompletedTaskOnCloudlet).sum();

		completedTask[numOfAppTypes] = IntStream.of(completedTask).sum();
		completedTaskOnCloud[numOfAppTypes] = IntStream.of(completedTaskOnCloud).sum();
		completedTaskOnCloudlet[numOfAppTypes] = IntStream.of(completedTaskOnCloudlet).sum();

		failedTask[numOfAppTypes] = IntStream.of(failedTask).sum();
		failedTaskOnCloud[numOfAppTypes] = IntStream.of(failedTaskOnCloud).sum();
		failedTaskOnCloudlet[numOfAppTypes] = IntStream.of(failedTaskOnCloudlet).sum();

		networkDelay[numOfAppTypes] = DoubleStream.of(networkDelay).sum();
		lanDelay[numOfAppTypes] = DoubleStream.of(lanDelay).sum();
		wanDelay[numOfAppTypes] = DoubleStream.of(wanDelay).sum();

		serviceTime[numOfAppTypes] = DoubleStream.of(serviceTime).sum();
		serviceTimeOnCloud[numOfAppTypes] = DoubleStream.of(serviceTimeOnCloud).sum();
		serviceTimeOnCloudlet[numOfAppTypes] = DoubleStream.of(serviceTimeOnCloudlet).sum();

		processingTime[numOfAppTypes] = DoubleStream.of(processingTime).sum();
		processingTimeOnCloud[numOfAppTypes] = DoubleStream.of(processingTimeOnCloud).sum();
		processingTimeOnCloudlet[numOfAppTypes] = DoubleStream.of(processingTimeOnCloudlet).sum();

		cost[numOfAppTypes] = DoubleStream.of(cost).sum();
		failedTaskDuetoBw[numOfAppTypes] = IntStream.of(failedTaskDuetoBw).sum();
		failedTaskDuetoWanBw[numOfAppTypes] = IntStream.of(failedTaskDuetoWanBw).sum();
		failedTaskDuetoLanBw[numOfAppTypes] = IntStream.of(failedTaskDuetoLanBw).sum();
		failedTaskDuetoMobility[numOfAppTypes] = IntStream.of(failedTaskDuetoMobility).sum();
		rejectedTaskDoToVmCapacity[numOfAppTypes] = IntStream.of(rejectedTaskDoToVmCapacity).sum();

		// calculate server load
		double totalVmLoad = 0;
		for (VmLoadLogItem entry : vmLoadList) {
			totalVmLoad += entry.getLoad();
			if (fileLogEnabled)
				appendToFile(vmLoadBW, entry.toString());
		}

		if (fileLogEnabled) {
			// write location info to file
			for (int t = 1; t < (SimSettings.getInstance().getSimulationTime()
					/ SimSettings.getInstance().getVmLocationLogInterval()); t++) {
				int[] locationInfo = new int[SimSettings.PLACE_TYPES.values().length];
				Double time = t * SimSettings.getInstance().getVmLocationLogInterval();

				if (time < SimSettings.getInstance().getWarmUpPeriod())
					continue;

				for (int i = 0; i < SimManager.getInstance().getNumOfMobileDevice(); i++) {

					Location loc = SimManager.getInstance().getMobilityModel().getLocation(i, time);
					SimSettings.PLACE_TYPES placeType = loc.getPlaceType();
					locationInfo[placeType.ordinal()]++;
				}

				locationBW.write(time.toString());
				for (int i = 0; i < locationInfo.length; i++)
					locationBW.write(SimSettings.DELIMITER + locationInfo[i]);

				locationBW.newLine();
			}

			for (int i = 0; i < numOfAppTypes + 1; i++) {

				if (i < numOfAppTypes) {
					// if related app is not used in this simulation, just
					// discard it
					if (SimSettings.getInstance().getTaskLookUpTable()[i][0] == 0)
						continue;
				}

				// check if the divisor is zero in order to avoid division by
				// zero problem
				double _serviceTime = (completedTask[i] == 0) ? 0.0 : (serviceTime[i] / (double) completedTask[i]);
				double _networkDelay = (completedTask[i] == 0) ? 0.0 : (networkDelay[i] / (double) completedTask[i]);
				double _processingTime = (completedTask[i] == 0) ? 0.0 : (processingTime[i] / (double) completedTask[i]);
				double _vmLoad = (vmLoadList.size() == 0) ? 0.0 : (totalVmLoad / (double) vmLoadList.size());
				double _cost = (completedTask[i] == 0) ? 0.0 : (cost[i] / (double) completedTask[i]);

				// write generic results
				String genericResult1 = Integer.toString(completedTask[i]) + SimSettings.DELIMITER
						+ Integer.toString(failedTask[i]) + SimSettings.DELIMITER 
						+ Integer.toString(uncompletedTask[i]) + SimSettings.DELIMITER 
						+ Integer.toString(failedTaskDuetoBw[i]) + SimSettings.DELIMITER
						+ Double.toString(_serviceTime) + SimSettings.DELIMITER 
						+ Double.toString(_processingTime) + SimSettings.DELIMITER 
						+ Double.toString(_networkDelay) + SimSettings.DELIMITER
						+ Double.toString(_vmLoad) + SimSettings.DELIMITER 
						+ Double.toString(_cost) + SimSettings.DELIMITER 
						+ Integer.toString(rejectedTaskDoToVmCapacity[i]) + SimSettings.DELIMITER 
						+ Integer.toString(failedTaskDuetoMobility[i]);

				// check if the divisor is zero in order to avoid division by
				// zero problem
				double _lanDelay = (completedTaskOnCloudlet[i] == 0) ? 0.0
						: (lanDelay[i] / (double) completedTaskOnCloudlet[i]);
				double _serviceTimeOnCloudlet = (completedTaskOnCloudlet[i] == 0) ? 0.0
						: (serviceTimeOnCloudlet[i] / (double) completedTaskOnCloudlet[i]);
				double _processingTimeOnCloudlet = (completedTaskOnCloudlet[i] == 0) ? 0.0
						: (processingTimeOnCloudlet[i] / (double) completedTaskOnCloudlet[i]);
				String genericResult2 = Integer.toString(completedTaskOnCloudlet[i]) + SimSettings.DELIMITER
						+ Integer.toString(failedTaskOnCloudlet[i]) + SimSettings.DELIMITER
						+ Integer.toString(uncompletedTaskOnCloudlet[i]) + SimSettings.DELIMITER
						+ Integer.toString(failedTaskDuetoLanBw[i]) + SimSettings.DELIMITER
						+ Double.toString(_serviceTimeOnCloudlet) + SimSettings.DELIMITER
						+ Double.toString(_processingTimeOnCloudlet) + SimSettings.DELIMITER
						+ Double.toString(_lanDelay);

				// check if the divisor is zero in order to avoid division by
				// zero problem
				double _wanDelay = (completedTaskOnCloud[i] == 0) ? 0.0
						: (wanDelay[i] / (double) completedTaskOnCloud[i]);
				double _serviceTimeOnCloud = (completedTaskOnCloud[i] == 0) ? 0.0
						: (serviceTimeOnCloud[i] / (double) completedTaskOnCloud[i]);
				double _processingTimeOnCloud = (completedTaskOnCloud[i] == 0) ? 0.0
						: (processingTimeOnCloud[i] / (double) completedTaskOnCloud[i]);
				String genericResult3 = Integer.toString(completedTaskOnCloud[i]) + SimSettings.DELIMITER
						+ Integer.toString(failedTaskOnCloud[i]) + SimSettings.DELIMITER
						+ Integer.toString(uncompletedTaskOnCloud[i]) + SimSettings.DELIMITER
						+ Integer.toString(failedTaskDuetoWanBw[i]) + SimSettings.DELIMITER
						+ Double.toString(_serviceTimeOnCloud) + SimSettings.DELIMITER
						+ Double.toString(_processingTimeOnCloud) + SimSettings.DELIMITER 
						+ Double.toString(_wanDelay);

				appendToFile(genericBWs[i], genericResult1);
				appendToFile(genericBWs[i], genericResult2);
				appendToFile(genericBWs[i], genericResult3);
			}

			// close open files
			if (SimSettings.getInstance().getDeepFileLoggingEnabled()) {
				successBW.close();
				failBW.close();
			}
			vmLoadBW.close();
			locationBW.close();
			for (int i = 0; i < numOfAppTypes + 1; i++) {
				if (i < numOfAppTypes) {
					// if related app is not used in this simulation, just
					// discard it
					if (SimSettings.getInstance().getTaskLookUpTable()[i][0] == 0)
						continue;
				}
				genericBWs[i].close();
			}
		}

		// printout important results
		printLine("# of tasks (Cloudlet/Cloud): "
				+ (failedTask[numOfAppTypes] + completedTask[numOfAppTypes]) + "("
				+ (failedTaskOnCloudlet[numOfAppTypes] + completedTaskOnCloudlet[numOfAppTypes]) + "/" 
				+ (failedTaskOnCloud[numOfAppTypes]+ completedTaskOnCloud[numOfAppTypes]) + ")");
		
		printLine("# of failed tasks (Cloudlet/Cloud): "
				+ failedTask[numOfAppTypes] + "("
				+ failedTaskOnCloudlet[numOfAppTypes]
				+ "/" + failedTaskOnCloud[numOfAppTypes] + ")");
		
		printLine("# of completed tasks (Cloudlet/Cloud): "
				+ completedTask[numOfAppTypes] + "("
				+ completedTaskOnCloudlet[numOfAppTypes]
				+ "/" + completedTaskOnCloud[numOfAppTypes] + ")");
		
		printLine("# of uncompleted tasks (Cloudlet/Cloud): "
				+ uncompletedTask[numOfAppTypes] + "("
				+ uncompletedTaskOnCloudlet[numOfAppTypes]
				+ "/" + uncompletedTaskOnCloud[numOfAppTypes] + ")");
		
		printLine("# of failed tasks due to vm capacity/LAN bw/WAN bw/mobility: "
				+ rejectedTaskDoToVmCapacity[numOfAppTypes]
				+ "/" + +failedTaskDuetoLanBw[numOfAppTypes] 
				+ "/" + +failedTaskDuetoWanBw[numOfAppTypes] 
				+ "/" + failedTaskDuetoMobility[numOfAppTypes]);
		
		printLine("percentage of failed tasks: "
				+ String.format("%.6f", ((double) failedTask[numOfAppTypes] * (double) 100)
						/ (double) (completedTask[numOfAppTypes] + failedTask[numOfAppTypes]))
				+ "%");

		printLine("average service time: "
				+ String.format("%.6f", serviceTime[numOfAppTypes] / (double) completedTask[numOfAppTypes])
				+ " seconds. (" + "on Cloudlet: "
				+ String.format("%.6f", serviceTimeOnCloudlet[numOfAppTypes] / (double) completedTaskOnCloudlet[numOfAppTypes])
				+ ", " + "on Cloud: "
				+ String.format("%.6f", serviceTimeOnCloud[numOfAppTypes] / (double) completedTaskOnCloud[numOfAppTypes])
				+ ")");

		printLine("average processing time: "
				+ String.format("%.6f", processingTime[numOfAppTypes] / (double) completedTask[numOfAppTypes])
				+ " seconds. (" + "on Cloudlet: "
				+ String.format("%.6f", processingTimeOnCloudlet[numOfAppTypes] / (double) completedTaskOnCloudlet[numOfAppTypes])
				+ ", " + "on Cloud: " 
				+ String.format("%.6f", processingTimeOnCloud[numOfAppTypes] / (double) completedTaskOnCloud[numOfAppTypes])
				+ ")");

		printLine("average netwrok delay: "
				+ String.format("%.6f", networkDelay[numOfAppTypes] / (double) completedTask[numOfAppTypes])
				+ " seconds. (" + "LAN delay: "
				+ String.format("%.6f", lanDelay[numOfAppTypes] / (double) completedTaskOnCloudlet[numOfAppTypes])
				+ ", " + "WAN delay: "
				+ String.format("%.6f", wanDelay[numOfAppTypes] / (double) completedTaskOnCloud[numOfAppTypes]) + ")");

		printLine("average server utilization: " 
				+ String.format("%.6f", totalVmLoad / (double) vmLoadList.size()) + "%");
		
		printLine("average cost: " + cost[numOfAppTypes] / completedTask[numOfAppTypes] + "$");

		// clear related collections (map list etc.)
		taskMap.clear();
		vmLoadList.clear();
	}
}

class VmLoadLogItem {
	private double time;
	private double vmLoad;

	VmLoadLogItem(double _time, double _vmLoad) {
		time = _time;
		vmLoad = _vmLoad;
	}

	public double getLoad() {
		return vmLoad;
	}

	public String toString() {
		return time + SimSettings.DELIMITER + vmLoad;
	}
}

class LogItem {
	private SimLogger.TASK_STATUS status;
	private int datacenterId;
	private int hostId;
	private int vmId;
	private int vmType;
	private int taskType;
	private int taskLenght;
	private int taskInputType;
	private int taskOutputSize;
	private double taskStartTime;
	private double taskEndTime;
	private double networkDelay;
	private double bwCost;
	private double cpuCost;
	private boolean isInWarmUpPeriod;

	LogItem(double _taskStartTime, int _taskType, int _taskLenght, int _taskInputType, int _taskOutputSize) {
		taskStartTime = _taskStartTime;
		taskType = _taskType;
		taskLenght = _taskLenght;
		taskInputType = _taskInputType;
		taskOutputSize = _taskOutputSize;
		status = SimLogger.TASK_STATUS.CREATED;
		taskEndTime = 0;

		if (_taskStartTime < SimSettings.getInstance().getWarmUpPeriod())
			isInWarmUpPeriod = true;
		else
			isInWarmUpPeriod = false;
	}
	
	public void taskUploadStarted(double taskUploadTime) {
		networkDelay += taskUploadTime;
		status = SimLogger.TASK_STATUS.UPLOADING;
	}

	public void taskUploaded(int _datacenterId, int _hostId, int _vmId, int _vmType) {
		status = SimLogger.TASK_STATUS.PROCESSING;
		datacenterId = _datacenterId;
		hostId = _hostId;
		vmId = _vmId;
		vmType = _vmType;
	}

	public void taskDownloadStarted(double taskDownloadTime) {
		networkDelay += taskDownloadTime;
		status = SimLogger.TASK_STATUS.DOWNLOADING;
	}

	public void taskDownloaded(double _taskEndTime) {
		taskEndTime = _taskEndTime;
		status = SimLogger.TASK_STATUS.COMLETED;
	}

	public void taskRejectedDueToVMCapacity(double _taskRejectTime) {
		taskEndTime = _taskRejectTime;
		status = SimLogger.TASK_STATUS.REJECTED_DUE_TO_VM_CAPACITY;
	}

	public void taskRejectedDueToBandwidth(double _taskRejectTime, int _vmType) {
		vmType = _vmType;
		taskEndTime = _taskRejectTime;
		status = SimLogger.TASK_STATUS.REJECTED_DUE_TO_BANDWIDTH;
	}

	public void taskFailedDueToBandwidth(double _time) {
		taskEndTime = _time;
		status = SimLogger.TASK_STATUS.UNFINISHED_DUE_TO_BANDWIDTH;
	}

	public void taskFailedDueToMobility(double _time) {
		taskEndTime = _time;
		status = SimLogger.TASK_STATUS.UNFINISHED_DUE_TO_MOBILITY;
	}

	public void setCost(double _bwCost, double _cpuCos) {
		bwCost = _bwCost;
		cpuCost = _cpuCos;
	}

	public boolean isInWarmUpPeriod() {
		return isInWarmUpPeriod;
	}

	public double getCost() {
		return bwCost + cpuCost;
	}

	public double getNetworkDelay() {
		return networkDelay;
	}

	public double getServiceTime() {
		return taskEndTime - taskStartTime;
	}

	public SimLogger.TASK_STATUS getStatus() {
		return status;
	}

	public int getVmType() {
		return vmType;
	}

	public int getTaskType() {
		return taskType;
	}

	public String toString(int taskId) {
		String result = taskId + SimSettings.DELIMITER + datacenterId + SimSettings.DELIMITER + hostId
				+ SimSettings.DELIMITER + vmId + SimSettings.DELIMITER + vmType + SimSettings.DELIMITER + taskType
				+ SimSettings.DELIMITER + taskLenght + SimSettings.DELIMITER + taskInputType + SimSettings.DELIMITER
				+ taskOutputSize + SimSettings.DELIMITER + taskStartTime + SimSettings.DELIMITER + taskEndTime
				+ SimSettings.DELIMITER;

		if (status == SimLogger.TASK_STATUS.COMLETED)
			result += networkDelay;
		else if (status == SimLogger.TASK_STATUS.REJECTED_DUE_TO_VM_CAPACITY)
			result += "1"; // failure reason 1
		else if (status == SimLogger.TASK_STATUS.REJECTED_DUE_TO_BANDWIDTH)
			result += "2"; // failure reason 2
		else if (status == SimLogger.TASK_STATUS.UNFINISHED_DUE_TO_BANDWIDTH)
			result += "3"; // failure reason 3
		else if (status == SimLogger.TASK_STATUS.UNFINISHED_DUE_TO_MOBILITY)
			result += "4"; // failure reason 4
		else
			result += "0"; // default failure reason
		return result;
	}
}