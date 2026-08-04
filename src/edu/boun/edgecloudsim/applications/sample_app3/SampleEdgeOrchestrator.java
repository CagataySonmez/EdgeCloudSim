/*
 * Title:        EdgeCloudSim - Edge Orchestrator
 * 
 * Description: 
 * SampleEdgeOrchestrator offloads tasks to proper server
 * In this scenario mobile devices can also execute tasks
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app3;

import java.util.List;

/*CloudSIM */
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;

/*CLOUD*/
import edu.boun.edgecloudsim.cloud_server.CloudVM;

/*EDGE*/
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.edge_client.CpuUtilizationModel_Custom;
import edu.boun.edgecloudsim.edge_client.Task;

/*MOBILE*/
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileVM;

/*UTILS*/
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;


public class SampleEdgeOrchestrator extends EdgeOrchestrator {
	
	private int numberOfHost; //used by load balancer

	public SampleEdgeOrchestrator(String _policy, String _simScenario) {
		super(_policy, _simScenario);
	}

	@Override
	public void initialize() {
		numberOfHost=SimSettings.getInstance().getNumOfEdgeHosts();
	}

	/*
	 * (non-Javadoc)
	 * @see edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator#getDeviceToOffload(edu.boun.edgecloudsim.edge_client.Task)
	 * 
	*/
	@Override
	public int getDeviceToOffload(Task task) {
		int result = 0;

		//dummy task to simulate a task with 1 Mbit file size to upload and download 
		Task dummyTask = new Task(0, 0, 0, 0, 128, 128, new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
			
		double wanDelay = SimManager.getInstance().getNetworkModel().getUploadDelay(task.getMobileDeviceId(),
				SimSettings.CLOUD_DATACENTER_ID, dummyTask /* 1 Mbit */);
		
		double wanBW = (wanDelay == 0) ? 0 : (1 / wanDelay); /* Mbps */
		
		double edgeUtilization = SimManager.getInstance().getEdgeServerManager().getAvgUtilization();
		
		// ONL_{ }
		if(policy.equals("ONLY_EDGE")){
			result = SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		else if(policy.equals("ONLY_MOBILE")){
			result = SimSettings.MOBILE_DATACENTER_ID;
		}
		else if(policy.equals("ONLY_CLOUD")){
			result = SimSettings.CLOUD_DATACENTER_ID;
		}

		// RANDOM
		else if (policy.equals("RANDOM")) {
			double randomNumber = SimUtils.getRandomDoubleNumber(0, 1);

			// 33% chance to offload to mobile, 33% to edge, and 34% to cloud
			if (randomNumber < 0.33) {
				result = SimSettings.MOBILE_DATACENTER_ID;
			} else if (randomNumber < 0.66) {
				result = SimSettings.GENERIC_EDGE_DEVICE_ID;
			} else {
				result = SimSettings.CLOUD_DATACENTER_ID;
			}
		}



		else if (policy.equals("NETWORK_BASED")) {
			if (wanBW > 6) {
				// Offload to cloud if WAN bandwidth is high
				result = SimSettings.CLOUD_DATACENTER_ID;
			} else if (wanBW > 3) {
				// Offload to edge if WAN bandwidth is moderate
				result = SimSettings.GENERIC_EDGE_DEVICE_ID;
			} else {
				// Offload to mobile if WAN bandwidth is low
				result = SimSettings.MOBILE_DATACENTER_ID;
			}
		}
		else if (policy.equals("UTILIZATION_BASED")) {
			double utilization = edgeUtilization;
		
			if (utilization > 80) {
				// Offload to cloud if edge utilization is too high
				result = SimSettings.CLOUD_DATACENTER_ID;
			} else if (utilization < 40) {
				// Offload to mobile if edge utilization is low
				result = SimSettings.MOBILE_DATACENTER_ID;
			} else {
				// Offload to edge if utilization is moderate
				result = SimSettings.GENERIC_EDGE_DEVICE_ID;
			}
		}
		

		// else if (policy.equals("HYBRID")) {
		// 	double utilization = edgeUtilization;
		
		// 	// If the WAN bandwidth is sufficient and edge utilization is high, offload to the cloud
		// 	if (wanBW > 6 && utilization > 80) {
		// 		result = SimSettings.CLOUD_DATACENTER_ID;
		// 	}
		// 	// If edge utilization is moderate and bandwidth is reasonable, offload to edge
		// 	else if (utilization <= 80 && utilization > 40 && wanBW > 3) {
		// 		result = SimSettings.GENERIC_EDGE_DEVICE_ID;
		// 	}
		// 	// Fallback to edge if utilization is not too high
		// 	else {
		// 		result = SimSettings.MOBILE_DATACENTER_ID;
		// 	}
		// }
		

		else {
			SimLogger.printLine("Unknow edge orchestrator policy! Terminating simulation...");
			System.exit(0);
		}

		return result;
	}

	@Override
	public Vm getVmToOffload(Task task, int deviceId) {
		Vm selectedVM = null;
		
		if (deviceId == SimSettings.MOBILE_DATACENTER_ID) {
			List<MobileVM> vmArray = SimManager.getInstance().getMobileServerManager().getVmList(task.getMobileDeviceId());
			double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(0).getVmType());
			double targetVmCapacity = (double) 100 - vmArray.get(0).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
			
			if (requiredCapacity <= targetVmCapacity)
				selectedVM = vmArray.get(0);
		}
		else if(deviceId == SimSettings.GENERIC_EDGE_DEVICE_ID){
			//Select VM on edge devices via Least Loaded algorithm!
			double selectedVmCapacity = 0; //start with min value
			for(int hostIndex=0; hostIndex<numberOfHost; hostIndex++){
				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex);
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					if(requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity){
						selectedVM = vmArray.get(vmIndex);
						selectedVmCapacity = targetVmCapacity;
					}
				}
			}
		}
		else if(deviceId == SimSettings.CLOUD_DATACENTER_ID){
			//Select VM on cloud devices via Least Loaded algorithm!
			double selectedVmCapacity = 0; //start with min value
			List<Host> list = SimManager.getInstance().getCloudServerManager().getDatacenter().getHostList();
			for (int hostIndex=0; hostIndex < list.size(); hostIndex++) {
				List<CloudVM> vmArray = SimManager.getInstance().getCloudServerManager().getVmList(hostIndex);
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					if(requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity){
						selectedVM = vmArray.get(vmIndex);
						selectedVmCapacity = targetVmCapacity;
					}
	            }
			}
		}

		else{
			SimLogger.printLine("Unknown device id! The simulation has been terminated.");
			System.exit(0);
		}
		
		return selectedVM;
	}

	@Override
	public void processEvent(SimEvent arg0) {
		// Nothing to do!
	}

	@Override
	public void shutdownEntity() {
		// Nothing to do!
	}

	@Override
	public void startEntity() {
		// Nothing to do!
	}

}