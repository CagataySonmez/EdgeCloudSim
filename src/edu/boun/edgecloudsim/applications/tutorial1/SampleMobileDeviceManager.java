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

package edu.boun.edgecloudsim.applications.tutorial1;

import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.core.SimSettings.NETWORK_DELAY_TYPES;
import edu.boun.edgecloudsim.edge_client.CpuUtilizationModel_Custom;
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.edge_server.EdgeHost;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.utils.TaskProperty;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;

// Execution pipeline overview:
// 1) submitTask(): create Task -> decide offloading target -> choose VM -> initiate upload
// 2) REQUEST_* events: emulate network hops (WLAN / MAN) until task reaches serving edge
// 3) Task executes on VM (CloudSim handles execution)
// 4) processCloudletReturn(): generate response path (direct or via relay)
// 5) RESPONSE_* events: emulate download and finalize logging
// Fail cases tracked: insufficient bandwidth, mobility-induced disconnection, VM capacity rejection.
// Time measurements: orchestrator overhead (ns), upload/download delays, execution start/end.

public class SampleMobileDeviceManager extends MobileDeviceManager {
	// Base value chosen to avoid collision with CloudSim's internal tag space.
	private static final int BASE = 100000; //start from base in order not to conflict cloudsim tag!
	
	// Event tags for custom network traversal state machine:
	private static final int REQUEST_RECEIVED_BY_EDGE_DEVICE = BASE + 1; // local edge got upload
	private static final int REQUEST_RECEIVED_BY_REMOTE_EDGE_DEVICE = BASE + 2; // neighbor/remote edge got relay upload
	private static final int REQUEST_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_NEIGHBOR = BASE + 3; // local edge will forward to neighbor (MAN hop)
	private static final int RESPONSE_RECEIVED_BY_MOBILE_DEVICE = BASE + 4; // final response arrived to device
	private static final int RESPONSE_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_MOBILE_DEVICE = BASE + 5; // remote edge sends response to original edge for WLAN relay
	
	private int taskIdCounter=0; // monotonically increasing local task id (per device manager instance)
	
	public SampleMobileDeviceManager() throws Exception{
	}

	@Override
	public void initialize() {
		// No pre-allocation needed; networking & mobility fetched on demand.
		// Could cache model references here for micro optimizations.
	}
	
	@Override
	public UtilizationModel getCpuUtilizationModel() {
		// Custom model provides per-task predicted CPU percentage for placement decisions.
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
		// Called when task execution completes at the edge VM.
		// Decide whether response needs relay (if served by foreign WLAN zone).
		// Apply mobility-aware validation before scheduling download.
		
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		Task task = (Task) ev.getData();
		
		SimLogger.getInstance().taskExecuted(task.getCloudletId());

		int nextEvent = RESPONSE_RECEIVED_BY_MOBILE_DEVICE;
		int nextDeviceForNetworkModel = SimSettings.GENERIC_EDGE_DEVICE_ID;
		NETWORK_DELAY_TYPES delayType = NETWORK_DELAY_TYPES.WLAN_DELAY;
		double delay = networkModel.getDownloadDelay(task.getAssociatedHostId(), task.getMobileDeviceId(), task);
		
		EdgeHost host = (EdgeHost)(SimManager.
				getInstance().
				getEdgeServerManager().
				getDatacenterList().get(task.getAssociatedHostId()).
				getHostList().get(0));
		
		//if neighbour edge device is selected
		if(host.getLocation().getServingWlanId() != task.getSubmittedLocation().getServingWlanId())
		{
			// if neighbor edge served the task, reroute through MAN back to original edge before WLAN delivery
			delay = networkModel.getDownloadDelay(SimSettings.GENERIC_EDGE_DEVICE_ID, SimSettings.GENERIC_EDGE_DEVICE_ID, task);
			nextEvent = RESPONSE_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_MOBILE_DEVICE;
			nextDeviceForNetworkModel = SimSettings.GENERIC_EDGE_DEVICE_ID + 1;
			delayType = NETWORK_DELAY_TYPES.MAN_DELAY;
		}
		
		if(delay > 0)
		{
			Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock()+delay);
			if(task.getSubmittedLocation().getServingWlanId() == currentLocation.getServingWlanId())
			{
				networkModel.downloadStarted(currentLocation, nextDeviceForNetworkModel);
				SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), delay, delayType);
				
