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
 * mobile device processing unit.
 * 
 * If you want to use different topology, you should modify
 * the flow implemented in this class.
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app3;

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

/**
 * Sample mobile device manager for hybrid mobile-edge processing scenarios.
 * Handles task submission to both edge servers and mobile device processing units.
 * Manages network delays and mobility-aware task execution for local and remote processing.
 */
public class SampleMobileDeviceManager extends MobileDeviceManager {
	/** Base value for custom event tags to avoid CloudSim tag conflicts */
	private static final int BASE = 100000;
	
	/** Event tag for task requests received by edge devices */
	private static final int REQUEST_RECEIVED_BY_EDGE_DEVICE = BASE + 1;
	/** Event tag for task requests received by mobile devices */
	private static final int REQUEST_RECEIVED_BY_MOBILE_DEVICE = BASE + 2;
	/** Event tag for task responses received by mobile devices */
	private static final int RESPONSE_RECEIVED_BY_MOBILE_DEVICE = BASE + 3;

	/** Counter for generating unique task IDs */
	private int taskIdCounter=0;
	
	/**
	 * Constructor for sample mobile device manager.
	 * @throws Exception if initialization fails
	 */
	public SampleMobileDeviceManager() throws Exception{
	}

	/**
	 * Initialize mobile device manager - no specific initialization needed.
	 */
	@Override
	public void initialize() {
	}
	
	/**
	 * Returns custom CPU utilization model for mobile device processing.
	 * @return CpuUtilizationModel_Custom for accurate capacity prediction
	 */
	@Override
	public UtilizationModel getCpuUtilizationModel() {
		return new CpuUtilizationModel_Custom();
	}
	
	@Override
	public void startEntity() {
		super.startEntity();
	}
	
	/**
	 * Submit cloudlets to VMs - not used in this mobile-edge scenario.
	 * Tasks are submitted through submitTask() method instead.
	 */
	protected void submitCloudlets() {
		// Not used in this scenario - tasks submitted via submitTask()
	}
	
