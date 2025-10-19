/*
 * Title:        EdgeCloudSim - Basic Edge Orchestrator implementation
 * 
 * Description: 
 * BasicEdgeOrchestrator implements basic algorithms which are
 * first/next/best/worst/random fit algorithms while assigning
 * requests to the edge devices.
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app4;

import java.util.List;

import org.antlr.runtime.RecognitionException;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;

import net.sourceforge.jFuzzyLogic.FIS;
import edu.boun.edgecloudsim.cloud_server.CloudVM;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.EdgeHost;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.edge_client.CpuUtilizationModel_Custom;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.utils.SimLogger;

public class FuzzyEdgeOrchestrator extends EdgeOrchestrator {
	public static final double MAX_DATA_SIZE=2500;
	
	private int numberOfHost; //used by load balancer
	private FIS fis1 = null;
	private FIS fis2 = null;
	private FIS fis3 = null;

	public FuzzyEdgeOrchestrator(String _policy, String _simScenario) {
		super(_policy, _simScenario);
	}

	@Override
	public void initialize() {
		numberOfHost=SimSettings.getInstance().getNumOfEdgeHosts();
		
		try {
			fis1 = FIS.createFromString(FCL_definition.fclDefinition1, false);
			fis2 = FIS.createFromString(FCL_definition.fclDefinition2, false);
			fis3 = FIS.createFromString(FCL_definition.fclDefinition3, false);
		} catch (RecognitionException e) {
			SimLogger.printLine("Cannot generate FIS! Terminating simulation...");
			e.printStackTrace();
			System.exit(0);
		}
	}

	/**
	 * Determines the optimal device for task offloading using fuzzy logic-based decision making.
	 * Implements a two-stage fuzzy inference system for edge vs cloud and local vs remote edge decisions.
	 * 
	 * @param task The task to be offloaded
	 * @return Device ID where the task should be executed (cloud datacenter ID or edge host ID)
	 */
	@Override
	public int getDeviceToOffload(Task task) {
		int result = 0;
		
		// Determine optimal execution location based on fuzzy logic algorithms
		
		// Single-tier scenario: all tasks go to edge devices
		if(simScenario.equals("SINGLE_TIER")){
			result = SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		// Two-tier scenario: use fuzzy logic to choose between edge and cloud
		else if(simScenario.equals("TWO_TIER_WITH_EO")){
			int bestRemoteEdgeHostIndex = 0;
			int nearestEdgeHostIndex = 0;
			double nearestEdgeUtilization = 0;
			
			// Create dummy task with 1 Mbit (128 KB) file size to measure network capacity
			Task dummyTask = new Task(0, 0, 0, 0, 128, 128, new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
			
			// Measure WAN upload delay and calculate bandwidth in Mbps
			double wanDelay = SimManager.getInstance().getNetworkModel().getUploadDelay(task.getMobileDeviceId(),
					SimSettings.CLOUD_DATACENTER_ID, dummyTask);
			double wanBW = (wanDelay == 0) ? 0 : (1 / wanDelay); // Convert delay to bandwidth (Mbps)

			// Measure MAN delay for edge-to-edge communication
			double manDelay = SimManager.getInstance().getNetworkModel().getUploadDelay(SimSettings.GENERIC_EDGE_DEVICE_ID,
					SimSettings.GENERIC_EDGE_DEVICE_ID, dummyTask);
			
			// Get average utilization across all edge servers
			double edgeUtilization = SimManager.getInstance().getEdgeServerManager().getAvgUtilization();
			
			// Find the least loaded neighbor edge host and identify nearest edge server
			double bestRemoteEdgeUtilization = 100; // Start with maximum value (100% utilization)
			for(int hostIndex=0; hostIndex<numberOfHost; hostIndex++){
				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex);
	
				// Calculate total CPU utilization across all VMs on this host
				double totalUtilization=0;
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					totalUtilization += vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				}
				
				double avgUtilization = (totalUtilization / (double)(vmArray.size()));
				
				EdgeHost host = (EdgeHost)(vmArray.get(0).getHost()); // All VMs share the same host
				
				// Check if this is the nearest edge server (same WLAN coverage area)
				if(host.getLocation().getServingWlanId() == task.getSubmittedLocation().getServingWlanId()){
					nearestEdgeUtilization = totalUtilization / (double)(vmArray.size());
					nearestEdgeHostIndex = hostIndex;
				}
				// Track the best remote edge server with lowest utilization
				else if(avgUtilization < bestRemoteEdgeUtilization){
					bestRemoteEdgeHostIndex = hostIndex;
					bestRemoteEdgeUtilization = avgUtilization;
				}
			}

			// FUZZY_BASED policy: Use two-stage fuzzy inference system
			if(policy.equals("FUZZY_BASED")){
				int bestHostIndex = nearestEdgeHostIndex;
				double bestHostUtilization = nearestEdgeUtilization;
				
		        // Stage 1: FIS2 decides between nearest and remote edge servers
		        // Set input variables for edge server selection
		        fis2.setVariable("man_delay", manDelay);
		        fis2.setVariable("nearest_edge_uitl", nearestEdgeUtilization);
		        fis2.setVariable("best_remote_edge_uitl", bestRemoteEdgeUtilization);
		        
		        // Evaluate fuzzy inference system 2
		        fis2.evaluate();
		        
		        // Debug output for FIS2 (commented out for performance)
		        /*
		        SimLogger.printLine("########################################");
		        SimLogger.printLine("man bw: " + manBW);
		        SimLogger.printLine("nearest_edge_uitl: " + nearestEdgeUtilization);
		        SimLogger.printLine("best_remote_edge_uitl: " + bestRemoteEdgeUtilization);
		        SimLogger.printLine("offload_decision: " + fis2.getVariable("offload_decision").getValue());
		        SimLogger.printLine("########################################");
				*/
		        
		        // If FIS2 output > 50, choose remote edge server; otherwise use nearest
				if(fis2.getVariable("offload_decision").getValue() > 50){
					bestHostIndex = bestRemoteEdgeHostIndex;
					bestHostUtilization = bestRemoteEdgeUtilization;
				}
				
				// Get task delay sensitivity from lookup table
				double delay_sensitivity = SimSettings.getInstance().getTaskLookUpTable()[task.getTaskType()][12];

		        // Stage 2: FIS1 decides between selected edge server and cloud
		        // Set input variables for edge vs cloud decision
		        fis1.setVariable("wan_bw", wanBW);
		        fis1.setVariable("task_size", task.getCloudletLength());
		        fis1.setVariable("delay_sensitivity", delay_sensitivity);
		        fis1.setVariable("avg_edge_util", bestHostUtilization);
		        
		        // Evaluate fuzzy inference system 1
		        fis1.evaluate();
		        
		        // Debug output for FIS1 (commented out for performance)
		        /*
		        SimLogger.printLine("########################################");
		        SimLogger.printLine("wan bw: " + wanBW);
		        SimLogger.printLine("task_size: " + task.getCloudletLength());
		        SimLogger.printLine("delay_sensitivity: " + delay_sensitivity);
		        SimLogger.printLine("avg_edge_util: " + bestHostUtilization);  
		        SimLogger.printLine("offload_decision: " + fis1.getVariable("offload_decision").getValue());
		        SimLogger.printLine("########################################");
		        */
		        
		        // If FIS1 output > 50, offload to cloud; otherwise use selected edge server
		        if(fis1.getVariable("offload_decision").getValue() > 50){
					result = SimSettings.CLOUD_DATACENTER_ID;
		        }
				else{
					result = bestHostIndex;
				}
			}
			// FUZZY_COMPETITOR policy: Alternative fuzzy approach for video processing
			else if(policy.equals("FUZZY_COMPETITOR")){
				double utilization = edgeUtilization;
	        	double cpuSpeed = (double)100 - utilization; // Available CPU capacity
	        	double videoExecution = SimSettings.getInstance().getTaskLookUpTable()[task.getTaskType()][12];
	        	double dataSize = task.getCloudletFileSize() + task.getCloudletOutputSize();
	        	double normalizedDataSize = Math.min(MAX_DATA_SIZE, dataSize)/MAX_DATA_SIZE;
	        	
		        // Use FIS3 for video processing offload decisions
		        // Set input variables for video processing fuzzy logic
		        fis3.setVariable("wan_bw", wanBW);
		        fis3.setVariable("cpu_speed", cpuSpeed);
		        fis3.setVariable("video_execution", videoExecution);
		        fis3.setVariable("data_size", normalizedDataSize);
		        
		        // Evaluate fuzzy inference system 3
		        fis3.evaluate();
		        
		        // Debug output for FIS3 (commented out for performance)
		        /*
		        SimLogger.printLine("########################################");
		        SimLogger.printLine("wan bw: " + wanBW);
		        SimLogger.printLine("cpu_speed: " + cpuSpeed);
		        SimLogger.printLine("video_execution: " + videoExecution);
		        SimLogger.printLine("data_size: " + normalizedDataSize);  
		        SimLogger.printLine("offload_decision: " + fis3.getVariable("offload_decision").getValue());
		        SimLogger.printLine("########################################");
				*/
		        
		        // Decision based on FIS3 output for video processing tasks
		        if(fis3.getVariable("offload_decision").getValue() > 50)
					result = SimSettings.CLOUD_DATACENTER_ID;
				else
					result = SimSettings.GENERIC_EDGE_DEVICE_ID;
			}
			// NETWORK_BASED policy: Decision based solely on WAN bandwidth availability
			else if(policy.equals("NETWORK_BASED")){
				if(wanBW > 6) // High bandwidth threshold (6 Mbps)
					result = SimSettings.CLOUD_DATACENTER_ID;
				else
					result = SimSettings.GENERIC_EDGE_DEVICE_ID;
			}
			// UTILIZATION_BASED policy: Decision based on edge server utilization
			else if(policy.equals("UTILIZATION_BASED")){
				double utilization = edgeUtilization;
				if(utilization > 80) // High utilization threshold (80%)
					result = SimSettings.CLOUD_DATACENTER_ID;
				else
					result = SimSettings.GENERIC_EDGE_DEVICE_ID;
			}
			// HYBRID policy: Combined network and utilization-based decision
			else if(policy.equals("HYBRID")){
				double utilization = edgeUtilization;
				if(wanBW > 6 && utilization > 80) // Both conditions must be met
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
	 * Selects the optimal VM for task execution using Least Loaded algorithm.
	 * Finds the VM with highest available capacity that can accommodate the task.
	 * 
	 * @param task The task to be executed
	 * @param deviceId The target device ID (cloud or edge server)
	 * @return Selected VM for task execution or null if no suitable VM found
	 */
	@Override
	public Vm getVmToOffload(Task task, int deviceId) {
		Vm selectedVM = null;
		
		// Cloud datacenter VM selection
		if(deviceId == SimSettings.CLOUD_DATACENTER_ID){
			// Use Least Loaded algorithm for cloud VM selection
			double selectedVmCapacity = 0; // Start with minimum capacity requirement
			List<Host> list = SimManager.getInstance().getCloudServerManager().getDatacenter().getHostList();
			for (int hostIndex=0; hostIndex < list.size(); hostIndex++) {
				List<CloudVM> vmArray = SimManager.getInstance().getCloudServerManager().getVmList(hostIndex);
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					// Select VM with highest available capacity that meets requirements
					if(requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity){
						selectedVM = vmArray.get(vmIndex);
						selectedVmCapacity = targetVmCapacity;
					}
	            }
			}
		}
		// Generic edge device VM selection (search across all edge servers)
		else if(deviceId == SimSettings.GENERIC_EDGE_DEVICE_ID){
			// Use Least Loaded algorithm across all edge servers
			double selectedVmCapacity = 0; // Start with minimum capacity requirement
			for(int hostIndex=0; hostIndex<numberOfHost; hostIndex++){
				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex);
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					// Select VM with highest available capacity that meets requirements
					if(requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity){
						selectedVM = vmArray.get(vmIndex);
						selectedVmCapacity = targetVmCapacity;
					}
				}
			}
		}
		// Specific edge host VM selection
		else{
			// Use specified host for VM selection
			List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(deviceId);
			
			// Use Least Loaded algorithm on the specific edge server
			double selectedVmCapacity = 0; // Start with minimum capacity requirement
			for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
				double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				// Select VM with highest available capacity that meets requirements
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