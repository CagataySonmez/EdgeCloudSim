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

package edu.boun.edgecloudsim.applications.tutorial1;

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
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

// Orchestrator responsibilities:
// - Decide destination device (edge / cloud / mobile) for a task
// - Select a VM on that device according to a placement (fit) policy
// Current implementation: only generic edge device supported
// VM selection criteria: sufficient residual CPU capacity for predicted task demand
// Policies implemented:
//   RANDOM_FIT : pick random host, then random VM if capacity fits
//   WORST_FIT  : choose VM with largest free capacity (greedy spread)
//   BEST_FIT   : choose VM with smallest free capacity that still fits (packing)
//   FIRST_FIT  : first encountered VM (scans hosts in order)
//   NEXT_FIT   : continuation of FIRST_FIT remembering last (reduces scan cost)
// If no VM satisfies capacity, returns null (caller may queue or drop task)
// Capacity units: percentage (0-100) based on utilization model prediction vs current usage

public class SampleEdgeOrchestrator extends EdgeOrchestrator {
	// numberOfHost: total edge hosts (for iteration / wrap-around)
	// lastSelectedHostIndex: remembers last host used by NEXT_FIT
	// lastSelectedVmIndexes: per-host last VM index used by NEXT_FIT
	
	private int numberOfHost; //used by load balancer
	private int lastSelectedHostIndex; //used by load balancer
	private int[] lastSelectedVmIndexes; //used by each host individually
	
	public SampleEdgeOrchestrator(String _policy, String _simScenario) {
		super(_policy, _simScenario);
	}

	@Override
	public void initialize() {
		// Obtain total hosts from settings
		numberOfHost=SimSettings.getInstance().getNumOfEdgeHosts();
		
		// Initialize NEXT_FIT tracking indices to sentinel (-1 means not started)
		lastSelectedHostIndex = -1;
		lastSelectedVmIndexes = new int[numberOfHost];
		for(int i=0; i<numberOfHost; i++)
			lastSelectedVmIndexes[i] = -1;
	}

	/*
	 * (non-Javadoc)
	 * @see edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator#getDeviceToOffload(edu.boun.edgecloudsim.edge_client.Task)
	 * 
	 * It is assumed that the edge orchestrator app is running on the edge devices in a distributed manner
	 */
	@Override
	public int getDeviceToOffload(Task task) {
		// Decide which device category to offload to.
		// Here only DEFAULT_SCENARIO supported -> always generic edge device.
		// Could be extended to include cloud / mobile fallback logic.
		int result = 0;

		if(simScenario.equals("DEFAULT_SCENARIO")){
			result = SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		else {
			SimLogger.printLine("Unknown simulation scenario! Terminating simulation...");
			System.exit(0);
		}
		return result;
	}

	@Override
	public Vm getVmToOffload(Task task, int deviceId) {
		// Predictive selection:
		// requiredCapacity = estimated CPU percentage this task will consume on VM type
		// targetVmCapacity = remaining (free) CPU percentage on VM at current simulation time
		// Accept if requiredCapacity <= targetVmCapacity
		Vm selectedVM = null;
		
		if(deviceId == SimSettings.GENERIC_EDGE_DEVICE_ID){
			if(policy.equalsIgnoreCase("RANDOM_FIT")){
				// RANDOM_FIT:
				// Pick host uniformly at random, then VM uniformly.
				// Accept only if capacity fits; otherwise returns null (no retry here).
				int randomHostIndex = SimUtils.getRandomNumber(0, numberOfHost-1);
				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(randomHostIndex);
				int randomIndex = SimUtils.getRandomNumber(0, vmArray.size()-1);
				
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(randomIndex).getVmType());
				double targetVmCapacity = (double)100 - vmArray.get(randomIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				if(requiredCapacity <= targetVmCapacity)
					selectedVM = vmArray.get(randomIndex);
				// requiredCapacity vs targetVmCapacity check:
				// If insufficient, caller will interpret null (e.g., task may wait or be dropped).
			}
			else if(policy.equalsIgnoreCase("WORST_FIT")){
				// WORST_FIT:
				// Scan all VMs; choose VM with maximum free capacity that can host task.
				// Goal: load balancing / minimize risk of future rejection.
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
			else if(policy.equalsIgnoreCase("BEST_FIT")){
				// BEST_FIT:
				// Scan all VMs; choose VM with minimal sufficient free capacity.
				// Goal: consolidate load, potentially improving energy efficiency.
				double selectedVmCapacity = 101; //start with max value
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
				// FIRST_FIT:
				// Sequential scan; pick first acceptable VM then stop.
				// Low overhead; order of hosts/VMs biases distribution.
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
				// NEXT_FIT:
				// Similar to FIRST_FIT but resumes from last successful (host,vm) position.
				// Reduces repeated scanning when load is moderate.
				// hostCheckCounter prevents infinite loops (bounded by numberOfHost).
				int hostCheckCounter = 0;	
				while(selectedVM == null && hostCheckCounter < numberOfHost){
					int tries = 0;
					lastSelectedHostIndex = (lastSelectedHostIndex+1) % numberOfHost;

					List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(lastSelectedHostIndex);
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
			// Return null if no VM found (caller must handle)
			return selectedVM;
		}
		else{
			// If deviceId unknown -> fatal configuration error (terminate)
			SimLogger.printLine("Unknown device id! The simulation has been terminated.");
			System.exit(0);
		}
		
		return selectedVM;
	}

	@Override
	public void processEvent(SimEvent arg0) {
		// No asynchronous events to process (stateless orchestrator)
		// Nothing to do!
	}

	@Override
	public void shutdownEntity() {
		// Cleanup not required (no allocated resources / threads)
		// Nothing to do!
	}

	@Override
	public void startEntity() {
		// No startup scheduling needed
		// Nothing to do!
	}

}