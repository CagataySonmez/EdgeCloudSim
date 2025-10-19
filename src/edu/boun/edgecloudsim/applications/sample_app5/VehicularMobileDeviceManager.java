/*
 * Title:        EdgeCloudSim - Vehicular Mobile Device Manager
 * 
 * Description: 
 * VehicularMobileDeviceManager implements advanced mobile device management specifically
 * designed for vehicular edge computing scenarios. This manager handles task orchestration
 * in dynamic vehicular environments with sophisticated network modeling capabilities:
 * 
 * Key Features:
 * - Vehicular-specific task routing via VehicularEdgeOrchestrator
 * - Multi-connectivity support: GSM, RSU-to-cloud, WLAN, MAN networks
 * - High-mobility awareness with real-time location tracking
 * - Vehicular CPU utilization modeling for realistic resource consumption
 * - Advanced handoff management for moving vehicles
 * - Road Side Unit (RSU) integration for V2I communication
 * - Adaptive network queue modeling with frequent updates
 * - Comprehensive failure handling for mobility-induced disconnections
 * 
 * The manager supports complex vehicular scenarios including vehicle-to-infrastructure
 * communication, mobile edge computing with RSUs, and seamless handoffs between
 * different network technologies in vehicular environments.
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app5;

import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;

import edu.boun.edgecloudsim.applications.sample_app5.VehicularEdgeOrchestrator;
import edu.boun.edgecloudsim.cloud_server.CloudVM;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.core.SimSettings.NETWORK_DELAY_TYPES;
import edu.boun.edgecloudsim.core.SimSettings.VM_TYPES;
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.edge_server.EdgeHost;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.utils.TaskProperty;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;

/**
 * VehicularMobileDeviceManager extends the base MobileDeviceManager to provide
 * specialized functionality for vehicular edge computing scenarios. This manager
 * handles complex vehicular networking environments with multiple connectivity options,
 * high mobility patterns, and sophisticated task orchestration strategies.
 * 
 * Key Vehicular Features:
 * - Multi-modal connectivity: GSM, RSU-WAN, WLAN, MAN networks
 * - Real-time vehicle mobility tracking and handoff management
 * - Road Side Unit (RSU) integration for Vehicle-to-Infrastructure (V2I) communication
 * - Vehicular-specific CPU utilization modeling
 * - High-frequency network queue model updates for dynamic environments
 * - Advanced failure recovery mechanisms for mobility-induced disconnections
 * - Comprehensive delay logging and performance analysis
 * 
 * The manager implements event-driven architecture with vehicular-specific event types
 * and provides seamless task execution across different network technologies commonly
 * found in intelligent transportation systems and vehicular edge computing deployments.
 */
public class VehicularMobileDeviceManager extends MobileDeviceManager {
	/** Base value for custom event tags to avoid conflicts with CloudSim tags */
	private static final int BASE = 100000;
	
	/** Event tag for periodic M/M/1 queue model updates in dynamic vehicular environment */
	private static final int UPDATE_MM1_QUEUE_MODEL = BASE + 1;
	/** Event tag for logging network delay measurements for performance analysis */
	private static final int SET_DELAY_LOG = BASE + 2;
	/** Event tag indicating readiness for VM selection after network delays */
	private static final int READY_TO_SELECT_VM = BASE + 3;
	/** Event tag for task request arrival at cloud datacenter via GSM */
	private static final int REQUEST_RECEIVED_BY_CLOUD = BASE + 4;
	/** Event tag for task request processing at mobile device */
	private static final int REQUEST_RECEIVED_BY_MOBILE_DEVICE = BASE + 5;
	/** Event tag for task request arrival at edge device via WLAN */
	private static final int REQUEST_RECEIVED_BY_EDGE_DEVICE = BASE + 6;
	/** Event tag for edge device relaying task to cloud via RSU-WAN */
	private static final int REQUEST_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_CLOUD = BASE + 7;
	/** Event tag for edge device relaying task to neighbor edge via MAN */
	private static final int REQUEST_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_NEIGHBOR = BASE + 8;
	/** Event tag for task response delivery to mobile device */
	private static final int RESPONSE_RECEIVED_BY_MOBILE_DEVICE = BASE + 9;
	/** Event tag for task response arrival at edge device */
	private static final int RESPONSE_RECEIVED_BY_EDGE_DEVICE = BASE + 10;
	/** Event tag for edge device relaying response to mobile device */
	private static final int RESPONSE_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_MOBILE_DEVICE = BASE + 11;