	/**
	 * Process task completion events from edge servers or mobile devices.
	 * Handles network delays, mobility effects, and result delivery.
	 * 
	 * @param ev SimEvent containing the completed task
	 */
	protected void processCloudletReturn(SimEvent ev) {
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		Task task = (Task) ev.getData();
		
		// Log task execution completion
		SimLogger.getInstance().taskExecuted(task.getCloudletId());

		if(task.getAssociatedDatacenterId() == SimSettings.GENERIC_EDGE_DEVICE_ID){
			// Handle task completed on edge server - need to download result
			double delay = networkModel.getDownloadDelay(task.getAssociatedDatacenterId(), task.getMobileDeviceId(), task);
			
			if(delay > 0)
			{
				// Check if mobile device is still in same WLAN coverage area
				Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock()+delay);
				if(task.getSubmittedLocation().getServingWlanId() == currentLocation.getServingWlanId())
				{
					// Start result download from edge server to mobile device
					networkModel.downloadStarted(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);
					SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), delay, NETWORK_DELAY_TYPES.WLAN_DELAY);
					
					schedule(getId(), delay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);
				}
				else
				{
					// Task failed due to mobility - device moved out of coverage
					SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
				}
			}
			else
			{
				// Task failed due to insufficient network bandwidth
				SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), NETWORK_DELAY_TYPES.WLAN_DELAY);
			}
		}
		else if(task.getAssociatedDatacenterId() == SimSettings.MOBILE_DATACENTER_ID) {
			// Task completed on mobile device - no network delay for local processing
			SimLogger.getInstance().taskEnded(task.getCloudletId(), CloudSim.clock());
			
			/*
			 * Note: Device-to-device (D2D) communication is not implemented in this scenario.
			 * For D2D scenarios, you would need to:
			 * 1. Calculate D2D network delay
			 * 2. Handle mobility effects on D2D links  
			 * 3. Consider D2D bandwidth limitations
			 * 4. Schedule RESPONSE_RECEIVED_BY_MOBILE_DEVICE event with calculated delay
			 */
		}
		else {
			SimLogger.printLine("Unknown datacenter id! Terminating simulation...");
			System.exit(0);
		}
	}
	
	/**
	 * Process custom simulation events for mobile-edge task management.
	 * Handles task requests and responses for both edge and mobile processing.
	 * 
	 * @param ev SimEvent to process
	 */
	protected void processOtherEvent(SimEvent ev) {
		if (ev == null) {
			SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - an event is null! Terminating simulation...");
			System.exit(0);
			return;
		}
		
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		
		switch (ev.getTag()) {
			case REQUEST_RECEIVED_BY_MOBILE_DEVICE:
			{
				// Task request received by mobile device for local processing
				Task task = (Task) ev.getData();			
				submitTaskToVm(task, SimSettings.VM_TYPES.MOBILE_VM);
				break;
			}
			case REQUEST_RECEIVED_BY_EDGE_DEVICE:
			{
				// Task request received by edge device after upload completion
				Task task = (Task) ev.getData();
				networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);
				submitTaskToVm(task, SimSettings.VM_TYPES.EDGE_VM);
				break;
			}
			case RESPONSE_RECEIVED_BY_MOBILE_DEVICE:
			{
				// Task result received by mobile device after download completion
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

	/**
	 * Submit task for execution on mobile device or edge server based on orchestrator decision.
	 * Handles network delays for edge offloading and local processing for mobile execution.
	 * 
	 * @param edgeTask Task properties including size, type, and requirements
	 */
	public void submitTask(TaskProperty edgeTask) {
		double delay = 0;
		int nextEvent = 0;
		int nextDeviceForNetworkModel = 0;
		VM_TYPES vmType = null;
		NETWORK_DELAY_TYPES delayType = null;
		
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		
		// Create task instance with unique ID and specifications
		Task task = createTask(edgeTask);
		
		// Get current location of mobile device for network delay calculations
		Location currentLocation = SimManager.getInstance().getMobilityModel().
				getLocation(task.getMobileDeviceId(), CloudSim.clock());
		
		// Record task submission location for mobility tracking
		task.setSubmittedLocation(currentLocation);

		// Log task properties for simulation tracking and analysis
		SimLogger.getInstance().addLog(task.getMobileDeviceId(),
				task.getCloudletId(),
				task.getTaskType(),
				(int)task.getCloudletLength(),
				(int)task.getCloudletFileSize(),
				(int)task.getCloudletOutputSize());

		// Get orchestrator decision on where to execute the task
		int nextHopId = SimManager.getInstance().getEdgeOrchestrator().getDeviceToOffload(task);
		
		if(nextHopId == SimSettings.GENERIC_EDGE_DEVICE_ID){
			// Task offloaded to edge server - calculate upload delay
			delay = networkModel.getUploadDelay(task.getMobileDeviceId(), nextHopId, task);
			vmType = SimSettings.VM_TYPES.EDGE_VM;
			nextEvent = REQUEST_RECEIVED_BY_EDGE_DEVICE;
			delayType = NETWORK_DELAY_TYPES.WLAN_DELAY;
			nextDeviceForNetworkModel = SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		else if(nextHopId == SimSettings.MOBILE_DATACENTER_ID){
			// Task executed locally on mobile device - no network delay
			vmType = VM_TYPES.MOBILE_VM;
			nextEvent = REQUEST_RECEIVED_BY_MOBILE_DEVICE;
			
			/*
			 * Note: Device-to-device (D2D) communication is not implemented.
			 * For D2D support, you would need to:
			 * 1. Calculate D2D network delay here
			 * 2. Add D2D_DELAY to NETWORK_DELAY_TYPES enum in SimSettings
			 * 3. Modify SimLogger to track D2D networking statistics
			 */
		}
		else {
			SimLogger.printLine("Unknown nextHopId! Terminating simulation...");
			System.exit(0);
		}
		
		// Proceed with task execution if network bandwidth is available or local processing
		if(delay>0 || nextHopId == SimSettings.MOBILE_DATACENTER_ID){
			
			// Get VM assignment from orchestrator based on load balancing
			Vm selectedVM = SimManager.getInstance().getEdgeOrchestrator().getVmToOffload(task, nextHopId);
			
			if(selectedVM != null){
				// Configure task associations for tracking and execution
				task.setAssociatedDatacenterId(nextHopId);
				task.setAssociatedHostId(selectedVM.getHost().getId());
				task.setAssociatedVmId(selectedVM.getId());
				
				// Bind task to selected VM for CloudSim execution
				getCloudletList().add(task);
				bindCloudletToVm(task.getCloudletId(), selectedVM.getId());

				SimLogger.getInstance().taskStarted(task.getCloudletId(), CloudSim.clock());
				
				// Start network upload for edge processing (skip for local mobile processing)
				if(nextHopId != SimSettings.MOBILE_DATACENTER_ID) {
					networkModel.uploadStarted(task.getSubmittedLocation(), nextDeviceForNetworkModel);
					SimLogger.getInstance().setUploadDelay(task.getCloudletId(), delay, delayType);
				}

				// Schedule task execution event with calculated delay
				schedule(getId(), delay, nextEvent, task);
			}
			else{
				// Task rejected due to insufficient VM capacity
				SimLogger.getInstance().rejectedDueToVMCapacity(task.getCloudletId(), CloudSim.clock(), vmType.ordinal());
			}
		}
		else
		{
			// Task rejected due to insufficient network bandwidth for edge offloading
			SimLogger.getInstance().rejectedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), vmType.ordinal(), delayType);
		}
	}
	
	/**
	 * Submit task to assigned VM for execution.
	 * 
	 * @param task Task to be executed
	 * @param vmType Type of VM (MOBILE_VM or EDGE_VM)
	 */
	private void submitTaskToVm(Task task, SimSettings.VM_TYPES vmType) {
		// Send task to datacenter for execution on assigned VM
		schedule(getVmsToDatacentersMap().get(task.getVmId()), 0, CloudSimTags.CLOUDLET_SUBMIT, task);

		// Log task assignment for performance analysis
		SimLogger.getInstance().taskAssigned(task.getCloudletId(),
				task.getAssociatedDatacenterId(),
				task.getAssociatedHostId(),
				task.getAssociatedVmId(),
				vmType.ordinal());
	}
	
	/**
	 * Create task instance from task properties with custom CPU utilization model.
	 * 
	 * @param edgeTask Task properties including size and requirements
	 * @return Configured task ready for execution
	 */
	private Task createTask(TaskProperty edgeTask){
		// Use full utilization for memory and bandwidth, custom model for CPU
		UtilizationModel utilizationModel = new UtilizationModelFull();
		UtilizationModel utilizationModelCPU = getCpuUtilizationModel();

		// Create task with unique ID and resource requirements
		Task task = new Task(edgeTask.getMobileDeviceId(), ++taskIdCounter,
				edgeTask.getLength(), edgeTask.getPesNumber(),
				edgeTask.getInputFileSize(), edgeTask.getOutputFileSize(),
				utilizationModelCPU, utilizationModel, utilizationModel);
		
		// Set task ownership and type for tracking
		task.setUserId(this.getId());
		task.setTaskType(edgeTask.getTaskType());
		
		// Configure custom CPU model with task reference for capacity prediction
		if (utilizationModelCPU instanceof CpuUtilizationModel_Custom) {
			((CpuUtilizationModel_Custom)utilizationModelCPU).setTask(task);
		}
		
		return task;
	}
}
