package edu.boun.edgecloudsim.applications.sample_app5;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.utils.SimLogger;

public class OrchestratorTrainerLogger {
	private static final double STAT_WINDOW = 1; //sec
	private static final String DELIMITER = ",";
	private Map<Integer, TrainerItem> trainerMap;
	private List<Double>[] TaskOffloadStats;

	private BufferedWriter learnerBW = null;

	class TrainerItem {
		int selectedDatacenter;
		int numOffloadedTask;
		double avgEdgeUtilization;
		double wanUploadDelay;
		double wanDownloadDelay;
		double gsmUploadDelay;
		double gsmDownloadDelay;
		double wlanUploadDelay;
		double wlanDownloadDelay;

		TrainerItem(int selectedDatacenter,
				int numOffloadedTask, double avgEdgeUtilization,
				double wanUploadDelay, double wanDownloadDelay,
				double gsmUploadDelay, double gsmDownloadDelay,
				double wlanUploadDelay, double wlanDownloadDelay)
		{
			this.selectedDatacenter = selectedDatacenter;
			this.avgEdgeUtilization = avgEdgeUtilization;
			this.numOffloadedTask = numOffloadedTask;
			this.wanUploadDelay = wanUploadDelay;
			this.wanDownloadDelay = wanDownloadDelay;
			this.gsmUploadDelay = gsmUploadDelay;
			this.gsmDownloadDelay = gsmDownloadDelay;
			this.wlanUploadDelay = wlanUploadDelay;
			this.wlanDownloadDelay = wlanDownloadDelay;
		}
	}

	@SuppressWarnings("unchecked")
	public OrchestratorTrainerLogger() {
		trainerMap = new HashMap<Integer, TrainerItem>();

		TaskOffloadStats = (ArrayList<Double>[])new ArrayList[3];
		TaskOffloadStats[0] = new ArrayList<Double>();
		TaskOffloadStats[1] = new ArrayList<Double>();
		TaskOffloadStats[2] = new ArrayList<Double>();
	}

