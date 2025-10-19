/*
 * Title:        EdgeCloudSim - Edge Orchestrator
 * 
 * Description: 
 * SampleEdgeOrchestrator offloads tasks to proper server
 * by considering WAN bandwidth and edge server utilization.
 * After the target server is decided, the least loaded VM is selected.
 * If the target server is a remote edge server, MAN is used.
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app2;

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

/**
 * Sample edge orchestrator for multi-tier edge-cloud computing scenarios.
 * Implements intelligent offloading decisions based on WAN bandwidth, edge server utilization,
 * and hybrid policies. Supports both SINGLE_TIER and TWO_TIER_WITH_EO deployment models.
 */
public class SampleEdgeOrchestrator extends EdgeOrchestrator {
	
	/** Number of edge hosts for load balancing calculations */
	private int numberOfHost;

	/**
	 * Constructor for sample edge orchestrator.
	 * @param _policy Orchestration policy (NETWORK_BASED, UTILIZATION_BASED, HYBRID)
	 * @param _simScenario Simulation scenario (SINGLE_TIER, TWO_TIER_WITH_EO)
	 */
	public SampleEdgeOrchestrator(String _policy, String _simScenario) {
		super(_policy, _simScenario);
	}

	/**
	 * Initialize orchestrator with edge host count for load balancing.
	 */
	@Override
	public void initialize() {
		numberOfHost=SimSettings.getInstance().getNumOfEdgeHosts();
	}

	/**
	 * Determines the target device for task offloading based on orchestration policy and scenario.
	 * The edge orchestrator runs in a distributed manner on edge devices for real-time decisions.
	 * 
	 * @param task The task to be offloaded
	 * @return Device ID: CLOUD_DATACENTER_ID for cloud, GENERIC_EDGE_DEVICE_ID for edge
	 */
	@Override
	public int getDeviceToOffload(Task task) {
		int result = 0;
		
		// Scenario-based offloading decision logic
		if(simScenario.equals("SINGLE_TIER")){
			// Single-tier scenario: all tasks go to edge servers only
			result = SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		else if(simScenario.equals("TWO_TIER_WITH_EO")){
			// Two-tier scenario: intelligent decision between edge and cloud based on policy
			
			// Create dummy task (1 Mbit = 128 KB) to probe current WAN bandwidth
			Task dummyTask = new Task(0, 0, 0, 0, 128, 128, new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
			
			// Calculate current WAN bandwidth availability
			double wanDelay = SimManager.getInstance().getNetworkModel().getUploadDelay(task.getMobileDeviceId(),
					SimSettings.CLOUD_DATACENTER_ID, dummyTask);
			
			double wanBW = (wanDelay == 0) ? 0 : (1 / wanDelay); // Convert delay to bandwidth (Mbps)
			
			// Get current edge server utilization percentage
			double edgeUtilization = SimManager.getInstance().getEdgeServerManager().getAvgUtilization();
			
			// Policy-based offloading decision making
			if(policy.equals("NETWORK_BASED")){
				// Network-based policy: offload to cloud if WAN bandwidth is sufficient (> 6 Mbps)
				if(wanBW > 6)
					result = SimSettings.CLOUD_DATACENTER_ID;
				else
					result = SimSettings.GENERIC_EDGE_DEVICE_ID;
			}
			else if(policy.equals("UTILIZATION_BASED")){
				// Utilization-based policy: offload to cloud if edge servers are overloaded (> 80%)
				double utilization = edgeUtilization;
				if(utilization > 80)
					result = SimSettings.CLOUD_DATACENTER_ID;
				else
					result = SimSettings.GENERIC_EDGE_DEVICE_ID;
			}
			else if(policy.equals("HYBRID")){
				// Hybrid policy: offload to cloud only if both conditions are met
				// (sufficient WAN bandwidth AND high edge utilization)
				double utilization = edgeUtilization;
				if(wanBW > 6 && utilization > 80)
					result = SimSettings.CLOUD_DATACENTER_ID;
				else
					result = SimSettings.GENERIC_EDGE_DEVICE_ID;
			}
			else {
				SimLogger.printLine("Unknown edge orchestrator policy! Terminating simulation...");
				System.exit(0);
			}
		}
		else {
			SimLogger.printLine("Unknown simulation scenario! Terminating simulation...");
			System.exit(0);
		}
		return result;
	}

	/**
	 * Selects specific VM for task execution using Least Loaded algorithm.
	 * Finds the VM with highest available capacity that can handle the task requirements.
	 * 
	 * @param task The task to be executed
	 * @param deviceId Target device ID (cloud or edge)
	 * @return Selected VM instance or null if no suitable VM found
	 */
	@Override
	public Vm getVmToOffload(Task task, int deviceId) {
		Vm selectedVM = null;
		
		if(deviceId == SimSettings.CLOUD_DATACENTER_ID){
			// Select VM on cloud servers using Least Loaded algorithm
			double selectedVmCapacity = 0; // Start with minimum value for comparison
			List<Host> list = SimManager.getInstance().getCloudServerManager().getDatacenter().getHostList();
			for (int hostIndex=0; hostIndex < list.size(); hostIndex++) {
				List<CloudVM> vmArray = SimManager.getInstance().getCloudServerManager().getVmList(hostIndex);
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					
					// Select VM with highest available capacity that can handle the task
					if(requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity){
						selectedVM = vmArray.get(vmIndex);
						selectedVmCapacity = targetVmCapacity;
					}
	            }
			}
		}
		else if(deviceId == SimSettings.GENERIC_EDGE_DEVICE_ID){
			// Select VM on edge servers using Least Loaded algorithm
			double selectedVmCapacity = 0; // Start with minimum value for comparison
			for(int hostIndex=0; hostIndex<numberOfHost; hostIndex++){
				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex);
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					
					// Select VM with highest available capacity that can handle the task
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