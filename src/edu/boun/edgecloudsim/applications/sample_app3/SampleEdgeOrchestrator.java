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
import edu.boun.edgecloudsim.utils.SimLogger;

/**
 * Sample edge orchestrator for mobile-edge hybrid processing scenarios.
 * Supports three orchestration policies:
 * - ONLY_EDGE: All tasks offloaded to edge servers
 * - ONLY_MOBILE: All tasks processed locally on mobile devices
 * - HYBRID: Intelligent decision based on mobile device capacity
 */
public class SampleEdgeOrchestrator extends EdgeOrchestrator {
	
	/** Number of edge hosts for load balancing calculations */
	private int numberOfHost;

	/**
	 * Constructor for sample edge orchestrator.
	 * @param _policy Orchestration policy (ONLY_EDGE, ONLY_MOBILE, HYBRID)
	 * @param _simScenario Simulation scenario identifier
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
	 * Determines the target device for task offloading based on orchestration policy.
	 * 
	 * @param task The task to be offloaded
	 * @return Device ID: GENERIC_EDGE_DEVICE_ID for edge, MOBILE_DATACENTER_ID for mobile
	 */
	@Override
	public int getDeviceToOffload(Task task) {
		int result = 0;

		// Policy-based device selection for task offloading
		if(policy.equals("ONLY_EDGE")){
			// Force all tasks to edge servers
			result = SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		else if(policy.equals("ONLY_MOBILE")){
			// Force all tasks to mobile device local processing
			result = SimSettings.MOBILE_DATACENTER_ID;
		}
		else if(policy.equals("HYBRID")){
			// Intelligent decision based on mobile device capacity
			List<MobileVM> vmArray = SimManager.getInstance().getMobileServerManager().getVmList(task.getMobileDeviceId());
			double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(0).getVmType());
			double targetVmCapacity = (double) 100 - vmArray.get(0).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
			
			// If mobile device has sufficient capacity, process locally; otherwise offload to edge
			if (requiredCapacity <= targetVmCapacity)
				result = SimSettings.MOBILE_DATACENTER_ID;
			else
				result = SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		else {
			SimLogger.printLine("Unknown edge orchestrator policy! Terminating simulation...");
			System.exit(0);
		}

		return result;
	}

	/**
	 * Selects specific VM for task execution based on device type and load balancing.
	 * 
	 * @param task The task to be executed
	 * @param deviceId Target device ID (mobile or edge)
	 * @return Selected VM instance or null if no suitable VM found
	 */
	@Override
	public Vm getVmToOffload(Task task, int deviceId) {
		Vm selectedVM = null;
		
		if (deviceId == SimSettings.MOBILE_DATACENTER_ID) {
			// Select mobile VM if device has sufficient capacity
			List<MobileVM> vmArray = SimManager.getInstance().getMobileServerManager().getVmList(task.getMobileDeviceId());
			double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(0).getVmType());
			double targetVmCapacity = (double) 100 - vmArray.get(0).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
			
			// Verify mobile device capacity before assignment
			if (requiredCapacity <= targetVmCapacity)
				selectedVM = vmArray.get(0);
		 }
		else if(deviceId == SimSettings.GENERIC_EDGE_DEVICE_ID){
			// Select edge VM using Least Loaded algorithm for load balancing
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