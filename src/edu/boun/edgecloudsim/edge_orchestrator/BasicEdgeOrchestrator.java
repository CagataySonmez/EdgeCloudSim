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

package edu.boun.edgecloudsim.edge_orchestrator;

import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;

import edu.boun.edgecloudsim.cloud_server.CloudVM;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.edge_client.CpuUtilizationModel_Custom;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimUtils;

/**
 * Basic implementation of EdgeOrchestrator providing standard VM placement algorithms.
 * 
 * This class implements fundamental task placement strategies commonly used in
 * distributed computing environments. It supports multiple algorithms for VM
 * selection including first-fit, next-fit, best-fit, worst-fit, and random-fit.
 * The orchestrator can operate in different modes based on simulation scenarios
 * (single-tier, two-tier with load balancing, or location-aware placement).
 * 
 * Supported algorithms:
 * - FIRST_FIT: Selects the first VM that can accommodate the task
 * - NEXT_FIT: Uses round-robin selection starting from last selected VM
 * - BEST_FIT: Selects VM with minimum remaining capacity after task placement
 * - WORST_FIT: Selects VM with maximum remaining capacity (load balancing)
 * - RANDOM_FIT: Randomly selects from available VMs
 */
public class BasicEdgeOrchestrator extends EdgeOrchestrator {
	private int numberOfHost;                 // Total number of edge hosts for load balancing
	private int lastSelectedHostIndex;        // Last selected host index for round-robin algorithms
	private int[] lastSelectedVmIndexes;      // Last selected VM index for each host individually
	
	/**
	 * Constructs a BasicEdgeOrchestrator with specified policy and scenario.
	 * 
	 * @param _policy VM placement algorithm name (FIRST_FIT, NEXT_FIT, BEST_FIT, WORST_FIT, RANDOM_FIT)
	 * @param _simScenario Simulation scenario type (SINGLE_TIER, TWO_TIER_WITH_EO, etc.)
	 */
	public BasicEdgeOrchestrator(String _policy, String _simScenario) {
		super(_policy, _simScenario);
	}

	/**
	 * Initializes the orchestrator with edge infrastructure configuration.
	 * Sets up tracking arrays for round-robin algorithms and loads the
	 * number of edge hosts from simulation settings.
	 */
	@Override
	public void initialize() {
		numberOfHost=SimSettings.getInstance().getNumOfEdgeHosts();
		
		// Initialize round-robin tracking for load balancing algorithms
		lastSelectedHostIndex = -1;
		lastSelectedVmIndexes = new int[numberOfHost];
		for(int i=0; i<numberOfHost; i++)
			lastSelectedVmIndexes[i] = -1;
	}

