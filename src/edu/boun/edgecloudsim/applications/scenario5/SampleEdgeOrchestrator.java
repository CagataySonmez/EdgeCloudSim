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

package edu.boun.edgecloudsim.applications.scenario5;

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
import edu.boun.edgecloudsim.utils.SimUtils;

public class SampleEdgeOrchestrator extends EdgeOrchestrator {
	private int numberOfHost; //used by load balancer
	
	public SampleEdgeOrchestrator(String _policy, String _simScenario) {
		super(_policy, _simScenario);
	}

	@Override
	public void initialize() {
		numberOfHost=SimSettings.getInstance().getNumOfEdgeHosts();
	}

	@Override
	public int getDeviceToOffload(Task task) {
		return SimSettings.GENERIC_EDGE_DEVICE_ID;
	}
	
	@Override
	public Vm getVmToOffload(Task task, int deviceId) {
		Vm selectedVM = null;
		
		int randomHostIndex = SimUtils.getRandomNumber(0, numberOfHost-1);
		List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(randomHostIndex);
		int randomIndex = SimUtils.getRandomNumber(0, vmArray.size()-1);
		
		double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(randomIndex).getVmType());
		double targetVmCapacity = (double)100 - vmArray.get(randomIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
		if(requiredCapacity <= targetVmCapacity)
			selectedVM = vmArray.get(randomIndex);
		
		return selectedVM;
	}
	

	@Override
	public void processEvent(SimEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void shutdownEntity() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startEntity() {
		// TODO Auto-generated method stub
		
	}
}