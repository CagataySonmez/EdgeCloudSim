/*
 * Title:        EdgeCloudSim - Mobile Device Manager
 * 
 * Description: 
 * DefaultMobileDeviceManager is responsible for submitting the tasks to the related
 * device by using the Edge Orchestrator. It also takes proper actions 
 * when the execution of the tasks are finished.
 * By default, DefaultMobileDeviceManager sends tasks to the edge servers or
 * cloud servers. If you want to use different topology, for example
 * MAN edge server, you should modify the flow defined in this class.
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.fuzzy;

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
import edu.boun.edgecloudsim.edge_server.EdgeHost;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.utils.TaskProperty;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;

public class FuzzyMobileDeviceManager extends MobileDeviceManager {
	private static final int BASE = 100000; // start from base in order not to conflict cloudsim tag!

	// UPDATE
	private static final int UPDATE_MM1_QUEUE_MODEL = BASE + 1;

	// REQ
	private static final int REQUEST_RECEIVED_BY_MOBILE_DEVICE = BASE + 2;
	private static final int REQUEST_RECEIVED_BY_EDGE_DEVICE = BASE + 3;
	private static final int REQUEST_RECEIVED_BY_CLOUD = BASE + 4;
	private static final int REQUEST_RECEIVED_BY_REMOTE_EDGE_DEVICE = BASE + 5;
	private static final int REQUEST_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_NEIGHBOR = BASE + 6;
	// RES
	private static final int RESPONSE_RECEIVED_BY_MOBILE_DEVICE = BASE + 7;
	private static final int RESPONSE_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_MOBILE_DEVICE = BASE + 8;

	private static final double MM1_QUEUE_MODEL_UPDATE_INTEVAL = 5; // seconds

	private int taskIdCounter = 0;

	public FuzzyMobileDeviceManager() throws Exception {
	}

	@Override
	public void initialize() {
	}

	@Override
	public UtilizationModel getCpuUtilizationModel() {
		return new CpuUtilizationModel_Custom();
	}

	@Override
	public void startEntity() {
		super.startEntity();
		schedule(getId(), SimSettings.CLIENT_ACTIVITY_START_TIME +
				MM1_QUEUE_MODEL_UPDATE_INTEVAL, UPDATE_MM1_QUEUE_MODEL);
	}

	/**
	 * Submit cloudlets to the created VMs.
	 * 
	 * @pre $none
	 * @post $none
	 */
	protected void submitCloudlets() {
		// do nothing!
	}

	/**
	 * Process a cloudlet return event.
	 * 
	 * @param ev a SimEvent object
	 * @pre ev != $null
	 * @post $none
	 */
	protected void processCloudletReturn(SimEvent ev) {
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		Task task = (Task) ev.getData();

		SimLogger.getInstance().taskExecuted(task.getCloudletId());

		if (task.getAssociatedDatacenterId() == SimSettings.CLOUD_DATACENTER_ID) {
			// SimLogger.printLine(CloudSim.clock() + ": " + getName() + ": task #" +
			// task.getCloudletId() + " received from cloud");
			double WanDelay = networkModel.getDownloadDelay(SimSettings.CLOUD_DATACENTER_ID, task.getMobileDeviceId(),
					task);
			if (WanDelay > 0) {
				Location currentLocation = SimManager.getInstance().getMobilityModel()
						.getLocation(task.getMobileDeviceId(), CloudSim.clock() + WanDelay);
				if (task.getSubmittedLocation().getServingWlanId() == currentLocation.getServingWlanId()) {
					networkModel.downloadStarted(task.getSubmittedLocation(), SimSettings.CLOUD_DATACENTER_ID);
					SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), WanDelay,
							NETWORK_DELAY_TYPES.WAN_DELAY);
					schedule(getId(), WanDelay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);
				} else {
					SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
				}
			} else {
				SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(),
						NETWORK_DELAY_TYPES.WAN_DELAY);
			}

		} else if (task.getAssociatedDatacenterId() == SimSettings.MOBILE_DATACENTER_ID) {
			SimLogger.getInstance().taskEnded(task.getCloudletId(), CloudSim.clock());

		} else {
			int nextEvent = RESPONSE_RECEIVED_BY_MOBILE_DEVICE;
			int nextDeviceForNetworkModel = SimSettings.GENERIC_EDGE_DEVICE_ID;
			NETWORK_DELAY_TYPES delayType = NETWORK_DELAY_TYPES.WLAN_DELAY;
			double delay = networkModel.getDownloadDelay(task.getAssociatedHostId(), task.getMobileDeviceId(), task);

			EdgeHost host = (EdgeHost) (SimManager.getInstance().getEdgeServerManager().getDatacenterList()
					.get(task.getAssociatedHostId()).getHostList().get(0));

			// if neighbor edge device is selected
			if (host.getLocation().getServingWlanId() != task.getSubmittedLocation().getServingWlanId()) {
				delay = networkModel.getDownloadDelay(SimSettings.GENERIC_EDGE_DEVICE_ID,
						SimSettings.GENERIC_EDGE_DEVICE_ID, task);
				nextEvent = RESPONSE_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_MOBILE_DEVICE;
				nextDeviceForNetworkModel = SimSettings.GENERIC_EDGE_DEVICE_ID + 1;
				delayType = NETWORK_DELAY_TYPES.MAN_DELAY;
			}

			if (delay > 0) {
				Location currentLocation = SimManager.getInstance().getMobilityModel()
						.getLocation(task.getMobileDeviceId(), CloudSim.clock() + delay);
				if (task.getSubmittedLocation().getServingWlanId() == currentLocation.getServingWlanId()) {
					networkModel.downloadStarted(currentLocation, nextDeviceForNetworkModel);
					SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), delay, delayType);

					schedule(getId(), delay, nextEvent, task);
				} else {
					SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
				}
			} else {
				SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), delayType);
			}
		}
	}

	protected void processOtherEvent(SimEvent ev) {
		if (ev == null) {
			SimLogger.printLine(
					getName() + ".processOtherEvent(): " + "Error - an event is null! Terminating simulation...");
			System.exit(0);
			return;
		}

		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();

		switch (ev.getTag()) {
			case UPDATE_MM1_QUEUE_MODEL: {
				((FuzzyNetworkModel) networkModel).updateMM1QueeuModel();
				schedule(getId(), MM1_QUEUE_MODEL_UPDATE_INTEVAL, UPDATE_MM1_QUEUE_MODEL);

				break;
			}
			case REQUEST_RECEIVED_BY_MOBILE_DEVICE: {
				Task task = (Task) ev.getData();
				submitTaskToVm(task, SimSettings.VM_TYPES.MOBILE_VM);
				break;
			}
			case REQUEST_RECEIVED_BY_EDGE_DEVICE: {
				Task task = (Task) ev.getData();
				networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);
				submitTaskToVm(task, SimSettings.VM_TYPES.EDGE_VM);
				break;
			}
			case REQUEST_RECEIVED_BY_CLOUD: {
				Task task = (Task) ev.getData();
				networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.CLOUD_DATACENTER_ID);
				submitTaskToVm(task, SimSettings.VM_TYPES.CLOUD_VM);
				break;
			}

			case REQUEST_RECEIVED_BY_REMOTE_EDGE_DEVICE: {
				Task task = (Task) ev.getData();
				networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID + 1);
				submitTaskToVm(task, SimSettings.VM_TYPES.EDGE_VM);

				break;
			}
			case REQUEST_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_NEIGHBOR: {
				Task task = (Task) ev.getData();
				networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);

				double manDelay = networkModel.getUploadDelay(SimSettings.GENERIC_EDGE_DEVICE_ID,
						SimSettings.GENERIC_EDGE_DEVICE_ID, task);
				if (manDelay > 0) {
					networkModel.uploadStarted(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID + 1);
					SimLogger.getInstance().setUploadDelay(task.getCloudletId(), manDelay,
							NETWORK_DELAY_TYPES.MAN_DELAY);
					schedule(getId(), manDelay, REQUEST_RECEIVED_BY_REMOTE_EDGE_DEVICE, task);
				} else {
					// SimLogger.printLine("Task #" + task.getCloudletId() + " cannot assign to any
					// VM");
					SimLogger.getInstance().rejectedDueToBandwidth(
							task.getCloudletId(),
							CloudSim.clock(),
							SimSettings.VM_TYPES.EDGE_VM.ordinal(),
							NETWORK_DELAY_TYPES.MAN_DELAY);
				}

				break;
			}
			case RESPONSE_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_MOBILE_DEVICE: {
				Task task = (Task) ev.getData();
				networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID + 1);

				// SimLogger.printLine(CloudSim.clock() + ": " + getName() + ": task #" +
				// task.getCloudletId() + " received from edge");
				double delay = networkModel.getDownloadDelay(task.getAssociatedHostId(), task.getMobileDeviceId(),
						task);

				if (delay > 0) {
					Location currentLocation = SimManager.getInstance().getMobilityModel()
							.getLocation(task.getMobileDeviceId(), CloudSim.clock() + delay);
					if (task.getSubmittedLocation().getServingWlanId() == currentLocation.getServingWlanId()) {
						networkModel.downloadStarted(currentLocation, SimSettings.GENERIC_EDGE_DEVICE_ID);
						SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), delay,
								NETWORK_DELAY_TYPES.WLAN_DELAY);
						schedule(getId(), delay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);
					} else {
						SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
					}
				} else {
					SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(),
							NETWORK_DELAY_TYPES.WLAN_DELAY);
				}

				break;
			}
			case RESPONSE_RECEIVED_BY_MOBILE_DEVICE: {
				Task task = (Task) ev.getData();

				if (task.getAssociatedDatacenterId() == SimSettings.CLOUD_DATACENTER_ID)
					networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.CLOUD_DATACENTER_ID);
				else if (task.getAssociatedDatacenterId() != SimSettings.MOBILE_DATACENTER_ID)
					networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);

				else
					networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);

				SimLogger.getInstance().taskEnded(task.getCloudletId(), CloudSim.clock());
				break;
			}
			default:
				SimLogger.printLine(getName() + ".processOtherEvent(): "
						+ "Error - event unknown by this DatacenterBroker. Terminating simulation...");
				System.exit(0);
				break;
		}
	}

	public void submitTask(TaskProperty edgeTask) {
		double delay = 0;
		int nextEvent = 0;
		int nextDeviceForNetworkModel = 0;
		VM_TYPES vmType = null;
		NETWORK_DELAY_TYPES delayType = null;

		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();

		// create a task
		Task task = createTask(edgeTask);

		Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),
				CloudSim.clock());

		// set location of the mobile device which generates this task
		task.setSubmittedLocation(currentLocation);

		// add related task to log list
		SimLogger.getInstance().addLog(task.getMobileDeviceId(),
				task.getCloudletId(),
				task.getTaskType(),
				(int) task.getCloudletLength(),
				(int) task.getCloudletFileSize(),
				(int) task.getCloudletOutputSize());

		int nextHopId = SimManager.getInstance().getEdgeOrchestrator().getDeviceToOffload(task);

		if (nextHopId == SimSettings.CLOUD_DATACENTER_ID) {
			delay = networkModel.getUploadDelay(task.getMobileDeviceId(), SimSettings.CLOUD_DATACENTER_ID, task);
			vmType = SimSettings.VM_TYPES.CLOUD_VM;
			nextEvent = REQUEST_RECEIVED_BY_CLOUD;
			delayType = NETWORK_DELAY_TYPES.WAN_DELAY;
			nextDeviceForNetworkModel = SimSettings.CLOUD_DATACENTER_ID;
		}

		else if (nextHopId == SimSettings.MOBILE_DATACENTER_ID) {
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
			delay = networkModel.getUploadDelay(task.getMobileDeviceId(), SimSettings.GENERIC_EDGE_DEVICE_ID, task);
			vmType = SimSettings.VM_TYPES.EDGE_VM;
			nextEvent = REQUEST_RECEIVED_BY_EDGE_DEVICE;
			delayType = NETWORK_DELAY_TYPES.WLAN_DELAY;
			nextDeviceForNetworkModel = SimSettings.GENERIC_EDGE_DEVICE_ID;
		}

		if (delay > 0 || nextHopId == SimSettings.MOBILE_DATACENTER_ID) {

			Vm selectedVM = SimManager.getInstance().getEdgeOrchestrator().getVmToOffload(task, nextHopId);

			if (selectedVM != null) {
				// set related host id
				task.setAssociatedDatacenterId(nextHopId);

				// set related host id
				task.setAssociatedHostId(selectedVM.getHost().getId());

				// set related vm id
				task.setAssociatedVmId(selectedVM.getId());

				// bind task to related VM
				getCloudletList().add(task);
				bindCloudletToVm(task.getCloudletId(), selectedVM.getId());

				SimLogger.getInstance().taskStarted(task.getCloudletId(), CloudSim.clock());

				// For EdgeVM, check if neighbor edge device is selected
				if (selectedVM instanceof EdgeVM) {
					EdgeHost host = (EdgeHost) (selectedVM.getHost());

					// if neighbor edge device is selected
					if (host.getLocation().getServingWlanId() != task.getSubmittedLocation().getServingWlanId()) {
						nextEvent = REQUEST_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_NEIGHBOR;
					}
				}

				// Only start upload if nextHop is not Mobile Datacenter
				if (nextHopId != SimSettings.MOBILE_DATACENTER_ID) {
					networkModel.uploadStarted(currentLocation, nextDeviceForNetworkModel);
					SimLogger.getInstance().setUploadDelay(task.getCloudletId(), delay, delayType);
				}

				// Schedule the task for the next event after the delay
				schedule(getId(), delay, nextEvent, task);

			} else {
				// If no VM was available due to capacity, log rejection
				SimLogger.getInstance().rejectedDueToVMCapacity(task.getCloudletId(), CloudSim.clock(),
						vmType.ordinal());
			}

		} else {
			// Log rejection due to insufficient bandwidth if delay <= 0
			SimLogger.getInstance().rejectedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), vmType.ordinal(),
					delayType);
		}

	}

	private void submitTaskToVm(Task task, SimSettings.VM_TYPES vmType) {
		// SimLogger.printLine(
		// CloudSim.clock() + ": Cloudlet#" + task.getCloudletId() + " is submitted to
		// VM#" + task.getVmId());

		// 기존에 있던 스케줄 작업
		schedule(getVmsToDatacentersMap().get(task.getVmId()), 0, CloudSimTags.CLOUDLET_SUBMIT, task);

		// 작업을 할당한 후 CPU utilization을 추적
		SimLogger.getInstance().taskAssigned(task.getCloudletId(),
				task.getAssociatedDatacenterId(),
				task.getAssociatedHostId(),
				task.getAssociatedVmId(),
				vmType.ordinal());

		// CPU utilization 로그를 기록하는 부분 추가
		// if (vmType == SimSettings.VM_TYPES.MOBILE_VM) {
		// double cpuUtilization =
		// task.getUtilizationModelCpu().getUtilization(CloudSim.clock());
		// SimLogger.printLine(
		// "Mobile Device ID: " + task.getMobileDeviceId() + ", CPU Utilization: " +
		// cpuUtilization);
		// }
	}

	private Task createTask(TaskProperty edgeTask) {
		UtilizationModel utilizationModel = new UtilizationModelFull(); /* UtilizationModelStochastic */
		UtilizationModel utilizationModelCPU = getCpuUtilizationModel();

		Task task = new Task(edgeTask.getMobileDeviceId(), ++taskIdCounter,
				edgeTask.getLength(), edgeTask.getPesNumber(),
				edgeTask.getInputFileSize(), edgeTask.getOutputFileSize(),
				utilizationModelCPU, utilizationModel, utilizationModel);

		// set the owner of this task
		task.setUserId(this.getId());
		task.setTaskType(edgeTask.getTaskType());

		if (utilizationModelCPU instanceof CpuUtilizationModel_Custom) {
			((CpuUtilizationModel_Custom) utilizationModelCPU).setTask(task);
		}

		return task;
	}
}