	/**
	 * Determines the target device type for task offloading.
	 * In multi-tier scenarios, makes a probabilistic decision between
	 * cloud and edge execution based on application-specific preferences.
	 * 
	 * @param task The task requiring placement decision
	 * @return Device type ID (CLOUD_DATACENTER_ID or GENERIC_EDGE_DEVICE_ID)
	 */
	@Override
	public int getDeviceToOffload(Task task) {
		int result = SimSettings.GENERIC_EDGE_DEVICE_ID;
		if(!simScenario.equals("SINGLE_TIER")){
			// Probabilistic decision between cloud and edge based on task characteristics
			int CloudVmPicker = SimUtils.getRandomNumber(0, 100);
			
			// Check application's preference for cloud execution (from lookup table)
			if(CloudVmPicker <= SimSettings.getInstance().getTaskLookUpTable()[task.getTaskType()][1])
				result = SimSettings.CLOUD_DATACENTER_ID;
			else
				result = SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		
		return result;
	}
	
	/**
	 * Selects a specific VM for task execution based on device type and scenario.
	 * Routes to appropriate VM selection method based on target infrastructure:
	 * cloud (least loaded), load-balanced edge, or location-aware edge.
	 * 
	 * @param task The task to be assigned to a VM
	 * @param deviceId Target device type (cloud or edge)
	 * @return Selected VM instance, or null if no suitable VM found
	 */
	@Override
	public Vm getVmToOffload(Task task, int deviceId) {
		Vm selectedVM = null;
		
		if(deviceId == SimSettings.CLOUD_DATACENTER_ID){
			// Select VM on cloud using least loaded (worst-fit) algorithm
			double selectedVmCapacity = 0; // Start with minimum value to find maximum capacity
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
		else if(simScenario.equals("TWO_TIER_WITH_EO"))
			selectedVM = selectVmOnLoadBalancer(task);  // Global load balancing across all edge hosts
		else
			selectedVM = selectVmOnHost(task);          // Location-aware selection on nearest host
		
		return selectedVM;
	}
	
	/**
	 * Selects a VM on the edge host closest to the task's mobile device.
	 * Uses device location to determine the serving WLAN/host, then applies
	 * the configured placement algorithm within that host's VM pool.
	 * 
	 * @param task The task requiring VM assignment
	 * @return Selected EdgeVM on the location-appropriate host, or null if none available
	 */
	public EdgeVM selectVmOnHost(Task task){
		EdgeVM selectedVM = null;
		
		// Get current location of the mobile device generating the task
		Location deviceLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(), CloudSim.clock());
		// In this scenario, serving WLAN ID corresponds directly to host ID
		// (one host per geographical location/access point)
		int relatedHostId=deviceLocation.getServingWlanId();
		List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(relatedHostId);
		
		// Apply the configured placement algorithm within the selected host
		if(policy.equalsIgnoreCase("RANDOM_FIT")){
			// Randomly select a VM and check if it can accommodate the task
			int randomIndex = SimUtils.getRandomNumber(0, vmArray.size()-1);
			double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(randomIndex).getVmType());
			double targetVmCapacity = (double)100 - vmArray.get(randomIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
			if(requiredCapacity <= targetVmCapacity)
				selectedVM = vmArray.get(randomIndex);
		}
		else if(policy.equalsIgnoreCase("WORST_FIT")){
			// Select VM with maximum available capacity (load balancing)
			double selectedVmCapacity = 0; // Start with minimum value to find maximum capacity
			for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
				double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				if(requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity){
					selectedVM = vmArray.get(vmIndex);
					selectedVmCapacity = targetVmCapacity;
				}
			}
		}
		else if(policy.equalsIgnoreCase("BEST_FIT")){
			// Select VM with minimum available capacity that still fits the task (tight packing)
			double selectedVmCapacity = 101; // Start with maximum value to find minimum adequate capacity
			for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
				double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				if(requiredCapacity <= targetVmCapacity && targetVmCapacity < selectedVmCapacity){
					selectedVM = vmArray.get(vmIndex);
					selectedVmCapacity = targetVmCapacity;
				}
			}
		}
		else if(policy.equalsIgnoreCase("FIRST_FIT")){
			// Select the first VM that can accommodate the task (simple and fast)
			for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
				double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				if(requiredCapacity <= targetVmCapacity){
					selectedVM = vmArray.get(vmIndex);
					break;
				}
			}
		}
		else if(policy.equalsIgnoreCase("NEXT_FIT")){
			// Use round-robin selection starting from the last selected VM on this host
			int tries = 0;
			while(tries < vmArray.size()){
				lastSelectedVmIndexes[relatedHostId] = (lastSelectedVmIndexes[relatedHostId]+1) % vmArray.size();
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(lastSelectedVmIndexes[relatedHostId]).getVmType());
				double targetVmCapacity = (double)100 - vmArray.get(lastSelectedVmIndexes[relatedHostId]).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				if(requiredCapacity <= targetVmCapacity){
					selectedVM = vmArray.get(lastSelectedVmIndexes[relatedHostId]);
					break;
				}
				tries++;
			}
		}
		
		return selectedVM;
	}

	/**
	 * Selects a VM using global load balancing across all edge hosts.
	 * Applies the configured placement algorithm across the entire edge
	 * infrastructure rather than being constrained to location-based selection.
	 * 
	 * @param task The task requiring VM assignment
	 * @return Selected EdgeVM from any available edge host, or null if none available
	 */
	public EdgeVM selectVmOnLoadBalancer(Task task){
		EdgeVM selectedVM = null;
		
		if(policy.equalsIgnoreCase("RANDOM_FIT")){
			// Randomly select both host and VM for maximum distribution
			int randomHostIndex = SimUtils.getRandomNumber(0, numberOfHost-1);
			List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(randomHostIndex);
			int randomIndex = SimUtils.getRandomNumber(0, vmArray.size()-1);
			
			double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(randomIndex).getVmType());
			double targetVmCapacity = (double)100 - vmArray.get(randomIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
			if(requiredCapacity <= targetVmCapacity)
				selectedVM = vmArray.get(randomIndex);
		}
		else if(policy.equalsIgnoreCase("WORST_FIT")){
			// Find VM with maximum available capacity across all hosts (global load balancing)
			double selectedVmCapacity = 0; // Start with minimum value to find maximum capacity
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
		else if(policy.equalsIgnoreCase("BEST_FIT")){
			// Find VM with minimum adequate capacity across all hosts (tight packing globally)
			double selectedVmCapacity = 101; // Start with maximum value to find minimum adequate capacity
			for(int hostIndex=0; hostIndex<numberOfHost; hostIndex++){
				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex);
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					if(requiredCapacity <= targetVmCapacity && targetVmCapacity < selectedVmCapacity){
						selectedVM = vmArray.get(vmIndex);
						selectedVmCapacity = targetVmCapacity;
					}
				}
			}
		}
		else if(policy.equalsIgnoreCase("FIRST_FIT")){
			// Search hosts sequentially and select first available VM (simple global search)
			for(int hostIndex=0; hostIndex<numberOfHost; hostIndex++){
				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex);
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					if(requiredCapacity <= targetVmCapacity){
						selectedVM = vmArray.get(vmIndex);
						break;
					}
				}
			}
		}
		else if(policy.equalsIgnoreCase("NEXT_FIT")){
			// Use round-robin selection across hosts and VMs for global load distribution
			int hostCheckCounter = 0;	
			while(selectedVM == null && hostCheckCounter < numberOfHost){
				int tries = 0;
				// Move to next host in round-robin fashion
				lastSelectedHostIndex = (lastSelectedHostIndex+1) % numberOfHost;

				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(lastSelectedHostIndex);
				// Try VMs on current host in round-robin fashion
				while(tries < vmArray.size()){
					lastSelectedVmIndexes[lastSelectedHostIndex] = (lastSelectedVmIndexes[lastSelectedHostIndex]+1) % vmArray.size();
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(lastSelectedVmIndexes[lastSelectedHostIndex]).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(lastSelectedVmIndexes[lastSelectedHostIndex]).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					if(requiredCapacity <= targetVmCapacity){
						selectedVM = vmArray.get(lastSelectedVmIndexes[lastSelectedHostIndex]);
						break;
					}
					tries++;
				}

				hostCheckCounter++;
			}
		}
		
		return selectedVM;
	}

	/**
	 * Processes simulation events. Currently not implemented for basic orchestrator.
	 * 
	 * @param arg0 The simulation event to process
	 */
	@Override
	public void processEvent(SimEvent arg0) {
		// No event processing required for basic orchestrator
		
	}

	/**
	 * Performs cleanup operations when the simulation entity is shutdown.
	 * Currently not implemented for basic orchestrator.
	 */
	@Override
	public void shutdownEntity() {
		// No cleanup required for basic orchestrator
		
	}

	/**
	 * Performs initialization when the simulation entity starts.
	 * Currently not implemented for basic orchestrator.
	 */
	@Override
	public void startEntity() {
		// No start operations required for basic orchestrator
		
	}
}