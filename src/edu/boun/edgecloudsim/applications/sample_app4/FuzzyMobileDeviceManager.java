/*
 * Title:        EdgeCloudSim - Fuzzy Mobile Device Manager
 * 
 * Description: 
 * FuzzyMobileDeviceManager extends the default mobile device manager with
 * advanced fuzzy logic-based task orchestration and experimental network modeling.
 * This manager implements sophisticated task routing decisions based on:
 * - Real-time network conditions and load balancing
 * - Mobility-aware response handling with handoff management
 * - Multi-tier architecture supporting cloud, edge, and MAN communications
 * - Adaptive M/M/1 queuing model updates for realistic network behavior
 * - Experimental WLAN performance data integration for accurate delay modeling
 * 
 * The class handles complex scenarios including neighbor edge server communication
 * through MAN networks and mobility-induced task failures.
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app4;

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

/**
 * FuzzyMobileDeviceManager implements advanced mobile device management with fuzzy logic
 * and experimental network modeling capabilities. This class extends the base MobileDeviceManager
 * to provide sophisticated task orchestration, mobility-aware communication, and adaptive
 * network performance modeling.
 * 
 * Key Features:
 * - Fuzzy logic-based task routing and load balancing
 * - Experimental WLAN performance data integration
 * - Multi-tier architecture (Cloud, Edge, MAN) support
 * - Adaptive M/M/1 queuing model with real-time parameter updates
 * - Mobility-aware response handling with handoff management
 * - Neighbor edge server communication through MAN networks
 * 
 * The manager uses event-driven architecture for efficient task processing and
 * network delay modeling based on empirical measurements and analytical models.
 */
public class FuzzyMobileDeviceManager extends MobileDeviceManager {
	/** Base value for custom event tags to avoid conflicts with CloudSim tags */
	private static final int BASE = 100000;
	
	/** Event tag for periodic M/M/1 queue model parameter updates */
	private static final int UPDATE_MM1_QUEUE_MODEL = BASE + 1;
	/** Event tag for task request received by cloud datacenter */
	private static final int REQUEST_RECEIVED_BY_CLOUD = BASE + 2;
	/** Event tag for task request received by local edge device */
	private static final int REQUEST_RECEIVED_BY_EDGE_DEVICE = BASE + 3;
	/** Event tag for task request received by remote edge device via MAN */
	private static final int REQUEST_RECEIVED_BY_REMOTE_EDGE_DEVICE = BASE + 4;
	/** Event tag for task request received by edge device for neighbor relay */
	private static final int REQUEST_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_NEIGHBOR = BASE + 5;
	/** Event tag for task response received by mobile device */
	private static final int RESPONSE_RECEIVED_BY_MOBILE_DEVICE = BASE + 6;
	/** Event tag for task response received by edge device for mobile relay */
	private static final int RESPONSE_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_MOBILE_DEVICE = BASE + 7;

	/** Update interval for M/M/1 queue model parameter recalculation (seconds) */
	private static final double MM1_QUEUE_MODEL_UPDATE_INTEVAL = 5;
	
	/** Counter for generating unique task IDs */
	/** Counter for generating unique task IDs */
	private int taskIdCounter=0;
	
	/**
	 * Constructor for FuzzyMobileDeviceManager.
	 * Initializes the fuzzy logic-based mobile device manager with experimental
	 * network modeling capabilities and adaptive queue management.
	 * 
	 * @throws Exception if initialization fails
	 */
	public FuzzyMobileDeviceManager() throws Exception{
	}

	/**
	 * Initializes the fuzzy mobile device manager.
	 * Sets up necessary components for advanced task orchestration and
	 * experimental network modeling functionality.
	 */
	@Override
	public void initialize() {
	}
	