	/** Update interval for M/M/1 queue model (frequent updates for vehicular dynamics) */
	private static final double MM1_QUEUE_MODEL_UPDATE_INTERVAL = 0.5;
	
	/** Counter for generating unique task IDs across vehicular network */
	private int taskIdCounter=0;

	/**
	 * Constructor for VehicularMobileDeviceManager.
	 * Initializes the vehicular-specific mobile device manager with enhanced
	 * capabilities for dynamic vehicular environments and multi-modal connectivity.
	 * 
	 * @throws Exception if initialization fails
	 */
	public VehicularMobileDeviceManager() throws Exception{
	}

	/**
	 * Initializes the vehicular mobile device manager.
	 * Sets up vehicular-specific components including network models,
	 * mobility tracking systems, and multi-connectivity infrastructure.
	 */
	@Override
	public void initialize() {
	}

	/**
	 * Returns the CPU utilization model specifically designed for vehicular scenarios.
	 * The VehicularCpuUtilizationModel accounts for the unique resource consumption
	 * patterns in vehicular applications and mobile computing environments.
	 * 
	 * @return VehicularCpuUtilizationModel for realistic vehicular CPU modeling
	 */
	@Override
	public UtilizationModel getCpuUtilizationModel() {
		return new VehicularCpuUtilizationModel();
	}

	/**
	 * Starts the vehicular entity and schedules periodic network model updates.
	 * Initializes high-frequency queue model updates suitable for dynamic vehicular
	 * environments and optional delay logging for performance analysis.
	 */
	@Override
	public void startEntity() {
		super.startEntity();
		// Schedule frequent queue model updates for dynamic vehicular environment
		schedule(getId(), SimSettings.CLIENT_ACTIVITY_START_TIME +
				MM1_QUEUE_MODEL_UPDATE_INTERVAL, UPDATE_MM1_QUEUE_MODEL);

		// Schedule delay logging if configured for performance analysis
		if(SimSettings.getInstance().getApDelayLogInterval() != 0)
			schedule(getId(), SimSettings.getInstance().getApDelayLogInterval(), SET_DELAY_LOG);
	}

	/**
	 * Legacy method - not used in vehicular scenarios.
	 * Tasks are submitted through submitTask() method instead.
	 */
	protected void submitCloudlets() {
		// No implementation needed - tasks submitted via different mechanism
	}

