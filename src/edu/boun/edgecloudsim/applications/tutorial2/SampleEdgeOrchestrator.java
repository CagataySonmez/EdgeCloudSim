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

package edu.boun.edgecloudsim.applications.tutorial2;

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
	// Responsibilities:
	// - Decide whether a task executes locally (mobile device) or at the serving edge host
	// - Select a VM on the chosen device ensuring enough residual CPU percentage
	// Policies implemented:
	//   RANDOM                : 50/50 coin flip between mobile and edge
	//   MOBILE_UTIL_HEURISTIC : keep local if mobile avg host utilization < 75%, else offload
	//   EDGE_UTIL_HEURISTIC   : offload if edge host (serving WLAN) utilization < 90%, else keep local
	// Utilization metric: average host CPU (percentage) abstracted by manager classes
	// VM capacity check: requiredCapacity (predicted) <= (100 - currentUtilization)
	// Edge VM choice: WORST FIT (largest free capacity) among VMs on serving host
	// Returns null when no VM satisfies capacity (caller handles rejection/queue)

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
			// RANDOM: unbiased random double [0,1); threshold 0.5 splits decision
			double randomNumber = SimUtils.getRandomDoubleNumber(0, 1);
			if(randomNumber < 0.5)
				result = SimSettings.MOBILE_DATACENTER_ID;
			else
				result = SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		else if(policy.equals("MOBILE_UTIL_HEURISTIC")){
			// MOBILE_UTIL_HEURISTIC:
			// Query mobile server manager for average utilization of this device's local host.
			// If below 75% keep computation local to save network latency.
			SampleMobileServerManager smsm = (SampleMobileServerManager)SimManager.getInstance().getMobileServerManager();
			double mobileUtilization = smsm.getAvgHostUtilization(task.getMobileDeviceId());
			if(mobileUtilization < 75)
				result = SimSettings.MOBILE_DATACENTER_ID;
			else
				result = SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		else if(policy.equals("EDGE_UTIL_HEURISTIC")){
			// EDGE_UTIL_HEURISTIC:
			// Determine serving WLAN -> host id mapping (1:1 in this scenario).
			// If edge host utilization below 90% offload to edge, else fallback to local.
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
			// Unknown policy is fatal misconfiguration.
			SimLogger.printLine("Unknown simulation policy! Terminating simulation...");
			System.exit(0);
		}
		return result;
	}

	@Override
	public Vm getVmToOffload(Task task, int deviceId) {
		Vm selectedVM = null;
		
		if (deviceId == SimSettings.MOBILE_DATACENTER_ID) {
			// Mobile execution: single MobileVM per device (index 0).
			// Predict required CPU vs free capacity; accept if fits.
			List<MobileVM> vmArray = SimManager.getInstance().getMobileServerManager().getVmList(task.getMobileDeviceId());
			double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(0).getVmType());
			double targetVmCapacity = (double) 100 - vmArray.get(0).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
			
			if (requiredCapacity <= targetVmCapacity)
				selectedVM = vmArray.get(0);
		 }
		else if(deviceId == SimSettings.GENERIC_EDGE_DEVICE_ID){
			// Edge execution:
			// 1) Find host in serving WLAN
			// 2) Iterate its VMs selecting the one with largest free capacity (WORST FIT)
			// 3) Use predictive model for required CPU
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
			// selectedVM remains null if no VM can host the task
		}
		
		return selectedVM;
	}

	@Override
	public void processEvent(SimEvent arg0) {
		// Stateless orchestrator: no asynchronous events to process.
	}

	@Override
	public void shutdownEntity() {
		// No resources to release.
	}

	@Override
	public void startEntity() {
		// No startup scheduling required.
	}

}