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

package edu.boun.edgecloudsim.applications.tutorial3;

import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;

import edu.boun.edgecloudsim.cloud_server.CloudVM;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.edge_client.CpuUtilizationModel_Custom;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

/**
 * Responsibilities:
 * - Decide offloading target: cloud vs generic edge datacenter
 * - Policies:
 *     NETWORK_BASED      : prefer cloud if estimated uplink WAN BW > 5 Mbps
 *     UTILIZATION_BASED  : prefer cloud if overall edge utilization > 75%
 *     RANDOM             : 50/50 coin flip
 * - VM selection (both cloud and edge): Least Loaded (pick VM with largest residual CPU capacity that fits)
 * Capacity definitions:
 *     requiredCapacity = predicted CPU % for task on VM type
 *     targetVmCapacity = 100 - current utilized CPU %
 * Returns null if no VM can host task (caller handles rejection).
 */
public class SampleEdgeOrchestrator extends EdgeOrchestrator {
	// numberOfHost cached to avoid repeated settings lookups
	private int numberOfHost; //used by load balancer

	public SampleEdgeOrchestrator(String _policy, String _simScenario) {
		super(_policy, _simScenario);
	}

	@Override
	public void initialize() {
		// Cache total edge host count for iteration in getVmToOffload
		numberOfHost=SimSettings.getInstance().getNumOfEdgeHosts();
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

		// Create a tiny dummy task (128 KB up/down ~ 1 Mbit total each way) to probe WAN delay
		// Used to estimate available WAN bandwidth = transferred_bits / delay
		// (Simplistic instantaneous probe; ignores contention dynamics)
		Task dummyTask = new Task(0, 0, 0, 0, 128, 128, new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
		
		double wanDelay = SimManager.getInstance().getNetworkModel().getUploadDelay(task.getMobileDeviceId(),
				SimSettings.CLOUD_DATACENTER_ID, dummyTask /* 1 Mbit */);
		double wanBW = (wanDelay == 0) ? 0 : (1 / wanDelay); /* Mbps (since dummy payload ~1 Mbit) */

		// Aggregate edge utilization across all edge VMs (percentage basis)
		double edgeUtilization = SimManager.getInstance().getEdgeServerManager().getAvgUtilization();
		

		if(policy.equals("NETWORK_BASED")){
			// Threshold heuristic: if WAN > 5 Mbps offload to cloud else stay at edge
			if(wanBW > 5)
				result = SimSettings.CLOUD_DATACENTER_ID;
			else
				result = SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		else if(policy.equals("UTILIZATION_BASED")){
			// If edge utilization exceeds 75%, divert load to cloud to prevent saturation
			double utilization = edgeUtilization;
			if(utilization > 75)
				result = SimSettings.CLOUD_DATACENTER_ID;
			else
				result = SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		else if(policy.equals("RANDOM")){
			// Fair random split between edge and cloud resources
			double randomNumber = SimUtils.getRandomDoubleNumber(0, 1);
			if(randomNumber < 0.5)
				result = SimSettings.CLOUD_DATACENTER_ID;
			else
				result = SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		else {
			// Unknown policy => configuration error
			SimLogger.printLine("Unknown edge orchestrator policy! Terminating simulation...");
			System.exit(0);
		}

		return result;
	}

	@Override
	public Vm getVmToOffload(Task task, int deviceId) {
		// Select VM with maximum residual capacity that can host predicted load (Least Loaded / WORST FIT)
		Vm selectedVM = null;
		
		if(deviceId == SimSettings.CLOUD_DATACENTER_ID){
			// Iterate all cloud hosts and VMs
			// Select VM on cloud devices via Least Loaded algorithm!
			double selectedVmCapacity = 0; //start with min value
			List<Host> list = SimManager.getInstance().getCloudServerManager().getDatacenter().getHostList();
			for (int hostIndex=0; hostIndex < list.size(); hostIndex++) {
				List<CloudVM> vmArray = SimManager.getInstance().getCloudServerManager().getVmList(hostIndex);
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					// requiredCapacity: predicted CPU% for this VM type
					// targetVmCapacity: current free CPU% on the VM
					// Accept and update best if residual capacity larger
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					if(requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity){
						selectedVM = vmArray.get(vmIndex);
						selectedVmCapacity = targetVmCapacity;
					}
	            }
			}
		}
		else if(deviceId == SimSettings.GENERIC_EDGE_DEVICE_ID){
			// Iterate all edge hosts and their VMs similarly
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
		else{
			// Defensive: unexpected device id
			SimLogger.printLine("Unknown device id! The simulation has been terminated.");
			System.exit(0);
		}
		
		return selectedVM;
	}

	@Override
	public void processEvent(SimEvent arg0) {
		// No asynchronous internal events required (stateless orchestrator)
		// Nothing to do!
	}

	@Override
	public void shutdownEntity() {
		// No resources to release
		// Nothing to do!
	}

	@Override
	public void startEntity() {
		// No startup scheduling needed
		// Nothing to do!
	}

}