				schedule(getId(), delay, nextEvent, task);
			}
			else
			{
				// Mobility check: ensure device still in original WLAN after simulated download delay
				SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
			}
		}
		else
		{
			// Failure branches:
			//  - delay <= 0 : bandwidth saturated
			//  - WLAN changed: mobility-induced failure
			SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), delayType);
		}
	}
	
	protected void processOtherEvent(SimEvent ev) {
		// Central dispatcher for custom network traversal events.
		// Each case advances the finite state machine for request/response.
		if (ev == null) {
			SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - an event is null! Terminating simulation...");
			System.exit(0);
			return;
		}
		
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		
		switch (ev.getTag()) {
			case REQUEST_RECEIVED_BY_EDGE_DEVICE:
			{
				// Local edge receives task -> finish WLAN upload -> submit to VM
				Task task = (Task) ev.getData();
				networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);
				submitTaskToVm(task, SimSettings.VM_TYPES.EDGE_VM);
				break;
			}
			case REQUEST_RECEIVED_BY_REMOTE_EDGE_DEVICE:
			{
				// Remote edge (neighbor) receives relayed task via MAN -> submit to VM
				Task task = (Task) ev.getData();
				networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID+1);
				submitTaskToVm(task, SimSettings.VM_TYPES.EDGE_VM);
				
				break;
			}
			case REQUEST_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_NEIGHBOR:
			{
				// Local edge decides to forward to neighbor (capacity / policy reason)
				// Start MAN upload; on success schedule remote edge receive event.
				Task task = (Task) ev.getData();
				networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);
				
				double manDelay =  networkModel.getUploadDelay(SimSettings.GENERIC_EDGE_DEVICE_ID, SimSettings.GENERIC_EDGE_DEVICE_ID, task);
				if(manDelay>0){
					networkModel.uploadStarted(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID+1);
					SimLogger.getInstance().setUploadDelay(task.getCloudletId(), manDelay, NETWORK_DELAY_TYPES.MAN_DELAY);
					schedule(getId(), manDelay, REQUEST_RECEIVED_BY_REMOTE_EDGE_DEVICE, task);
				}
				else
				{
					// If MAN bandwidth unavailable -> reject due to MAN bandwidth
					SimLogger.getInstance().rejectedDueToBandwidth(
							task.getCloudletId(),
							CloudSim.clock(),
							SimSettings.VM_TYPES.EDGE_VM.ordinal(),
							NETWORK_DELAY_TYPES.MAN_DELAY);
				}
				
				break;
			}
			case RESPONSE_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_MOBILE_DEVICE:
			{
				// Remote edge finished execution; sending result back to original edge via MAN then WLAN last hop.
				// Mobility check performed after MAN leg before WLAN leg scheduling.
				Task task = (Task) ev.getData();
				networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID+1);
				
				//SimLogger.printLine(CloudSim.clock() + ": " + getName() + ": task #" + task.getCloudletId() + " received from edge");
				double delay = networkModel.getDownloadDelay(task.getAssociatedHostId(), task.getMobileDeviceId(), task);
				
				if(delay > 0)
				{
					Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock()+delay);
					if(task.getSubmittedLocation().getServingWlanId() == currentLocation.getServingWlanId())
					{
						networkModel.downloadStarted(currentLocation, SimSettings.GENERIC_EDGE_DEVICE_ID);
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
				
				break;
			}
			case RESPONSE_RECEIVED_BY_MOBILE_DEVICE:
			{
				// Final delivery; mark task completion (success path).
				Task task = (Task) ev.getData();
				
				if(task.getAssociatedDatacenterId() == SimSettings.CLOUD_DATACENTER_ID)
					networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.CLOUD_DATACENTER_ID);
				else
					networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);
				
				SimLogger.getInstance().taskEnded(task.getCloudletId(), CloudSim.clock());
				break;
			}
			default:
				// Defensive: unexpected tag indicates logic/config error.
				SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - event unknown by this DatacenterBroker. Terminating simulation...");
				System.exit(0);
				break;
		}
	}

	public void submitTask(TaskProperty edgeTask) {
		// Entry point for generating and offloading a new task instance.
		// Steps:
		// 1) Instantiate Task + assign submission location
		// 2) Log metadata (for post-simulation analysis)
		// 3) Query orchestrator: device target
		// 4) Measure orchestrator decision latency (nanoseconds)
		// 5) Compute upload delay; if >0 attempt VM selection
		// 6) If VM capacity available: bind, schedule network upload event chain
		// 7) Else: log rejection (capacity or bandwidth)
		
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
		
		NETWORK_DELAY_TYPES delayType = NETWORK_DELAY_TYPES.WLAN_DELAY;
		double delay = networkModel.getUploadDelay(task.getMobileDeviceId(), nextHopId, task);
		int vmType = SimSettings.VM_TYPES.EDGE_VM.ordinal();
		int nextEvent = REQUEST_RECEIVED_BY_EDGE_DEVICE;
		int nextDeviceForNetworkModel = SimSettings.GENERIC_EDGE_DEVICE_ID;
		
		if(delay>0){
			
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
				
				if(selectedVM instanceof EdgeVM){
					EdgeHost host = (EdgeHost)(selectedVM.getHost());
					
					//if neighbour edge device is selected
					if(host.getLocation().getServingWlanId() != task.getSubmittedLocation().getServingWlanId()){
						// For neighbor edge, we alter the first network event to relay path
						nextEvent = REQUEST_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_NEIGHBOR;
					}
				}
				networkModel.uploadStarted(currentLocation, nextDeviceForNetworkModel);
				
				SimLogger.getInstance().taskStarted(task.getCloudletId(), CloudSim.clock());
				SimLogger.getInstance().setUploadDelay(task.getCloudletId(), delay, delayType);

				schedule(getId(), delay, nextEvent, task);
			}
			else{
				// Failures:
				//  - selectedVM == null -> no VM capacity
				//  - delay <= 0 -> insufficient uplink bandwidth
				SimLogger.getInstance().rejectedDueToVMCapacity(task.getCloudletId(), CloudSim.clock(), vmType);
			}
		}
		else
		{
			// Failures:
			//  - selectedVM == null -> no VM capacity
			//  - delay <= 0 -> insufficient uplink bandwidth
			SimLogger.getInstance().rejectedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), vmType, delayType);
		}
	}
	
	private void submitTaskToVm(Task task, SimSettings.VM_TYPES vmType) {
		// Immediate (zero-delay) submission to the target VM's datacenter broker mapping.
		// Logging captures placement tuple for traceability.
		//SimLogger.printLine(CloudSim.clock() + ": Cloudlet#" + task.getCloudletId() + " is submitted to VM#" + task.getVmId());
		schedule(getVmsToDatacentersMap().get(task.getVmId()), 0, CloudSimTags.CLOUDLET_SUBMIT, task);

		SimLogger.getInstance().taskAssigned(task.getCloudletId(),
				task.getAssociatedDatacenterId(),
				task.getAssociatedHostId(),
				task.getAssociatedVmId(),
				vmType.ordinal());
	}
	
	private Task createTask(TaskProperty edgeTask){
		// Builds a Task (Cloudlet) with:
		//  - Custom CPU utilization model (predictive dynamic utilization)
		//  - Full (constant) utilization for RAM and BW models
		// Binds task back into utilization model for feedback-based prediction.
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
			((CpuUtilizationModel_Custom)utilizationModelCPU).setTask(task);
		}
		
		// link reverse reference for dynamic prediction
		return task;
	}
}
