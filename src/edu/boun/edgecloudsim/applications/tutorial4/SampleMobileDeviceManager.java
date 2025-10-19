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

package edu.boun.edgecloudsim.applications.tutorial4;

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
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.utils.TaskProperty;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;

public class SampleMobileDeviceManager extends MobileDeviceManager {
private static final int BASE = 100000; //start from base in order not to conflict cloudsim tag!
	
	private static final int SET_DELAY_LOG = BASE + 1; // Periodic AP delay sampling event
	private static final int REQUEST_RECEIVED_BY_EDGE_DEVICE = BASE + 2; // After uplink completes
	private static final int RESPONSE_RECEIVED_BY_MOBILE_DEVICE = BASE + 3; // After downlink completes

	private int taskIdCounter=0;
	
	public SampleMobileDeviceManager() throws Exception{
	}

	@Override
	public void initialize() {
	}
	
	@Override
	public UtilizationModel getCpuUtilizationModel() {
		return new SampleCpuUtilizationModel();
	}
	
	@Override
	public void startEntity() {
		super.startEntity();
		// Kick off periodic AP delay logging (passive probe model).
		if(SimSettings.getInstance().getApDelayLogInterval() != 0)
			schedule(getId(), SimSettings.getInstance().getApDelayLogInterval(), SET_DELAY_LOG);
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
		// Called once execution completes at edge VM; handles only WLAN downlink in this scenario.
		// Mobility re-check ensures stale results are discarded if user roamed to different AP.
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		Task task = (Task) ev.getData();
		
		SimLogger.getInstance().taskExecuted(task.getCloudletId());

		if(task.getAssociatedDatacenterId() == SimSettings.GENERIC_EDGE_DEVICE_ID){
			double delay = networkModel.getDownloadDelay(task.getAssociatedDatacenterId(), task.getMobileDeviceId(), task);
			
			if(delay > 0)
			{
				Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock()+delay);
				if(task.getSubmittedLocation().getServingWlanId() == currentLocation.getServingWlanId())
				{
					// Device still anchored to same WLAN: proceed with downlink
					networkModel.downloadStarted(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);
					SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), delay, NETWORK_DELAY_TYPES.WLAN_DELAY);
					
					schedule(getId(), delay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);
				}
				else
				{
					// Mobility-induced failure: no handover continuation logic implemented.
					SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
				}
			}
			else
			{
				SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), NETWORK_DELAY_TYPES.WLAN_DELAY);
			}
		}
		else {
			// Defensive: only single generic edge DC supported in this simplified scenario.
			SimLogger.printLine("Unknown datacenter id! Terminating simulation...");
			System.exit(0);
		}
	}
	
	protected void processOtherEvent(SimEvent ev) {
		if (ev == null) {
			SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - an event is null! Terminating simulation...");
			System.exit(0);
			return;
		}
		
		SampleNetworkModel networkModel = (SampleNetworkModel)SimManager.getInstance().getNetworkModel();
		
		switch (ev.getTag()) {
			case SET_DELAY_LOG:
			{
				// Samples representative AP RTT components via synthetic 1MB probe.
				// TODO: Replace static indices with iteration over all APs if full visibility needed.
				int dataSize = 1024; //estimate network delay for 1MB
				int[] indices = {0,3,5};
				double apUploadDelays[] = new double[indices.length];
				double apDownloadDelays[] = new double[indices.length];
				for(int i=0; i<indices.length; i++){
					apUploadDelays[i] = networkModel.estimateWlanUploadDelay(indices[i],dataSize);
					apDownloadDelays[i] = networkModel.estimateWlanDownloadDelay(indices[i],dataSize);
				}
				SimLogger.getInstance().addApDelayLog(CloudSim.clock(), apUploadDelays, apDownloadDelays);
	
				schedule(getId(), SimSettings.getInstance().getVmLoadLogInterval(), SET_DELAY_LOG);
				break;
			}
			case REQUEST_RECEIVED_BY_EDGE_DEVICE:
			{
				// Uplink finished; submit to VM scheduler immediately (no queueing delay modeled here).
				Task task = (Task) ev.getData();
				networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);
				submitTaskToVm(task, SimSettings.VM_TYPES.EDGE_VM);
				break;
			}
			case RESPONSE_RECEIVED_BY_MOBILE_DEVICE:
			{
				// Final step: task result delivered; logging end time.
				Task task = (Task) ev.getData();
				
				networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);
				
				SimLogger.getInstance().taskEnded(task.getCloudletId(), CloudSim.clock());
				break;
			}
			default:
				SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - event unknown by this DatacenterBroker. Terminating simulation...");
				System.exit(0);
				break;
		}
	}

	public void submitTask(TaskProperty edgeTask) {
		// End-to-end submission pipeline:
		// 1) Create task
		// 2) Ask orchestrator for destination device
		// 3) Compute uplink delay
		// 4) Reserve VM (bind) or reject due to capacity/bandwidth
		// 5) Schedule arrival at edge
		// Orchestrator call & overhead timing isolated here; suitable for profiling policy complexity.
		// If future cloud support is added, extend switch on nextHopId.
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
		// NOTE: Overhead captured at nanosecond granularity; aggregate externally if needed.

		SimLogger.getInstance().setOrchestratorOverhead(task.getCloudletId(), estimatedTime);
		
		if(nextHopId == SimSettings.GENERIC_EDGE_DEVICE_ID){
			// Currently only local generic edge path supported.
			delay = networkModel.getUploadDelay(task.getMobileDeviceId(), nextHopId, task);
			vmType = SimSettings.VM_TYPES.EDGE_VM;
			nextEvent = REQUEST_RECEIVED_BY_EDGE_DEVICE;
			delayType = NETWORK_DELAY_TYPES.WLAN_DELAY;
			nextDeviceForNetworkModel = SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		else {
			// TODO: Extend for WAN / cloud / multi-tier orchestration.
			SimLogger.printLine("Unknown nextHopId! Terminating simulation...");
			System.exit(0);
		}
		
		if(delay>0){
			// Positive uplink delay -> attempt capacity placement
			
			Vm selectedVM = SimManager.getInstance().getEdgeOrchestrator().getVmToOffload(task, nextHopId);
			
			if(selectedVM != null){
				// Binding occurs before network transfer completes (optimistic reservation).
				// Consider deferred binding if contention modeling is required.

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
				networkModel.uploadStarted(task.getSubmittedLocation(), nextDeviceForNetworkModel);
				SimLogger.getInstance().setUploadDelay(task.getCloudletId(), delay, delayType);

				schedule(getId(), delay, nextEvent, task);
			}
			else{
				// All candidate VMs saturated -> capacity rejection.
				SimLogger.getInstance().rejectedDueToVMCapacity(task.getCloudletId(), CloudSim.clock(), vmType.ordinal());
			}
		}
		else {
			// Bandwidth zero or negative (no residual share) -> immediate rejection.
			SimLogger.getInstance().rejectedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), vmType.ordinal(), delayType);
		}
	}
	
	private void submitTaskToVm(Task task, SimSettings.VM_TYPES vmType) {
		//SimLogger.printLine(CloudSim.clock() + ": Cloudlet#" + task.getCloudletId() + " is submitted to VM#" + task.getVmId());
		schedule(getVmsToDatacentersMap().get(task.getVmId()), 0, CloudSimTags.CLOUDLET_SUBMIT, task);

		SimLogger.getInstance().taskAssigned(task.getCloudletId(),
				task.getAssociatedDatacenterId(),
				task.getAssociatedHostId(),
				task.getAssociatedVmId(),
				vmType.ordinal());
	}
	
	private Task createTask(TaskProperty edgeTask){
		// Constructs Task and associates a CPU utilization model for dynamic consumption profile.
		// NOTE: RAM and BW models kept full; adapt if finer resource contention required.
		UtilizationModel utilizationModel = new UtilizationModelFull(); /*UtilizationModelStochastic*/
		UtilizationModel utilizationModelCPU = getCpuUtilizationModel();

		Task task = new Task(edgeTask.getMobileDeviceId(), ++taskIdCounter,
				edgeTask.getLength(), edgeTask.getPesNumber(),
				edgeTask.getInputFileSize(), edgeTask.getOutputFileSize(),
				utilizationModelCPU, utilizationModel, utilizationModel);
		
		//set the owner of this task
		task.setUserId(this.getId());
		task.setTaskType(edgeTask.getTaskType());
		
		if (utilizationModelCPU instanceof SampleCpuUtilizationModel) {
			// Provide back-reference so utilization model can inspect task parameters.
			((SampleCpuUtilizationModel)utilizationModelCPU).setTask(task);
		}
		
		return task;
	}
}