	public void openTrainerOutputFile() {
		try {
			int numOfMobileDevices = SimManager.getInstance().getNumOfMobileDevice();
			String learnerOutputFile = SimLogger.getInstance().getOutputFolder() +
					"/" + numOfMobileDevices + "_learnerOutputFile.cvs";
			File learnerFile = new File(learnerOutputFile);
			FileWriter learnerFW = new FileWriter(learnerFile);
			learnerBW = new BufferedWriter(learnerFW);

			String line = "Decision"
					+ DELIMITER + "Result"
					+ DELIMITER + "ServiceTime"
					+ DELIMITER + "ProcessingTime"
					+ DELIMITER + "VehicleLocation"
					+ DELIMITER + "SelectedHostID"
					+ DELIMITER + "TaskLength"
					+ DELIMITER + "TaskInput"
					+ DELIMITER + "TaskOutput"
					+ DELIMITER + "WANUploadDelay"
					+ DELIMITER + "WANDownloadDelay"
					+ DELIMITER + "GSMUploadDelay"
					+ DELIMITER + "GSMDownloadDelay"
					+ DELIMITER + "WLANUploadDelay"
					+ DELIMITER + "WLANDownloadDelay"
					+ DELIMITER + "AvgEdgeUtilization"
					+ DELIMITER + "NumOffloadedTask";

			//for(int i=1; i<=SimSettings.getInstance().getNumOfEdgeHosts(); i++)
			//	line += DELIMITER + "Avg Edge(" + i + ") Utilization";

			learnerBW.write(line);
			learnerBW.newLine();

		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	public void closeTrainerOutputFile() {
		try {
			learnerBW.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public void saveStat(TrainerItem trainerItem, Task task,
			boolean result, double serviceTime) {
		String line = "";

		switch(trainerItem.selectedDatacenter){
		case VehicularEdgeOrchestrator.EDGE_DATACENTER:
			line = "EDGE";
			break;
		case VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_RSU:
			line = "CLOUD_VIA_RSU";
			break;
		case VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_GSM:
			line = "CLOUD_VIA_GSM";
			break;
		default:
			SimLogger.printLine("Unknown datacenter type");
			System.exit(1);
			break;
		}

		int submittedLocation = task.getSubmittedLocation().getServingWlanId();
		Double processingTime = task.getFinishTime()-task.getExecStartTime();

		line  +=  DELIMITER + (result == true ? "success" : "fail")
				+ DELIMITER + Double.toString(serviceTime)
				+ DELIMITER + Double.toString(processingTime)
				+ DELIMITER + Integer.toString(submittedLocation)
				+ DELIMITER + Integer.toString(task.getAssociatedHostId())
				+ DELIMITER + Long.toString(task.getCloudletLength())
				+ DELIMITER + Long.toString(task.getCloudletFileSize())
				+ DELIMITER + Long.toString(task.getCloudletOutputSize())
				+ DELIMITER + Double.toString(trainerItem.wanUploadDelay)
				+ DELIMITER + Double.toString(trainerItem.wanDownloadDelay)
				+ DELIMITER + Double.toString(trainerItem.gsmUploadDelay)
				+ DELIMITER + Double.toString(trainerItem.gsmDownloadDelay)
				+ DELIMITER + Double.toString(trainerItem.wlanUploadDelay)
				+ DELIMITER + Double.toString(trainerItem.wlanDownloadDelay)
				+ DELIMITER + Double.toString(trainerItem.avgEdgeUtilization)
				+ DELIMITER + Integer.toString(trainerItem.numOffloadedTask);

		//for(int i=0; i<trainerItem.edgeUtilizations.length; i++)
		//	line += DELIMITER + Double.toString(trainerItem.edgeUtilizations[i]);

		try {
			learnerBW.write(line);
			learnerBW.newLine();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public void addStat(int id, int selectedDatacenter,
			double wanUploadDelay, double wanDownloadDelay,
			double gsmUploadDelay, double gsmDownloadDelay,
			double wlanUploadDelay, double wlanDownloadDelay){

		addOffloadStat(selectedDatacenter-1);
		int numOffloadedTasks = getOffloadStat(selectedDatacenter-1);

		int numberOfHost = SimSettings.getInstance().getNumOfEdgeHosts();
		double totalUtlization = 0;
		double[] edgeUtilizations = new double[numberOfHost];
		for(int hostIndex=0; hostIndex<numberOfHost; hostIndex++){
			List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex);

			double utilization=0;
			for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
				utilization += vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
			}
			totalUtlization += utilization;

			edgeUtilizations[hostIndex] = utilization / (double)(vmArray.size());
		}

		double avgEdgeUtilization = totalUtlization / SimSettings.getInstance().getNumOfEdgeVMs();

		trainerMap.put(id,
				new TrainerItem(selectedDatacenter,
						numOffloadedTasks, avgEdgeUtilization,
						wanUploadDelay, wanDownloadDelay,
						gsmUploadDelay, gsmDownloadDelay,
						wlanUploadDelay, wlanDownloadDelay
						)
				);

	}

	public synchronized void addSuccessStat(Task task, double serviceTime) {
		TrainerItem trainerItem = trainerMap.remove(task.getCloudletId());
		saveStat(trainerItem, task, true, serviceTime);
	}

	public synchronized void addFailStat(Task task) {
		TrainerItem trainerItem = trainerMap.remove(task.getCloudletId());
		saveStat(trainerItem, task, false, 0);
	}

	public synchronized void addOffloadStat(int datacenterIdx) {
		double time = CloudSim.clock();
		for (Iterator<Double> iter = TaskOffloadStats[datacenterIdx].iterator(); iter.hasNext(); ) {
			if (iter.next() + STAT_WINDOW < time)
				iter.remove();
			else
				break;
		}
		TaskOffloadStats[datacenterIdx].add(time);
	}

	public synchronized int getOffloadStat(int datacenterIdx) {
		return TaskOffloadStats[datacenterIdx].size();
	}
}