	/**
	 * Returns the CPU utilization model for task execution.
	 * Uses custom CPU utilization model that provides realistic resource
	 * consumption patterns based on task characteristics.
	 * 
	 * @return CpuUtilizationModel_Custom instance for realistic CPU modeling
	 */
	@Override
	public UtilizationModel getCpuUtilizationModel() {
		return new CpuUtilizationModel_Custom();
	}
	
	/**
	 * Starts the entity and schedules periodic M/M/1 queue model updates.
	 * This method initializes the adaptive network modeling system that
	 * continuously updates queuing parameters based on observed traffic patterns.
	 */
	@Override
	public void startEntity() {
		super.startEntity();
		schedule(getId(), SimSettings.CLIENT_ACTIVITY_START_TIME +
				MM1_QUEUE_MODEL_UPDATE_INTEVAL, UPDATE_MM1_QUEUE_MODEL);
	}
	
	/**
	 * Submit cloudlets to the created VMs.
	 * Note: This method intentionally does nothing as task submission
	 * is handled through the event-driven architecture via submitTask() method.
	 * 
	 * @pre None
	 * @post None
	 */
	protected void submitCloudlets() {
		// Task submission handled through event processing rather than direct cloudlet submission
	}
	
	/**
	 * Process a cloudlet return event after task execution completion.
	 * Handles response delivery with network delays and mobility considerations.
	 * 
	 * @param ev SimEvent containing the completed task
	 * @pre ev != null
	 * @post Task response is scheduled for delivery or marked as failed
	 */
	protected void processCloudletReturn(SimEvent ev) {
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		Task task = (Task) ev.getData();
		
		// Log task execution completion
		SimLogger.getInstance().taskExecuted(task.getCloudletId());

		// Handle response from cloud datacenter execution
		if(task.getAssociatedDatacenterId() == SimSettings.CLOUD_DATACENTER_ID){
			// Calculate WAN download delay for response delivery
			double WanDelay = networkModel.getDownloadDelay(SimSettings.CLOUD_DATACENTER_ID, task.getMobileDeviceId(), task);
			if(WanDelay > 0)
			{
				// Check if mobile device is still in the same WLAN coverage area
				Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock()+WanDelay);
				if(task.getSubmittedLocation().getServingWlanId() == currentLocation.getServingWlanId())
				{
					// Mobile device hasn't moved, schedule response delivery
					networkModel.downloadStarted(task.getSubmittedLocation(), SimSettings.CLOUD_DATACENTER_ID);
					SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), WanDelay, NETWORK_DELAY_TYPES.WAN_DELAY);
					schedule(getId(), WanDelay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);
				}
				else
				{
					// Mobile device moved to different WLAN, task fails due to mobility
					SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
				}
			}
			else
			{
				// Insufficient WAN bandwidth for response delivery
				SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), NETWORK_DELAY_TYPES.WAN_DELAY);
			}
		}
		// Handle response from edge server execution
		else{
			// Initialize default parameters for local edge server response
			int nextEvent = RESPONSE_RECEIVED_BY_MOBILE_DEVICE;
			int nextDeviceForNetworkModel = SimSettings.GENERIC_EDGE_DEVICE_ID;
			NETWORK_DELAY_TYPES delayType = NETWORK_DELAY_TYPES.WLAN_DELAY;
			double delay = networkModel.getDownloadDelay(task.getAssociatedHostId(), task.getMobileDeviceId(), task);
			
			// Get the edge host that executed the task
			EdgeHost host = (EdgeHost)(SimManager.
					getInstance().
					getEdgeServerManager().
					getDatacenterList().get(task.getAssociatedHostId()).
					getHostList().get(0));
			
			// Check if task was executed on a neighbor edge device (different WLAN)
			if(host.getLocation().getServingWlanId() != task.getSubmittedLocation().getServingWlanId())
			{
				// Response must be relayed through MAN network first
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
					SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
				}
			}
			else
			{
				SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), delayType);
			}
		}
	}
	
	/**
	 * Processes custom events specific to fuzzy mobile device management.
	 * This method handles various network events including M/M/1 queue updates,
	 * task routing between different tiers (cloud, edge, MAN), and response relay
	 * operations with mobility and bandwidth considerations.
	 * 
	 * @param ev The simulation event to process
	 * @pre ev != null
	 * @post Event is processed according to its type with appropriate network actions
	 */
	protected void processOtherEvent(SimEvent ev) {
		if (ev == null) {
			SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - an event is null! Terminating simulation...");
			System.exit(0);
			return;
		}
		
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		
		switch (ev.getTag()) {
			case UPDATE_MM1_QUEUE_MODEL:
			{
				// Update M/M/1 queue model parameters based on recent traffic patterns
				((FuzzyExperimentalNetworkModel)networkModel).updateMM1QueeuModel();
				// Schedule next update for continuous adaptation
				schedule(getId(), MM1_QUEUE_MODEL_UPDATE_INTEVAL, UPDATE_MM1_QUEUE_MODEL);
	
				break;
			}
			case REQUEST_RECEIVED_BY_CLOUD:
			{
				// Handle task arrival at cloud datacenter
				Task task = (Task) ev.getData();
				networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.CLOUD_DATACENTER_ID);
				submitTaskToVm(task, SimSettings.VM_TYPES.CLOUD_VM);
				break;
			}
			case REQUEST_RECEIVED_BY_EDGE_DEVICE:
			{
				// Handle task arrival at local edge device
				Task task = (Task) ev.getData();
				networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);
				submitTaskToVm(task, SimSettings.VM_TYPES.EDGE_VM);
				break;
			}
			case REQUEST_RECEIVED_BY_REMOTE_EDGE_DEVICE:
			{
				// Handle task arrival at remote edge device via MAN
				Task task = (Task) ev.getData();
				networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID+1);
				submitTaskToVm(task, SimSettings.VM_TYPES.EDGE_VM);
				
				break;
			}
			case REQUEST_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_NEIGHBOR:
			{
				// Handle task relay from local edge to neighbor edge via MAN
				Task task = (Task) ev.getData();
				networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);
				
				// Calculate MAN delay for neighbor edge server communication
				double manDelay =  networkModel.getUploadDelay(SimSettings.GENERIC_EDGE_DEVICE_ID, SimSettings.GENERIC_EDGE_DEVICE_ID, task);
				if(manDelay>0){
					// Sufficient MAN bandwidth available for relay
					networkModel.uploadStarted(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID+1);
					SimLogger.getInstance().setUploadDelay(task.getCloudletId(), manDelay, NETWORK_DELAY_TYPES.MAN_DELAY);
					schedule(getId(), manDelay, REQUEST_RECEIVED_BY_REMOTE_EDGE_DEVICE, task);
				}
				else
				{
					// Insufficient MAN bandwidth, reject task
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
				// Handle response relay from remote edge device to mobile device
				Task task = (Task) ev.getData();
				networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID+1);
				
				// Calculate WLAN delay for final delivery to mobile device
				double delay = networkModel.getDownloadDelay(task.getAssociatedHostId(), task.getMobileDeviceId(), task);
				
				if(delay > 0)
				{
					// Check mobility: ensure mobile device is still in same WLAN coverage
					Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock()+delay);
					if(task.getSubmittedLocation().getServingWlanId() == currentLocation.getServingWlanId())
					{
						// Mobile device hasn't moved, deliver response
						networkModel.downloadStarted(currentLocation, SimSettings.GENERIC_EDGE_DEVICE_ID);
						SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), delay, NETWORK_DELAY_TYPES.WLAN_DELAY);
						schedule(getId(), delay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);
					}
					else
					{
						// Mobile device moved to different WLAN, task fails
						SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
					}
				}
				else
				{
					// Insufficient WLAN bandwidth for response delivery
					SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), NETWORK_DELAY_TYPES.WLAN_DELAY);
				}
				
				break;
			}
			case RESPONSE_RECEIVED_BY_MOBILE_DEVICE:
			{
				// Handle final task response delivery to mobile device
				Task task = (Task) ev.getData();
				
				// Update network model state based on response source
				if(task.getAssociatedDatacenterId() == SimSettings.CLOUD_DATACENTER_ID)
					networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.CLOUD_DATACENTER_ID);
				else if(task.getAssociatedDatacenterId() != SimSettings.MOBILE_DATACENTER_ID)
					networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);
				
				// Log successful task completion with end time
				SimLogger.getInstance().taskEnded(task.getCloudletId(), CloudSim.clock());
				break;
			}
			default:
				// Handle unknown event types with error termination
				SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - event unknown by this DatacenterBroker. Terminating simulation...");
				System.exit(0);
				break;
		}
	}

	/**
	 * Submits a task for offloading using fuzzy logic-based orchestration.
	 * Determines optimal execution location and handles network delay modeling.
	 * 
	 * @param edgeTask Task properties to be submitted for execution
	 */
	public void submitTask(TaskProperty edgeTask) {
		// Initialize parameters for task submission
		int vmType=0;
		int nextEvent=0;
		int nextDeviceForNetworkModel;
		NETWORK_DELAY_TYPES delayType;
		double delay=0;
		
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		
		// Create CloudSim task from EdgeCloudSim task properties
		Task task = createTask(edgeTask);
		
		// Get current location of the mobile device for network modeling
		Location currentLocation = SimManager.getInstance().getMobilityModel().
				getLocation(task.getMobileDeviceId(), CloudSim.clock());
		
		// Record submission location for mobility tracking during execution
		task.setSubmittedLocation(currentLocation);

		// Log task submission details for performance analysis
		SimLogger.getInstance().addLog(task.getMobileDeviceId(),
				task.getCloudletId(),
				task.getTaskType(),
				(int)task.getCloudletLength(),
				(int)task.getCloudletFileSize(),
				(int)task.getCloudletOutputSize());

		// Use fuzzy orchestrator to determine optimal execution location
		int nextHopId = SimManager.getInstance().getEdgeOrchestrator().getDeviceToOffload(task);
		
		// Configure network parameters based on fuzzy orchestration decision
		if(nextHopId == SimSettings.CLOUD_DATACENTER_ID){
			// Task offloaded to cloud: configure WAN network parameters
			delay = networkModel.getUploadDelay(task.getMobileDeviceId(), SimSettings.CLOUD_DATACENTER_ID, task);
			vmType = SimSettings.VM_TYPES.CLOUD_VM.ordinal();
			nextEvent = REQUEST_RECEIVED_BY_CLOUD;
			delayType = NETWORK_DELAY_TYPES.WAN_DELAY;
			nextDeviceForNetworkModel = SimSettings.CLOUD_DATACENTER_ID;
		}
		else {
			// Task offloaded to edge: configure WLAN network parameters
			delay = networkModel.getUploadDelay(task.getMobileDeviceId(), SimSettings.GENERIC_EDGE_DEVICE_ID, task);
			vmType = SimSettings.VM_TYPES.EDGE_VM.ordinal();
			nextEvent = REQUEST_RECEIVED_BY_EDGE_DEVICE;
			delayType = NETWORK_DELAY_TYPES.WLAN_DELAY;
			nextDeviceForNetworkModel = SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		
		if(delay>0){
			// Network delay calculation successful, proceed with VM selection
			Vm selectedVM = SimManager.getInstance().getEdgeOrchestrator().getVmToOffload(task, nextHopId);
			
			if(selectedVM != null){
				// Configure task association with selected infrastructure components
				task.setAssociatedDatacenterId(nextHopId);
				task.setAssociatedHostId(selectedVM.getHost().getId());
				task.setAssociatedVmId(selectedVM.getId());
				
				// Register task in CloudSim framework
				getCloudletList().add(task);
				bindCloudletToVm(task.getCloudletId(), selectedVM.getId());
				
				// Check if task requires neighbor edge server communication via MAN
				if(selectedVM instanceof EdgeVM){
					EdgeHost host = (EdgeHost)(selectedVM.getHost());
					
					// If selected edge server is in different WLAN area, use MAN relay
					if(host.getLocation().getServingWlanId() != task.getSubmittedLocation().getServingWlanId()){
						nextEvent = REQUEST_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_NEIGHBOR;
					}
				}
				
				// Start network transmission and logging
				networkModel.uploadStarted(currentLocation, nextDeviceForNetworkModel);
				SimLogger.getInstance().taskStarted(task.getCloudletId(), CloudSim.clock());
				SimLogger.getInstance().setUploadDelay(task.getCloudletId(), delay, delayType);

				// Schedule task arrival at destination
				schedule(getId(), delay, nextEvent, task);
			}
			else{
				// No available VM capacity at selected destination
				SimLogger.getInstance().rejectedDueToVMCapacity(task.getCloudletId(), CloudSim.clock(), vmType);
			}
		}
		else
		{
			// Insufficient network bandwidth for task transmission
			SimLogger.getInstance().rejectedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), vmType, delayType);
		}
	}
	
	/**
	 * Submits a task to the assigned virtual machine for execution.
	 * This method handles the final step of task orchestration by delivering
	 * the task to the selected VM and logging assignment details for performance analysis.
	 * 
	 * @param task The task to be executed on the VM
	 * @param vmType Type of VM (cloud or edge) for logging purposes
	 * @pre task != null && task has valid VM assignment
	 * @post Task is submitted to CloudSim for execution
	 */
	private void submitTaskToVm(Task task, SimSettings.VM_TYPES vmType) {
		// Submit task to CloudSim scheduler for immediate execution
		schedule(getVmsToDatacentersMap().get(task.getVmId()), 0, CloudSimTags.CLOUDLET_SUBMIT, task);

		// Log task assignment details for performance analysis
		SimLogger.getInstance().taskAssigned(task.getCloudletId(),
				task.getAssociatedDatacenterId(),
				task.getAssociatedHostId(),
				task.getAssociatedVmId(),
				vmType.ordinal());
	}
	
	/**
	 * Creates a CloudSim task from EdgeCloudSim task properties.
	 * This method converts high-level task specifications into CloudSim-compatible
	 * task objects with appropriate resource utilization models and unique identifiers.
	 * 
	 * @param edgeTask EdgeCloudSim task properties containing task specifications
	 * @return CloudSim Task object ready for execution and network modeling
	 * @pre edgeTask != null && contains valid task parameters
	 * @post Returns initialized Task with unique ID and resource models
	 */
	private Task createTask(TaskProperty edgeTask){
		// Configure resource utilization models for realistic resource consumption
		UtilizationModel utilizationModel = new UtilizationModelFull(); // Memory/Storage: full utilization
		UtilizationModel utilizationModelCPU = getCpuUtilizationModel();   // CPU: custom model

		// Create CloudSim task with EdgeCloudSim parameters and unique ID
		Task task = new Task(edgeTask.getMobileDeviceId(), ++taskIdCounter,
				edgeTask.getLength(), edgeTask.getPesNumber(),
				edgeTask.getInputFileSize(), edgeTask.getOutputFileSize(),
				utilizationModelCPU, utilizationModel, utilizationModel);
		
		// Configure task ownership and classification
		task.setUserId(this.getId());                    // Set mobile device manager as owner
		task.setTaskType(edgeTask.getTaskType());        // Set application-specific task type
		
		// Initialize custom CPU utilization model with task context
		if (utilizationModelCPU instanceof CpuUtilizationModel_Custom) {
			((CpuUtilizationModel_Custom)utilizationModelCPU).setTask(task);
		}
		
		return task;
	}
}
