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

package edu.boun.edgecloudsim.applications.scenario1;

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

public class SampleEdgeOrchestrator extends EdgeOrchestrator {
	
	private int numberOfHost; //used by load balancer
	private int lastSelectedHostIndex; //used by load balancer
	private int[] lastSelectedVmIndexes; //used by each host individually
	
	public SampleEdgeOrchestrator(String _policy, String _simScenario) {
		super(_policy, _simScenario);
	}

	@Override
	public void initialize() {
		numberOfHost=SimSettings.getInstance().getNumOfEdgeHosts();
		
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
		Vm selectedVM = null;
		
		if(deviceId == SimSettings.GENERIC_EDGE_DEVICE_ID){
			if(policy.equalsIgnoreCase("RANDOM_FIT")){
				int randomHostIndex = SimUtils.getRandomNumber(0, numberOfHost-1);
				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(randomHostIndex);
				int randomIndex = SimUtils.getRandomNumber(0, vmArray.size()-1);
				
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(randomIndex).getVmType());
				double targetVmCapacity = (double)100 - vmArray.get(randomIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				if(requiredCapacity <= targetVmCapacity)
					selectedVM = vmArray.get(randomIndex);
			}
			else if(policy.equalsIgnoreCase("WORST_FIT")){
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
			
			return selectedVM;
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