	/**
	 * Processes task completion events and handles network delays for result transmission.
	 * Calculates download delays based on the datacenter type used for task execution.
	 * 
	 * @param ev SimEvent containing the completed task
	 */
	protected void processCloudletReturn(SimEvent ev) {
		VehicularNetworkModel networkModel = (VehicularNetworkModel)SimManager.getInstance().getNetworkModel();
		VehicularEdgeOrchestrator edgeOrchestrator = (VehicularEdgeOrchestrator)SimManager.getInstance().getEdgeOrchestrator();

		Task task = (Task) ev.getData();

		// Log task execution completion for performance analysis
		SimLogger.getInstance().taskExecuted(task.getCloudletId());

		// Handle response from cloud datacenter via GSM connectivity
		if(task.getAssociatedDatacenterId() == VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_GSM) {
			NETWORK_DELAY_TYPES delayType = NETWORK_DELAY_TYPES.GSM_DELAY;
			double gsmDelay = networkModel.getDownloadDelay(delayType, task);
			if(gsmDelay > 0)
			{
				// Sufficient GSM bandwidth available for response delivery
				SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), gsmDelay, delayType);
				schedule(getId(), gsmDelay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);
			}
			else
			{
				// Insufficient GSM bandwidth, task fails
				SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), delayType);
				edgeOrchestrator.taskFailed(task);
			}
		}
		// Handle response from cloud datacenter via RSU-to-WAN connectivity
		else if(task.getAssociatedDatacenterId() == VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_RSU) {
			NETWORK_DELAY_TYPES delayType = NETWORK_DELAY_TYPES.WAN_DELAY;
			double wanDelay = networkModel.getDownloadDelay(delayType, task);
			if(wanDelay > 0)
			{
				// Sufficient RSU-WAN bandwidth available, response goes to edge device first
				SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), wanDelay, delayType);
				schedule(getId(), wanDelay, RESPONSE_RECEIVED_BY_EDGE_DEVICE, task);
			}
			else
			{
				// Insufficient RSU-WAN bandwidth, task fails
				SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), delayType);
				edgeOrchestrator.taskFailed(task);
			}
		}
		// Handle response from edge datacenter with mobility awareness
		else if(task.getAssociatedDatacenterId() == VehicularEdgeOrchestrator.EDGE_DATACENTER) {
			Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock());
			
			// Check if vehicle is still in same WLAN coverage area
			if(task.getSubmittedLocation().getServingWlanId() == currentLocation.getServingWlanId())
			{
				// Vehicle hasn't moved to different RSU coverage, use direct WLAN delivery
				NETWORK_DELAY_TYPES delayType = NETWORK_DELAY_TYPES.WLAN_DELAY;
				double wlanDelay = networkModel.getDownloadDelay(delayType, task);
				if(wlanDelay > 0)
				{
					// Check future location to ensure vehicle won't move during transmission
					Location futureLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock()+wlanDelay);
					if(task.getSubmittedLocation().getServingWlanId() == futureLocation.getServingWlanId())
					{
						// Vehicle will remain in coverage, deliver response directly
						SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), wlanDelay, delayType);
						schedule(getId(), wlanDelay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);
					}
					else
					{
						// Vehicle will move out of coverage during transmission, task fails due to mobility
						SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
						// Note: No need to record failed task due to mobility in orchestrator
					}
				}
				else
				{
					// Insufficient WLAN bandwidth, task fails
					SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), delayType);
					edgeOrchestrator.taskFailed(task);
				}
			}
			else
			{
				// Vehicle moved to different RSU coverage, use MAN network for relay
				NETWORK_DELAY_TYPES delayType = NETWORK_DELAY_TYPES.MAN_DELAY;
				double manDelay = networkModel.getDownloadDelay(delayType, task);
				if(manDelay > 0)
				{
					// Sufficient MAN bandwidth available for inter-RSU relay
					SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), manDelay, delayType);
					schedule(getId(), manDelay, RESPONSE_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_MOBILE_DEVICE, task);
				}
				else
				{
					// Insufficient MAN bandwidth for inter-RSU communication
					SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), delayType);
					edgeOrchestrator.taskFailed(task);
				}
			}
		}
		else {
			// Unknown datacenter configuration error
			SimLogger.printLine("Unknown datacenter id! Terminating simulation...");
			System.exit(1);
		}

	}

	/**
	 * Processes custom vehicular events including network updates and task routing.
	 * This method handles vehicular-specific events such as M/M/1 queue model updates,
	 * delay logging, VM selection, and complex multi-hop task routing scenarios.
	 * 
	 * @param ev The simulation event to process
	 * @pre ev != null
	 * @post Event is processed according to vehicular networking requirements
	 */
	protected void processOtherEvent(SimEvent ev) {
		if (ev == null) {
			SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - an event is null! Terminating simulation...");
			System.exit(1);
			return;
		}

		VehicularNetworkModel networkModel = (VehicularNetworkModel)SimManager.getInstance().getNetworkModel();
		VehicularEdgeOrchestrator edgeOrchestrator = (VehicularEdgeOrchestrator)SimManager.getInstance().getEdgeOrchestrator();

		switch (ev.getTag()) {
		case UPDATE_MM1_QUEUE_MODEL:
		{
			// Update M/M/1 queue model parameters for dynamic vehicular environment
			((VehicularNetworkModel)networkModel).updateMM1QueeuModel();
			// Schedule next update with high frequency for vehicular dynamics
			schedule(getId(), MM1_QUEUE_MODEL_UPDATE_INTERVAL, UPDATE_MM1_QUEUE_MODEL);

			break;
		}
		case SET_DELAY_LOG:
		{
			// Log network delay measurements for selected access points
			int[] indices = {0,6,10}; // Sample RSU indices for performance monitoring
			double apUploadDelays[] = new double[indices.length];
			double apDownloadDelays[] = new double[indices.length];
			
			// Estimate current WLAN delays for selected RSUs
			for(int i=0; i<indices.length; i++){
				apUploadDelays[i] = networkModel.estimateWlanUploadDelay(indices[i]);
				apDownloadDelays[i] = networkModel.estimateWlanDownloadDelay(indices[i]);
			}
			
			// Record delay measurements for performance analysis
			SimLogger.getInstance().addApDelayLog(CloudSim.clock(), apUploadDelays, apDownloadDelays);

			// Schedule next delay logging interval
			schedule(getId(), SimSettings.getInstance().getVmLoadLogInterval(), SET_DELAY_LOG);
			break;
		}
		case READY_TO_SELECT_VM:
		{
			// Handle VM selection after network delay calculations
			Task task = (Task) ev.getData();

			int nextHopId = task.getAssociatedDatacenterId();
			int nextEvent = 0;
			VM_TYPES vmType = null;

			// Configure routing based on orchestrator decision
			if(nextHopId == VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_GSM){
				// Direct GSM connection to cloud datacenter
				nextEvent = REQUEST_RECEIVED_BY_CLOUD;
				vmType = VM_TYPES.CLOUD_VM;
			}
			else if(nextHopId == VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_RSU){
				// Two-hop routing: vehicle -> RSU -> cloud via WAN
				nextEvent = REQUEST_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_CLOUD;
				vmType = VM_TYPES.CLOUD_VM;
			}
			else if(nextHopId == VehicularEdgeOrchestrator.EDGE_DATACENTER){
				// Edge computing: direct or via neighbor RSU (determined later)
				vmType = VM_TYPES.EDGE_VM;
			}
			else {
				// Unknown routing configuration error
				SimLogger.printLine("Unknown nextHopId! Terminating simulation...");
				System.exit(1);
			}

			// Select appropriate VM based on orchestrator decision
			Vm selectedVM = SimManager.getInstance().getEdgeOrchestrator().getVmToOffload(task, nextHopId);

			if(selectedVM != null) {
				// Configure task association with selected infrastructure
				task.setAssociatedHostId(selectedVM.getHost().getId());
				task.setAssociatedVmId(selectedVM.getId());

				// Register task in CloudSim framework
				getCloudletList().add(task);
				bindCloudletToVm(task.getCloudletId(), selectedVM.getId());
				getCloudletList().clear();

				// Determine routing strategy for edge VMs based on location
				if(selectedVM instanceof EdgeVM) {
					EdgeHost host = (EdgeHost)(selectedVM.getHost());

					// Check if selected edge server is in same RSU coverage area
					if(host.getLocation().getServingWlanId() == task.getSubmittedLocation().getServingWlanId())
						nextEvent = REQUEST_RECEIVED_BY_EDGE_DEVICE; // Direct WLAN connection
					else
						nextEvent = REQUEST_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_NEIGHBOR; // MAN relay required
				}

				// Proceed with task submission immediately
				scheduleNow(getId(), nextEvent, task);
			}
			else {
				// No available VM capacity at selected destination
				SimLogger.getInstance().rejectedDueToVMCapacity(task.getCloudletId(), CloudSim.clock(), vmType.ordinal());
				edgeOrchestrator.taskFailed(task);
			}
			break;
		}
		case REQUEST_RECEIVED_BY_CLOUD:
		{
			// Handle task arrival at cloud datacenter (via GSM)
			Task task = (Task) ev.getData();
			submitTaskToVm(task, SimSettings.VM_TYPES.CLOUD_VM);
			break;
		}
		case REQUEST_RECEIVED_BY_EDGE_DEVICE:
		{
			// Handle task arrival at edge device (direct WLAN connection)
			Task task = (Task) ev.getData();			
			submitTaskToVm(task, SimSettings.VM_TYPES.EDGE_VM);
			break;
		}
		case REQUEST_RECEIVED_BY_MOBILE_DEVICE:
		{
			// Handle task execution on mobile device (local processing)
			Task task = (Task) ev.getData();			
			submitTaskToVm(task, SimSettings.VM_TYPES.MOBILE_VM);
			break;
		}
		case REQUEST_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_CLOUD:
		{
			// Handle task relay from RSU to cloud datacenter via WAN
			Task task = (Task) ev.getData();
			NETWORK_DELAY_TYPES delayType = NETWORK_DELAY_TYPES.WAN_DELAY;

			// Calculate RSU-to-cloud WAN upload delay
			double wanDelay =  networkModel.getUploadDelay(delayType, task);
			if(wanDelay>0){
				// Sufficient RSU-WAN bandwidth available
				SimLogger.getInstance().setUploadDelay(task.getCloudletId(), wanDelay, delayType);
				schedule(getId(), wanDelay, REQUEST_RECEIVED_BY_CLOUD, task);
			}
			else
			{
				// Insufficient RSU-WAN bandwidth, task fails
				SimLogger.getInstance().rejectedDueToBandwidth(
						task.getCloudletId(),
						CloudSim.clock(),
						SimSettings.VM_TYPES.CLOUD_VM.ordinal(),
						delayType);

				edgeOrchestrator.taskFailed(task);
			}
			break;
		}
		case REQUEST_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_NEIGHBOR:
		{
			// Handle task relay from current RSU to neighbor RSU via MAN
			Task task = (Task) ev.getData();
			NETWORK_DELAY_TYPES delayType = NETWORK_DELAY_TYPES.MAN_DELAY;

			// Calculate inter-RSU MAN upload delay
			double manDelay =  networkModel.getUploadDelay(delayType, task);
			if(manDelay>0){
				// Sufficient MAN bandwidth available for inter-RSU communication
				SimLogger.getInstance().setUploadDelay(task.getCloudletId(), manDelay, delayType);
				schedule(getId(), manDelay, REQUEST_RECEIVED_BY_EDGE_DEVICE, task);
			}
			else
			{
				// Insufficient MAN bandwidth for inter-RSU relay
				SimLogger.getInstance().rejectedDueToBandwidth(
						task.getCloudletId(),
						CloudSim.clock(),
						SimSettings.VM_TYPES.EDGE_VM.ordinal(),
						delayType);

				edgeOrchestrator.taskFailed(task);
			}
			break;
		}
		case RESPONSE_RECEIVED_BY_EDGE_DEVICE:
		{
			// Handle response arrival at edge device (from cloud via RSU-WAN)
			Task task = (Task) ev.getData();
			
			// Check vehicle's current location for optimal response routing
			Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock());
			if(task.getSubmittedLocation().getServingWlanId() == currentLocation.getServingWlanId())
			{
				// Vehicle still in same RSU coverage, proceed with direct delivery
				scheduleNow(getId(), RESPONSE_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_MOBILE_DEVICE, task);
			}
			else
			{
				// Vehicle moved to different RSU, use MAN for inter-RSU relay
				NETWORK_DELAY_TYPES delayType = NETWORK_DELAY_TYPES.MAN_DELAY;
				double manDelay = networkModel.getDownloadDelay(delayType, task);
				if(manDelay > 0)
				{
					// Sufficient MAN bandwidth for inter-RSU response relay
					SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), manDelay, delayType);
					schedule(getId(), manDelay, RESPONSE_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_MOBILE_DEVICE, task);
				}
				else
				{
					// Insufficient MAN bandwidth for response relay
					SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), delayType);
					edgeOrchestrator.taskFailed(task);
				}
			}

			break;
		}
		case RESPONSE_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_MOBILE_DEVICE:
		{
			// Handle final response delivery from RSU to vehicle via WLAN
			Task task = (Task) ev.getData();
			NETWORK_DELAY_TYPES delayType = NETWORK_DELAY_TYPES.WLAN_DELAY;

			// Calculate WLAN download delay for response delivery
			double wlanDelay = networkModel.getDownloadDelay(delayType, task);

			if(wlanDelay > 0)
			{
				// Check vehicle mobility during response transmission
				Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock());
				Location futureLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock()+wlanDelay);

				if(currentLocation.getServingWlanId() == futureLocation.getServingWlanId())
				{
					// Vehicle will remain in RSU coverage during transmission
					SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), wlanDelay, delayType);
					schedule(getId(), wlanDelay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);
				}
				else
				{
					// Vehicle will move out of RSU coverage, task fails due to mobility
					SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
					// Note: Mobility failures don't need orchestrator notification
				}
			}
			else
			{
				// Insufficient WLAN bandwidth for response delivery
				SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), delayType);
				edgeOrchestrator.taskFailed(task);
			}
			break;
		}
		case RESPONSE_RECEIVED_BY_MOBILE_DEVICE:
		{
			// Handle final task completion and QoE calculation
			Task task = (Task) ev.getData();

			// Retrieve task properties for QoE calculation
			String taskName = SimSettings.getInstance().getTaskName(task.getTaskType());
			double taskProperty[] = SimSettings.getInstance().getTaskProperties(taskName);
			double serviceTime = CloudSim.clock() - task.getCreationTime();
			double delaySensitivity = taskProperty[12]; // Application sensitivity to delays
			double maxDelayRequirement = taskProperty[13]; // Maximum acceptable delay

			// Calculate Quality of Experience (QoE) based on service time and requirements
			double QoE = 100; // Perfect QoE by default
			if(serviceTime > maxDelayRequirement){
				// Calculate QoE degradation for delay-sensitive applications
				QoE = (Math.min(2*maxDelayRequirement,serviceTime) - maxDelayRequirement) / maxDelayRequirement;
				QoE = 100 * (1-QoE) * (1-delaySensitivity);
			}

			// Log QoE metrics for performance analysis
			SimLogger.getInstance().setQoE(task.getCloudletId(),QoE);

			// Notify orchestrator of successful task completion
			edgeOrchestrator.taskCompleted(task, serviceTime);

			// Log task completion event
			SimLogger.getInstance().taskEnded(task.getCloudletId(), CloudSim.clock());

			break;
		}
		default:
			// Handle unknown event types with error termination
			SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - event unknown by this DatacenterBroker. Terminating simulation...");
			System.exit(1);
			break;
		}
	}

	/**
	 * Submits a task for processing by determining the optimal offloading destination.
	 * Handles the complete task lifecycle from submission to scheduling.
	 * 
	 * @param edgeTask task properties including size, type, and timing requirements
	 */
	public synchronized void submitTask(TaskProperty edgeTask) {
		VehicularNetworkModel networkModel = (VehicularNetworkModel)SimManager.getInstance().getNetworkModel();
		VehicularEdgeOrchestrator edgeOrchestrator = (VehicularEdgeOrchestrator)SimManager.getInstance().getEdgeOrchestrator();

		// Create task object from properties
		Task task = createTask(edgeTask);

		// Get current location of the mobile device generating this task
		Location currentLocation = SimManager.getInstance().getMobilityModel().
				getLocation(task.getMobileDeviceId(),CloudSim.clock());

		// Associate task with current device location
		task.setSubmittedLocation(currentLocation);

		// Log task details for performance analysis
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

		double delay = 0;
		VM_TYPES vmType = null;
		NETWORK_DELAY_TYPES delayType = null;

		if(nextHopId == VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_GSM){
			vmType = VM_TYPES.CLOUD_VM;
			delayType = NETWORK_DELAY_TYPES.GSM_DELAY;
			delay = networkModel.getUploadDelay(delayType, task);
		}
		//task is sent to cloud over RSU via 2 hops
		else if(nextHopId == VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_RSU){
			vmType = VM_TYPES.CLOUD_VM;
			delayType = NETWORK_DELAY_TYPES.WLAN_DELAY;
			delay = networkModel.getUploadDelay(delayType, task);
		}
		//task is sent to best edge device via 2 hops (unless the best edge is the nearest one)
		else if(nextHopId == VehicularEdgeOrchestrator.EDGE_DATACENTER){
			vmType = VM_TYPES.EDGE_VM;
			delayType = NETWORK_DELAY_TYPES.WLAN_DELAY;
			delay = networkModel.getUploadDelay(delayType, task);
		}
		else {
			SimLogger.printLine("Unknown nextHopId! Terminating simulation...");
			System.exit(1);
		}

		task.setAssociatedDatacenterId(nextHopId);

		if(delay>0){
			// Sufficient network bandwidth available, schedule VM selection
			schedule(getId(), delay, READY_TO_SELECT_VM, task);

			// Log task initiation and network delay
			SimLogger.getInstance().taskStarted(task.getCloudletId(), CloudSim.clock());
			SimLogger.getInstance().setUploadDelay(task.getCloudletId(), delay, delayType);
		}
		else
		{
			// Insufficient network bandwidth, reject task immediately
			SimLogger.getInstance().rejectedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), vmType.ordinal(), delayType);

			edgeOrchestrator.taskFailed(task);
		}
	}

	/**
	 * Submits a task to the specified virtual machine after capacity validation.
	 * This method handles the final step of task assignment by checking VM capacity
	 * and either executing the task or rejecting it based on resource availability.
	 * 
	 * @param task The task to be submitted for execution
	 * @param vmType Type of VM (edge, cloud, or mobile) for capacity management
	 * @pre task != null && task has valid VM assignment
	 * @post Task is submitted to CloudSim or rejected with appropriate logging
	 */
	private void submitTaskToVm(Task task, SimSettings.VM_TYPES vmType) {
		VehicularEdgeOrchestrator edgeOrchestrator = (VehicularEdgeOrchestrator)SimManager.getInstance().getEdgeOrchestrator();

		Vm targetVM = null;
		if(vmType == VM_TYPES.EDGE_VM) {
			int numberOfHost = SimSettings.getInstance().getNumOfEdgeHosts();
			for (int hostIndex = 0; hostIndex < numberOfHost; hostIndex++) {
				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex);
				for (int vmIndex = 0; vmIndex < vmArray.size(); vmIndex++) {
					if(vmArray.get(vmIndex).getId() == task.getAssociatedVmId()) {
						targetVM = vmArray.get(vmIndex);
						break;
					}
				}
				if(targetVM != null)
					break;
			}
		}
		else if(vmType == VM_TYPES.CLOUD_VM) {
			List<Host> list = SimManager.getInstance().getCloudServerManager().getDatacenter().getHostList();
			for (int hostIndex = 0; hostIndex < list.size(); hostIndex++) {
				List<CloudVM> vmArray = SimManager.getInstance().getCloudServerManager().getVmList(hostIndex);
				for (int vmIndex = 0; vmIndex < vmArray.size(); vmIndex++) {
					if(vmArray.get(vmIndex).getId() == task.getAssociatedVmId()) {
						targetVM = vmArray.get(vmIndex);
						break;
					}
				}
				if(targetVM != null)
					break;
			}
		}
		else {
			SimLogger.printLine("Unknown vm type! Terminating simulation...");
			System.exit(1);
		}

		// Calculate current VM capacity and required resources
		double targetVmCapacity = (double) 100 - targetVM.getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
		double requiredCapacity = ((VehicularCpuUtilizationModel) task.getUtilizationModelCpu()).predictUtilization(vmType);

		// Check if VM has sufficient capacity for task execution
		if (requiredCapacity > targetVmCapacity) {
			// Insufficient VM capacity, reject task
			SimLogger.getInstance().rejectedDueToVMCapacity(task.getCloudletId(), CloudSim.clock(), vmType.ordinal());
			edgeOrchestrator.taskFailed(task);
		} else {
			// Sufficient VM capacity, submit task for execution
			schedule(getVmsToDatacentersMap().get(task.getVmId()), 0, CloudSimTags.CLOUDLET_SUBMIT, task);

			// Log successful task assignment with infrastructure details
			SimLogger.getInstance().taskAssigned(task.getCloudletId(), task.getAssociatedDatacenterId(),
					task.getAssociatedHostId(), task.getAssociatedVmId(), vmType.ordinal());
		}
	}

	/**
	 * Creates a CloudSim task from EdgeCloudSim task properties for vehicular scenarios.
	 * This method converts high-level vehicular task specifications into CloudSim-compatible
	 * task objects with vehicular-specific resource utilization models.
	 * 
	 * @param edgeTask EdgeCloudSim task properties containing vehicular task specifications
	 * @return CloudSim Task object configured for vehicular computing scenarios
	 * @pre edgeTask != null && contains valid vehicular task parameters
	 * @post Returns initialized Task with unique ID and vehicular resource models
	 */
	private Task createTask(TaskProperty edgeTask){
		// Configure resource utilization models for vehicular scenarios
		UtilizationModel utilizationModel = new UtilizationModelFull();    // Memory/Storage: full utilization
		UtilizationModel utilizationModelCPU = getCpuUtilizationModel();   // CPU: vehicular-specific model

		// Create CloudSim task with vehicular parameters and unique ID
		Task task = new Task(edgeTask.getMobileDeviceId(), ++taskIdCounter,
				edgeTask.getLength(), edgeTask.getPesNumber(),
				edgeTask.getInputFileSize(), edgeTask.getOutputFileSize(),
				utilizationModelCPU, utilizationModel, utilizationModel);

		// Configure task ownership and vehicular classification
		task.setUserId(this.getId());                       // Set vehicular device manager as owner
		task.setTaskType(edgeTask.getTaskType());          // Set vehicular application type

		// Initialize vehicular CPU utilization model with task context
		if (utilizationModelCPU instanceof VehicularCpuUtilizationModel) {
			((VehicularCpuUtilizationModel)utilizationModelCPU).setTask(task);
		}

		return task;
	}
}
