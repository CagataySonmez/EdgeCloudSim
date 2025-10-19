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

package edu.boun.edgecloudsim.applications.tutorial5;

import java.util.List;

import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.edge_client.CpuUtilizationModel_Custom;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimUtils;

// Responsibilities:
// - Decide offloading target (fixed to generic edge datacenter in this scenario)
// - Select an edge VM in the mobile device's serving WLAN (one host per WLAN)
// Policy here: RANDOM FIT within serving host with a capacity check.
// Capacity terms:
//   requiredCapacity = predicted CPU utilization (%) for this task on VM type
//   targetVmCapacity = residual CPU capacity (%) = 100 - current utilization
// Returns null if randomly chosen VM cannot host (caller handles rejection).
public class SampleEdgeOrchestrator extends EdgeOrchestrator {
	
	public SampleEdgeOrchestrator(String _policy, String _simScenario) {
		super(_policy, _simScenario);
	}

	@Override
	public void initialize() {
	}

	@Override
	public int getDeviceToOffload(Task task) {
		// Always offload to edge (no cloud/mobile local decision logic in this scenario)
		return SimSettings.GENERIC_EDGE_DEVICE_ID;
	}
	
	@Override
	public Vm getVmToOffload(Task task, int deviceId) {
		Vm selectedVM = null;
		
		// Determine serving WLAN -> host id (assumption: 1 host per WLAN region)
		Location deviceLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(), CloudSim.clock());
		int relatedHostId=deviceLocation.getServingWlanId();
		
		List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(relatedHostId);
		int randomIndex = SimUtils.getRandomNumber(0, vmArray.size()-1);
		
		// RANDOM selection among VMs on that host
		// Predictive utilization vs residual capacity check
		double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(randomIndex).getVmType());
		double targetVmCapacity = (double)100 - vmArray.get(randomIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
		if(requiredCapacity <= targetVmCapacity)
			selectedVM = vmArray.get(randomIndex);
		
		return selectedVM;
	}
	
	@Override
	public void processEvent(SimEvent arg0) {
		// No asynchronous events handled (stateless orchestrator)
	}

	@Override
	public void shutdownEntity() {
		// No resources to release
	}

	@Override
	public void startEntity() {
		// No startup scheduling needed
	}
}