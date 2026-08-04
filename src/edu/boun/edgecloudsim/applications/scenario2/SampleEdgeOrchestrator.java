/*
 * Title:        EdgeCloudSim - Edge Orchestrator
 * 
 * Description: 
 * SampleEdgeOrchestrator offloads tasks to proper server
 * based on the applied scenario
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2022, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.scenario2;

import java.util.List;

import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.edge_client.CpuUtilizationModel_Custom;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileVM;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

public class SampleEdgeOrchestrator extends EdgeOrchestrator {
		
	public SampleEdgeOrchestrator(String _policy, String _simScenario) {
		super(_policy, _simScenario);
	}

	@Override
	public void initialize() {
	}

	/*
	 * (non-Javadoc)
	 * @see edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator#getDeviceToOffload(edu.boun.edgecloudsim.edge_client.Task)
	 * 
	 * It is assumed that the edge orchestrator app is running on the edge devices in a distributed manner
	 */
	@Override
	public int getDeviceToOffload(Task task) {
		int result = 0;

		if(policy.equals("RANDOM")){
			double randomNumber = SimUtils.getRandomDoubleNumber(0, 1);
			if(randomNumber < 0.5)
				result = SimSettings.MOBILE_DATACENTER_ID;
			else
				result = SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		else if(policy.equals("MOBILE_UTIL_HEURISTIC")){
			SampleMobileServerManager smsm = (SampleMobileServerManager)SimManager.getInstance().getMobileServerManager();
			double mobileUtilization = smsm.getAvgHostUtilization(task.getMobileDeviceId());
			if(mobileUtilization < 75)
				result = SimSettings.MOBILE_DATACENTER_ID;
			else
				result = SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		else if(policy.equals("EDGE_UTIL_HEURISTIC")){
			Location deviceLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(), CloudSim.clock());
			
			//in this scenario, clients can only offload to edge servers located in the serving WLAN place
			//serving wlan ID is equal to the host id because there is only one host in one place
			int relatedHostId=deviceLocation.getServingWlanId();
			
			SampleEdgeServerManager sesm = (SampleEdgeServerManager)SimManager.getInstance().getEdgeServerManager();
			double edgeUtilization = sesm.getAvgHostUtilization(relatedHostId);
			if(edgeUtilization < 90)
				result = SimSettings.GENERIC_EDGE_DEVICE_ID;
			else
				result = SimSettings.MOBILE_DATACENTER_ID;
		}
		else {
			SimLogger.printLine("Unknown simulation policy! Terminating simulation...");
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
			Location deviceLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(), CloudSim.clock());
			
			//in this scenario, clients can only offload to edge servers located in the serving WLAN place
			//serving wlan ID is equal to the host id because there is only one host in one place
			int relatedHostId=deviceLocation.getServingWlanId();
			
			List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(relatedHostId);
			
			//use least loaded policy (WORST FIT) to select VM on Host located in the serving WLAN place
			double selectedVmCapacity = 0; //start with min value
			for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
				double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				if(requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity){
					selectedVM = vmArray.get(vmIndex);
					selectedVmCapacity = targetVmCapacity;
				}
			}
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