/*
 * Title:        EdgeCloudSim - Mobile Device Manager
 * 
 * Description: 
 * Mobile Device Manager is one of the most important component
 * in EdgeCloudSim. It is responsible for creating the tasks,
 * submitting them to the related VM with respect to the
 * Edge Orchestrator decision, and takes proper actions when
 * the execution of the tasks are finished. It also feeds the
 * SimLogger with the relevant results.

 * SampleMobileDeviceManager sends tasks to the edge servers or
 * cloud servers. The mobile devices use WAN if the tasks are
 * offloaded to the edge servers. On the other hand, they use WLAN
 * if the target server is an edge server. Finally, the mobile
 * devices use MAN if they must be served by a remote edge server
 * due to the congestion at their own location. In this case,
 * they access the edge server via two hops where the packets
 * must go through WLAN and MAN.
 * 
 * If you want to use different topology, you should modify
 * the flow implemented in this class.
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.tutorial2;

import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.core.SimSettings.NETWORK_DELAY_TYPES;
import edu.boun.edgecloudsim.core.SimSettings.VM_TYPES;
import edu.boun.edgecloudsim.edge_client.CpuUtilizationModel_Custom;
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.utils.TaskProperty;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;

public class SampleMobileDeviceManager extends MobileDeviceManager {
	// Execution pipeline summary:
	// 1) submitTask(): build Task -> pick device (mobile or edge) -> compute uplink delay (if edge)
	// 2) REQUEST_* event: deliver task to chosen execution environment (mobile VM or edge VM)
	// 3) CloudSim executes task (completion triggers processCloudletReturn)
	// 4) processCloudletReturn: if edge result -> schedule RESPONSE_* after download delay (mobility/bandwidth checks)
	// 5) If local (mobile) execution -> finish immediately (D2D return skipped by design; TODO hook provided)
	// Fail modes logged: VM capacity, bandwidth saturation (delay<=0), mobility change (WLAN mismatch).
	// Times logged: orchestrator overhead (ns), upload/download delays, start/execution/end timestamps.

	// Custom event tags (offset avoids CloudSim tag collision)
	private static final int BASE = 100000; //start from base in order not to conflict cloudsim tag!
	private static final int REQUEST_RECEIVED_BY_EDGE_DEVICE = BASE + 1; // edge got task upload
	private static final int REQUEST_RECEIVED_BY_MOBILE_DEVICE = BASE + 2; // local mobile VM execution start
	private static final int RESPONSE_RECEIVED_BY_MOBILE_DEVICE = BASE + 3; // final result delivered

	private int taskIdCounter=0;
	
	public SampleMobileDeviceManager() throws Exception{
	}

	@Override
	public void initialize() {
	}
	
	@Override
	public UtilizationModel getCpuUtilizationModel() {
		// Predictive per-task CPU utilization model used by orchestrator for capacity checks
		return new CpuUtilizationModel_Custom();
	}
	
	@Override
	public void startEntity() {
		super.startEntity();
	}
	
	/**
	 * Submit cloudlets to the created VMs.
	 * 
	 * @pre $none
	 * @post $none
	 */
	protected void submitCloudlets() {
		//do nothing!
	}
	
	/**
	 * Process a cloudlet return event.
	 * 
	 * @param ev a SimEvent object
	 * @pre ev != $null
	 * @post $none
	 */
	protected void processCloudletReturn(SimEvent ev) {
		// Triggered when execution finishes on assigned VM (edge or mobile)
		// Edge branch: emulate WLAN download path with mobility validation
		// Local branch: complete immediately (no D2D transfer modeled)
		
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		Task task = (Task) ev.getData();
		
		SimLogger.getInstance().taskExecuted(task.getCloudletId());

		if(task.getAssociatedDatacenterId() == SimSettings.GENERIC_EDGE_DEVICE_ID){
			// Compute downlink delay; if >0 verify device stays in original WLAN after that period
			// Mobility failure -> logged (task result lost)
			// Bandwidth failure -> delay<=0 logged
			double delay = networkModel.getDownloadDelay(task.getAssociatedDatacenterId(), task.getMobileDeviceId(), task);
			
			if(delay > 0)
			{
				Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock()+delay);
				if(task.getSubmittedLocation().getServingWlanId() == currentLocation.getServingWlanId())
				{
					networkModel.downloadStarted(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);
					SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), delay, NETWORK_DELAY_TYPES.WLAN_DELAY);
					
					schedule(getId(), delay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);
				}
				else
				{
					SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
				}
			}
			else
			{
				SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), NETWORK_DELAY_TYPES.WLAN_DELAY);
			}
		}
		else if(task.getAssociatedDatacenterId() == SimSettings.MOBILE_DATACENTER_ID) {
			// Local execution completes; D2D return intentionally skipped.
			// TODO block documents how to extend with D2D including new NETWORK_DELAY_TYPES entry.
			SimLogger.getInstance().taskEnded(task.getCloudletId(), CloudSim.clock());
			
			/*
			 * TODO: In this scenario device to device (D2D) communication is ignored.
			 * If you want to consider D2D communication, you should transmit the result
			 * of the task to the sender mobile device. Hence, you should calculate
			 * D2D_DELAY here and send the following event:
			 * 
			 * schedule(getId(), delay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);
			 * 
			 * Please not that you should deal with the mobility and D2D delay calculation.
			 * The task can be failed due to the network bandwidth or the nobility.
			 */
		}
		else {
			// Defensive: unknown datacenter id indicates configuration error
			SimLogger.printLine("Unknown datacenter id! Terminating simulation...");
			System.exit(0);
		}
	}
	
	protected void processOtherEvent(SimEvent ev) {
		// Dispatch finite-state transitions of offloading flow
		// REQUEST_*: task arrival to execution environment
		// RESPONSE_*: result delivery to mobile
		
		if (ev == null) {
			SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - an event is null! Terminating simulation...");
			System.exit(0);
			return;
		}
		
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		
		switch (ev.getTag()) {
			case REQUEST_RECEIVED_BY_MOBILE_DEVICE:
				// Local execution start (no network delay)
				{
				Task task = (Task) ev.getData();			
				submitTaskToVm(task, SimSettings.VM_TYPES.MOBILE_VM);
				break;
				}
			case REQUEST_RECEIVED_BY_EDGE_DEVICE:
				// Edge upload completed -> submit to edge VM
				{
				Task task = (Task) ev.getData();
				networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);
				submitTaskToVm(task, SimSettings.VM_TYPES.EDGE_VM);
				break;
				}
			case RESPONSE_RECEIVED_BY_MOBILE_DEVICE:
				// Final result reception after WLAN download
				{
				Task task = (Task) ev.getData();
				
				networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);
				
				SimLogger.getInstance().taskEnded(task.getCloudletId(), CloudSim.clock());
				break;
				}
			default:
				// Unknown tag -> terminate to preserve data integrity
				SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - event unknown by this DatacenterBroker. Terminating simulation...");
				System.exit(0);
				break;
		}
	}

	public void submitTask(TaskProperty edgeTask) {
		// Task submission flow:
		// 1) Instantiate Task + set submission location
		// 2) Log metadata
		// 3) Orchestrator decides target (mobile vs edge) and overhead time measured
		// 4) If edge: compute WLAN uplink delay (bandwidth gating)
		// 5) VM selection (null => capacity rejection)
		// 6) Schedule first event (REQUEST_*) after delay (0 for local)
		// 7) Record upload delay & start time
		// Failure paths:
		//  - delay<=0 (edge path) -> rejectedDueToBandwidth
		//  - selectedVM==null -> rejectedDueToVMCapacity
		
		double delay = 0;
		int nextEvent = 0;
		int nextDeviceForNetworkModel = 0;
		VM_TYPES vmType = null;
		NETWORK_DELAY_TYPES delayType = null;
		
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		
		//create a task
		Task task = createTask(edgeTask);
		
		Location currentLocation = SimManager.getInstance().getMobilityModel().
				getLocation(task.getMobileDeviceId(), CloudSim.clock());
		
		//set location of the mobile device which generates this task
		task.setSubmittedLocation(currentLocation);

		//add related task to log list
		SimLogger.getInstance().addLog(task.getMobileDeviceId(),
				task.getCloudletId(),
				task.getTaskType(),
				(int)task.getCloudletLength(),
				(int)task.getCloudletFileSize(),
				(int)task.getCloudletOutputSize());

		long startTime = System.nanoTime();   
		int nextHopId = SimManager.getInstance().getEdgeOrchestrator().getDeviceToOffload(task);
		long estimatedTime = System.nanoTime() - startTime;

		SimLogger.getInstance().setOrchestratorOverhead(task.getCloudletId(), estimatedTime);
		
		if(nextHopId == SimSettings.GENERIC_EDGE_DEVICE_ID){
			// Compute WLAN uplink delay (bandwidth gating)
			delay = networkModel.getUploadDelay(task.getMobileDeviceId(), nextHopId, task);
			vmType = SimSettings.VM_TYPES.EDGE_VM;
			nextEvent = REQUEST_RECEIVED_BY_EDGE_DEVICE;
			delayType = NETWORK_DELAY_TYPES.WLAN_DELAY;
			nextDeviceForNetworkModel = SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		else if(nextHopId == SimSettings.MOBILE_DATACENTER_ID){
			// Local execution path
			vmType = VM_TYPES.MOBILE_VM;
			nextEvent = REQUEST_RECEIVED_BY_MOBILE_DEVICE;
			
			/*
			 * TODO: In this scenario device to device (D2D) communication is ignored.
			 * If you want to consider D2D communication, you should calculate D2D
			 * network delay here.
			 * 
			 * You should also add D2D_DELAY to the following enum in SimSettings
			 * public static enum NETWORK_DELAY_TYPES { WLAN_DELAY, MAN_DELAY, WAN_DELAY }
			 * 
			 * If you want to get statistics of the D2D networking, you should modify
			 * SimLogger in a way to consider D2D_DELAY statistics.
			 */
		}
		else {
			// Defensive: unknown nextHopId indicates configuration error
			SimLogger.printLine("Unknown nextHopId! Terminating simulation...");
			System.exit(0);
		}
		
		if(delay>0 || nextHopId == SimSettings.MOBILE_DATACENTER_ID){
			
			Vm selectedVM = SimManager.getInstance().getEdgeOrchestrator().getVmToOffload(task, nextHopId);
			
			if(selectedVM != null){
				//set related host id
				task.setAssociatedDatacenterId(nextHopId);

				//set related host id
				task.setAssociatedHostId(selectedVM.getHost().getId());
				
				//set related vm id
				task.setAssociatedVmId(selectedVM.getId());
				
				//bind task to related VM
				getCloudletList().add(task);
				bindCloudletToVm(task.getCloudletId(), selectedVM.getId());

				SimLogger.getInstance().taskStarted(task.getCloudletId(), CloudSim.clock());
				
				if(nextHopId != SimSettings.MOBILE_DATACENTER_ID) {
					// For edge execution: log upload delay & start network upload
					networkModel.uploadStarted(task.getSubmittedLocation(), nextDeviceForNetworkModel);
					SimLogger.getInstance().setUploadDelay(task.getCloudletId(), delay, delayType);
				}

				// Schedule first event to trigger REQUEST_RECEIVED_BY_* on next hop
				schedule(getId(), delay, nextEvent, task);
			}
			else{
				//SimLogger.printLine("Task #" + task.getCloudletId() + " cannot assign to any VM");
				SimLogger.getInstance().rejectedDueToVMCapacity(task.getCloudletId(), CloudSim.clock(), vmType.ordinal());
			}
		}
		else
		{
			//SimLogger.printLine("Task #" + task.getCloudletId() + " cannot assign to any VM");
			SimLogger.getInstance().rejectedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), vmType.ordinal(), delayType);
		}
	}
	
	private void submitTaskToVm(Task task, SimSettings.VM_TYPES vmType) {
		// Zero-delay submission to VM (CloudSim processes scheduling)
		// Logging captures (dc, host, vm) tuple for analysis
		//SimLogger.printLine(CloudSim.clock() + ": Cloudlet#" + task.getCloudletId() + " is submitted to VM#" + task.getVmId());
		schedule(getVmsToDatacentersMap().get(task.getVmId()), 0, CloudSimTags.CLOUDLET_SUBMIT, task);

		SimLogger.getInstance().taskAssigned(task.getCloudletId(),
				task.getAssociatedDatacenterId(),
				task.getAssociatedHostId(),
				task.getAssociatedVmId(),
				vmType.ordinal());
	}
	
	private Task createTask(TaskProperty edgeTask){
		// Wraps edgeTask metadata into a CloudSim Task (Cloudlet)
		// CPU utilization model linked bidirectionally for dynamic prediction
		// RAM/BW models set to UtilizationModelFull (100%) for simplicity

		UtilizationModel utilizationModel = new UtilizationModelFull(); /*UtilizationModelStochastic*/
		UtilizationModel utilizationModelCPU = getCpuUtilizationModel();

		Task task = new Task(edgeTask.getMobileDeviceId(), ++taskIdCounter,
				edgeTask.getLength(), edgeTask.getPesNumber(),
				edgeTask.getInputFileSize(), edgeTask.getOutputFileSize(),
				utilizationModelCPU, utilizationModel, utilizationModel);
		
		//set the owner of this task
		task.setUserId(this.getId());
		task.setTaskType(edgeTask.getTaskType());
		
		if (utilizationModelCPU instanceof CpuUtilizationModel_Custom) {
			// Provide task context to utilization predictor
			((CpuUtilizationModel_Custom)utilizationModelCPU).setTask(task);
		}
		
		return task;
	}
}
