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

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;

public class SimLogger {
	public static enum TASK_STATUS {CREATED,
									UPLOADING,
									PROCESSING,
									DOWNLOADING,
									COMLETED,
									REJECTED_DUE_TO_VM_CAPACITY,
									REJECTED_DUE_TO_BANDWIDTH,
									UNFINISHED_DUE_TO_BANDWIDTH,
									UNFINISHED_DUE_TO_MOBILITY
	}
	
	private static boolean fileLogEnabled;
	private static boolean printLogEnabled;
	private String filePrefix;
	private String outputFolder;
	private Map<Integer, LogItem> taskMap;
	private LinkedList<VmLoadLogItem> vmLoadList;

	private static SimLogger singleton = new SimLogger( );

	/* A private Constructor prevents any other 
	 * class from instantiating.
	 */
	private SimLogger(){
		fileLogEnabled = false;
		printLogEnabled = false;
	}

	/* Static 'instance' method */
	public static SimLogger getInstance( ) {
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
	
	private void appendToFile(BufferedWriter bw, String line) throws IOException{
		bw.write(line);
		bw.newLine();
	}

	public static void printLine(String msg){
		if(printLogEnabled)
			System.out.println(msg);
	}

	public static void print(String msg){
		if(printLogEnabled)
			System.out.print(msg);
	}
	
	public void simStarted(String outFolder,String fileName) {
		filePrefix = fileName;
		outputFolder = outFolder;
		taskMap = new HashMap<Integer, LogItem>();
		vmLoadList = new LinkedList<VmLoadLogItem>();
	}

	public void addLog(double taskStartTime, int taskId, int taskType, int taskLenght, int taskInputType, int taskOutputSize) {
		//printLine(taskId+"->"+taskStartTime);
		taskMap.put(taskId, new LogItem(taskStartTime, taskType, taskLenght, taskInputType, taskOutputSize));
	}

	public void uploadStarted(int taskId, double taskUploadTime) {
		taskMap.get(taskId).taskUploadStarted(taskUploadTime);
	}

	public void uploaded(int taskId, int datacenterId, int hostId, int vmId, int vmType) {
		taskMap.get(taskId).taskUploaded(datacenterId, hostId, vmId,vmType);
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

	public void rejectedDueToBandwidth(int taskId, double taskRejectTime) {
		taskMap.get(taskId).taskRejectedDueToBandwidth(taskRejectTime);
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
		File file1=null,file2=null,file3=null,file4=null,file5=null;
		FileWriter fileWriter1=null,fileWriter2=null,fileWriter3=null,fileWriter4=null,fileWriter5=null;
		BufferedWriter bufferedWriter1=null,bufferedWriter2=null,bufferedWriter3=null,bufferedWriter4=null,bufferedWriter5=null;
		
		int uncompletedTaskOnCloudlet = 0;
		int completedTaskOnCloudlet = 0;
		int uncompletedTaskOnCloud = 0;
		int completedTaskOnCloud = 0;
		int rejectedTaskDoToVmCapacity = 0;
		int rejectedTaskDoToBandwidth = 0;
		int failedTaskDuetoBandwidth = 0;
		int failedTaskDuetoMobility = 0;
		int totalCompletedTask = 0;
		int totalFailedTask = 0;
		double totalCost = 0;
		double totalLanDelay = 0;
		double totalWanDelay = 0;
		double totalServiceTime = 0;
		double totalNetworkDelay = 0;
		double totalProcessingTime = 0;
		double totalVmLoad = 0;

		//open all files and prepare them for write
		if(fileLogEnabled){
			if(SimSettings.getInstance().getDeepFileLoggingEnabled()){
				file1 = new File(outputFolder, filePrefix+"_SUCCESS.log");
				fileWriter1 = new FileWriter(file1, true);
				bufferedWriter1 = new BufferedWriter(fileWriter1);
				
				file2 = new File(outputFolder, filePrefix+"_FAIL.log");
				fileWriter2 = new FileWriter(file2, true);
				bufferedWriter2 = new BufferedWriter(fileWriter2);
			}
			
			file3 = new File(outputFolder, filePrefix+"_VM_LOAD.log");
			fileWriter3 = new FileWriter(file3, true);
			bufferedWriter3 = new BufferedWriter(fileWriter3);

			file4 = new File(outputFolder, filePrefix+"_GENERIC.log");
			fileWriter4 = new FileWriter(file4, true);
			bufferedWriter4 = new BufferedWriter(fileWriter4);

			file5 = new File(outputFolder, filePrefix+"_LOCATION.log");
			fileWriter5 = new FileWriter(file5, true);
			bufferedWriter5 = new BufferedWriter(fileWriter5);
			
			if(SimSettings.getInstance().getDeepFileLoggingEnabled()){
				appendToFile(bufferedWriter1, "# taskId;dataCenterId;hostId;vmId;vmType;taskType;taskLenght;taskInputType;taskOutputSize;taskStartTime;taskEndTime;nwDelay");
				appendToFile(bufferedWriter2, "# taskId;dataCenterId;hostId;vmId;vmType;taskType;taskLenght;taskInputType;taskOutputSize;taskStartTime;taskEndTime;failReason");
			}
			
			appendToFile(bufferedWriter3, "# time;load");
			appendToFile(bufferedWriter4, "# completedTask;uncompletedTask;rejectedTask;failedDueToMobilityTask;avgFailure;avgServiceTime;avgNetworkDelay;avgServerLoad;avgCost");
			appendToFile(bufferedWriter5, "# time;#ofClient@PlaceType1;#ofClient@PlaceTypen");
		}
		
		//extract the result of each task and write it to the file if required
		for (Map.Entry<Integer, LogItem> entry : taskMap.entrySet()) {
			Integer key = entry.getKey();
			LogItem value = entry.getValue();
			
			if(value.isInWarmUpPeriod())
				continue;
		    
			if(value.getStatus() == SimLogger.TASK_STATUS.COMLETED){
				if(value.getVmType() == SimSettings.VM_TYPES.CLOUD_VM.ordinal()){
					completedTaskOnCloud++;
					totalWanDelay += value.getNetworkDelay();
				}
				else{
					completedTaskOnCloudlet++;
					totalLanDelay += value.getNetworkDelay();
				}
				totalCost += value.getCost();
				totalServiceTime += value.getServiceTime();
				totalNetworkDelay += value.getNetworkDelay();
				totalProcessingTime += (value.getServiceTime() - value.getNetworkDelay());
				
				if(fileLogEnabled && SimSettings.getInstance().getDeepFileLoggingEnabled())
					appendToFile(bufferedWriter1, value.toString(key));
			}
			else if(value.getStatus() == SimLogger.TASK_STATUS.REJECTED_DUE_TO_VM_CAPACITY){
				rejectedTaskDoToVmCapacity++;
				if(fileLogEnabled && SimSettings.getInstance().getDeepFileLoggingEnabled())
					appendToFile(bufferedWriter2, value.toString(key));
			}
			else if(value.getStatus() == SimLogger.TASK_STATUS.REJECTED_DUE_TO_BANDWIDTH){
				rejectedTaskDoToBandwidth++;
				if(fileLogEnabled && SimSettings.getInstance().getDeepFileLoggingEnabled())
					appendToFile(bufferedWriter2, value.toString(key));
			}
			else if(value.getStatus() == SimLogger.TASK_STATUS.UNFINISHED_DUE_TO_BANDWIDTH){
				failedTaskDuetoBandwidth++;
				if(fileLogEnabled && SimSettings.getInstance().getDeepFileLoggingEnabled())
					appendToFile(bufferedWriter2, value.toString(key));
			}
			else if(value.getStatus() == SimLogger.TASK_STATUS.UNFINISHED_DUE_TO_MOBILITY){
				failedTaskDuetoMobility++;
				if(fileLogEnabled && SimSettings.getInstance().getDeepFileLoggingEnabled())
					appendToFile(bufferedWriter2, value.toString(key));
			}
			else {
				if(value.getVmType() == SimSettings.VM_TYPES.CLOUD_VM.ordinal())
					uncompletedTaskOnCloud++;
				else
					uncompletedTaskOnCloudlet++;
			}
		}
		totalFailedTask = rejectedTaskDoToVmCapacity+rejectedTaskDoToBandwidth+failedTaskDuetoBandwidth;
		totalCompletedTask = completedTaskOnCloudlet+completedTaskOnCloud;
		
		//calculate server load
		for(VmLoadLogItem entry : vmLoadList){
			totalVmLoad += entry.getLoad();
			if(fileLogEnabled)
				appendToFile(bufferedWriter3, entry.toString());
		}
			
		if(fileLogEnabled){
			//write location info to file
			for(int t=1; t<(SimSettings.getInstance().getSimulationTime()/SimSettings.getInstance().getVmLocationLogInterval()); t++){
				int[] locationInfo = new int[SimSettings.PLACE_TYPES.values().length];
				for(int i=0; i<SimManager.getInstance().getNumOfMobileDevice(); i++) {
					
					Location loc = SimManager.getInstance().getMobilityModel().getLocation(i, t*SimSettings.getInstance().getVmLocationLogInterval());
					SimSettings.PLACE_TYPES placeType = loc.getPlaceType();
					locationInfo[placeType.ordinal()]++;
				}
				
				Double time=t*SimSettings.getInstance().getVmLocationLogInterval();
				bufferedWriter5.write(time.toString());
				for(int i=0; i<locationInfo.length; i++)
					bufferedWriter5.write(SimSettings.DELIMITER + locationInfo[i]);
				
				bufferedWriter5.newLine();
			}
			
			//write generic results
			String genericResult = Integer.toString(completedTaskOnCloudlet) + SimSettings.DELIMITER + 
					Integer.toString(uncompletedTaskOnCloudlet) + SimSettings.DELIMITER +
					Integer.toString(completedTaskOnCloud) + SimSettings.DELIMITER + 
					Integer.toString(uncompletedTaskOnCloud) + SimSettings.DELIMITER +
					Integer.toString(rejectedTaskDoToVmCapacity) + SimSettings.DELIMITER +
					Integer.toString(rejectedTaskDoToBandwidth) + SimSettings.DELIMITER +
					Integer.toString(failedTaskDuetoBandwidth) + SimSettings.DELIMITER +
					Integer.toString(failedTaskDuetoMobility) + SimSettings.DELIMITER +
					Double.toString(((double)totalFailedTask*(double)100)/(double)(totalCompletedTask+totalFailedTask)) + SimSettings.DELIMITER +
					Double.toString(totalServiceTime/(double)totalCompletedTask) + SimSettings.DELIMITER +
					Double.toString(totalNetworkDelay/(double)totalCompletedTask) + SimSettings.DELIMITER +
					Double.toString(totalLanDelay/(double)totalCompletedTask) + SimSettings.DELIMITER +
					Double.toString(totalWanDelay/(double)totalCompletedTask) + SimSettings.DELIMITER +
					Double.toString(totalProcessingTime/(double)totalCompletedTask) + SimSettings.DELIMITER +
					Double.toString(totalVmLoad/(double)vmLoadList.size()) + SimSettings.DELIMITER +
					Double.toString(totalCost/(double)totalCompletedTask);
			
			appendToFile(bufferedWriter4,genericResult);
			
			//close open files
			if(SimSettings.getInstance().getDeepFileLoggingEnabled()){
				bufferedWriter1.close();
				bufferedWriter2.close();
			}
			bufferedWriter3.close();
			bufferedWriter4.close();
			bufferedWriter5.close();
		}
		
		//printout important results
		printLine("# of uncompleted tasks on Cloudlet/Cloud: " + uncompletedTaskOnCloudlet + "/" + uncompletedTaskOnCloud);
		printLine("# of completed task on Cloudlet/Cloud: " + (completedTaskOnCloudlet+uncompletedTaskOnCloudlet) + "/" + (completedTaskOnCloud+uncompletedTaskOnCloud));
		printLine("# of rejected tasks (due to vm capacity): " + rejectedTaskDoToVmCapacity);
		printLine("# of rejected tasks (due to bandwidth): " + rejectedTaskDoToBandwidth);
		printLine("# of failed tasks (due to bandwidth): " + failedTaskDuetoBandwidth);
		printLine("# of failed tasks (due to mobility): " + failedTaskDuetoMobility);
		printLine("percentage of failed task: " + String.format("%.6f", ((double)totalFailedTask*(double)100)/(double)(totalCompletedTask+totalFailedTask)) + "%");
		printLine("average service time: " + String.format("%.6f", totalServiceTime/(double)totalCompletedTask) + " seconds. ("
				+ "processing time: " + String.format("%.6f", totalProcessingTime/(double)totalCompletedTask) + ")");
		printLine("average netwrok delay: " + String.format("%.6f", totalNetworkDelay/(double)totalCompletedTask) + " seconds. ("
				+ "LAN delay: " + String.format("%.6f", totalLanDelay/(double)totalCompletedTask) + ", "
				+ "WAN delay: " + String.format("%.6f", totalWanDelay/(double)totalCompletedTask) + ")");
		printLine("average server utilization: " + String.format("%.6f", totalVmLoad/(double)vmLoadList.size()) + "%");
		printLine("average cost: " + totalCost/totalCompletedTask + "$");

		//clear related collections (map list etc.)
		taskMap.clear();
		vmLoadList.clear();
	}
}

class VmLoadLogItem
{
	private double time;
	private double vmLoad;
	
	VmLoadLogItem(double _time, double _vmLoad)
	{
		time = _time;
		vmLoad = _vmLoad;
	}
	public double getLoad()
	{
		return vmLoad;
	}
	public String toString()
	{
		return time + SimSettings.DELIMITER + vmLoad;
	}
}
class LogItem
{
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
	LogItem (double _taskStartTime, int _taskType, int _taskLenght, int _taskInputType, int _taskOutputSize)
	{
		taskStartTime = _taskStartTime;
		taskType=_taskType;
		taskLenght=_taskLenght;
		taskInputType=_taskInputType;
		taskOutputSize=_taskOutputSize;
		status = SimLogger.TASK_STATUS.CREATED;
		taskEndTime = 0;
		
		if(_taskStartTime < SimSettings.getInstance().getWarmUpPeriod())
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
		vmType=_vmType;
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
	public void taskRejectedDueToBandwidth(double _taskRejectTime) {
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
	public boolean isInWarmUpPeriod(){
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
	public SimLogger.TASK_STATUS getStatus(){
		return status;
	}
	public int getVmType(){
		return vmType;
	}
	public String toString (int taskId)
	{
		String result = taskId + SimSettings.DELIMITER +
				datacenterId + SimSettings.DELIMITER +
				hostId + SimSettings.DELIMITER +
				vmId + SimSettings.DELIMITER +
				vmType + SimSettings.DELIMITER +
				taskType + SimSettings.DELIMITER +
				taskLenght + SimSettings.DELIMITER +
				taskInputType + SimSettings.DELIMITER +
				taskOutputSize + SimSettings.DELIMITER +
				taskStartTime +  SimSettings.DELIMITER +
				taskEndTime +  SimSettings.DELIMITER;
		
		if(status == SimLogger.TASK_STATUS.COMLETED)
			result += networkDelay;
		else if(status == SimLogger.TASK_STATUS.REJECTED_DUE_TO_VM_CAPACITY)
			result +="1"; //failure reason 1
		else if(status == SimLogger.TASK_STATUS.REJECTED_DUE_TO_BANDWIDTH)
			result +="2"; //failure reason 2
		else if(status == SimLogger.TASK_STATUS.UNFINISHED_DUE_TO_BANDWIDTH)
			result +="3"; //failure reason 3
		else if(status == SimLogger.TASK_STATUS.UNFINISHED_DUE_TO_MOBILITY)
			result +="4"; //failure reason 4
		else
			result +="0"; //default failure reason
		return result;
	}
}