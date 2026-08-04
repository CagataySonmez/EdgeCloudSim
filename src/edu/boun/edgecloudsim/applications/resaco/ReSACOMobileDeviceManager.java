/*
 * Title:        EdgeCloudSim - ReSACO Mobile Device Manager
 *
 * Description:
 * Same task lifecycle as ThreeTierMobileDeviceManager (creates tasks,
 * submits them to the VM chosen by the orchestrator, reacts to
 * completion/failure events), plus: whenever a task's real outcome
 * becomes known, it is reported back to the ReSACO bridge
 * (ReSACOBridgeClient.reportOutcome) so the served policy's online
 * update (Algorithm 3/4, where applicable) can learn from it.
 *
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 */

package edu.boun.edgecloudsim.applications.resaco;

import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;

import edu.boun.edgecloudsim.applications.three_tier.ThreeTierNetworkModel;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.core.SimSettings.NETWORK_DELAY_TYPES;
import edu.boun.edgecloudsim.core.SimSettings.VM_TYPES;
import edu.boun.edgecloudsim.edge_client.CpuUtilizationModel_Custom;
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.edge_server.EdgeHost;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.utils.TaskProperty;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;


public class ReSACOMobileDeviceManager extends MobileDeviceManager {
	private static final int BASE = 200000; //start from base in order not to conflict cloudsim tag or the three_tier app's tags!

	private static final int UPDATE_MM1_QUEUE_MODEL = BASE + 1;
	private static final int REQUEST_RECEIVED_BY_MOBILE_DEVICE = BASE + 2;
	private static final int REQUEST_RECEIVED_BY_EDGE_DEVICE = BASE + 3;
	private static final int REQUEST_RECEIVED_BY_CLOUD = BASE + 4;
	private static final int REQUEST_RECEIVED_BY_REMOTE_EDGE_DEVICE = BASE + 5;
	private static final int REQUEST_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_NEIGHBOR = BASE + 6;
	private static final int RESPONSE_RECEIVED_BY_MOBILE_DEVICE = BASE + 7;
	private static final int RESPONSE_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_MOBILE_DEVICE = BASE + 8;

	private static final double MM1_QUEUE_MODEL_UPDATE_INTEVAL = 5; //seconds

	private int taskIdCounter = 0;

	public ReSACOMobileDeviceManager() throws Exception {
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
	}

	protected void submitCloudlets() {
		//do nothing!
	}

	/**
	 * Reports a task's real outcome back to the ReSACO bridge so the served
	 * policy's online update (Algorithm 3/4, where applicable) can learn
	 * from it. Harmless no-op (silently ignored bridge-side) if the task
	 * was actually routed by the fallback heuristic (e.g. the bridge was
	 * down at submission time).
	 */
	private void reportReSACOOutcome(Task task, boolean success) {
		String requestId = ReSACOStateBuilder.requestIdFor(task);
		// Always consume the submission clock entry -- the failure branch
		// doesn't need the elapsed time, but leaving its entry behind would
		// leak one map entry per failed task for the life of the process.
		double serviceTime = ReSACOBridgeClient.getInstance().elapsedSince(requestId);
		// Same delay-sensitivity reward weighting as the training env
		// (resaco/env.py step()): w = base + this app type's delay_sensitivity,
		// applied to both the success and the failure branch.
		double weight = ReSACOStateBuilder.delaySensitivityWeight(task);
		double reward;
		if (success) {
			if (serviceTime < 0) {
				return; // this task wasn't decided by the bridge (e.g. it was down at submission time)
			}
			// energy / monetary cost / SLA-deadline penalties, mirroring the
			// training env's industry-direction reward terms
			reward = -weight * serviceTime - ReSACOStateBuilder.industryPenalty(task, serviceTime);
		} else {
			reward = -weight * (ReSACOStateBuilder.RESACO_TMAX_SECONDS + 1);
		}
		String algo = SimManager.getInstance().getOrchestratorPolicy();
		double[] nextState = ReSACOStateBuilder.buildStateForDevice(task.getMobileDeviceId(), task.getTaskType());
		// done=false: the paper's task stream is continuing, never episodic --
		// training (env.py) always stores done=False, and done=1 would zero
		// Eq. (10)'s bootstrap term gamma*V(s') on every single online update,
		// silently degrading Algorithm 4's adaptation to a one-step bandit.
		ReSACOBridgeClient.getInstance().reportOutcome(algo, requestId, reward, false, nextState);
	}

	protected void processCloudletReturn(SimEvent ev) {
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		Task task = (Task) ev.getData();

		SimLogger.getInstance().taskExecuted(task.getCloudletId());

		if (task.getAssociatedDatacenterId() == SimSettings.CLOUD_DATACENTER_ID) {
			double WanDelay = networkModel.getDownloadDelay(SimSettings.CLOUD_DATACENTER_ID, task.getMobileDeviceId(), task);
			if (WanDelay > 0) {
				Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(), CloudSim.clock() + WanDelay);
				if (task.getSubmittedLocation().getServingWlanId() == currentLocation.getServingWlanId()) {
					networkModel.downloadStarted(task.getSubmittedLocation(), SimSettings.CLOUD_DATACENTER_ID);
					SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), WanDelay, NETWORK_DELAY_TYPES.WAN_DELAY);
					schedule(getId(), WanDelay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);
				} else {
					SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
					reportReSACOOutcome(task, false);
				}
			} else {
				SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), NETWORK_DELAY_TYPES.WAN_DELAY);
				reportReSACOOutcome(task, false);
			}
		}

		else if (task.getAssociatedDatacenterId() == SimSettings.GENERIC_EDGE_DEVICE_ID) {
			int nextEvent = RESPONSE_RECEIVED_BY_MOBILE_DEVICE;
			int nextDeviceForNetworkModel = SimSettings.GENERIC_EDGE_DEVICE_ID;
			NETWORK_DELAY_TYPES delayType = NETWORK_DELAY_TYPES.WLAN_DELAY;
			double delay = networkModel.getDownloadDelay(task.getAssociatedHostId(), task.getMobileDeviceId(), task);

			EdgeHost host = (EdgeHost) (SimManager.
					getInstance().
					getEdgeServerManager().
					getDatacenterList().get(task.getAssociatedHostId()).
					getHostList().get(0));

			//if neighbor edge device is selected
			if (host.getLocation().getServingWlanId() != task.getSubmittedLocation().getServingWlanId()) {
				delay = networkModel.getDownloadDelay(SimSettings.GENERIC_EDGE_DEVICE_ID, SimSettings.GENERIC_EDGE_DEVICE_ID, task);
				nextEvent = RESPONSE_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_MOBILE_DEVICE;
				nextDeviceForNetworkModel = SimSettings.GENERIC_EDGE_DEVICE_ID + 1;
				delayType = NETWORK_DELAY_TYPES.MAN_DELAY;
			}

			if (delay > 0) {
				Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(), CloudSim.clock() + delay);
				if (task.getSubmittedLocation().getServingWlanId() == currentLocation.getServingWlanId()) {
					networkModel.downloadStarted(currentLocation, nextDeviceForNetworkModel);
					SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), delay, delayType);

					schedule(getId(), delay, nextEvent, task);
				} else {
					SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
					reportReSACOOutcome(task, false);
				}
			} else {
				SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), delayType);
				reportReSACOOutcome(task, false);
			}
		}
		else if (task.getAssociatedDatacenterId() == SimSettings.MOBILE_DATACENTER_ID) {
			SimLogger.getInstance().taskEnded(task.getCloudletId(), CloudSim.clock());
			reportReSACOOutcome(task, true);
		}

		else {
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

		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();

		switch (ev.getTag()) {
			case UPDATE_MM1_QUEUE_MODEL: {
				((ThreeTierNetworkModel) networkModel).updateMM1QueeuModel();
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

				double manDelay = networkModel.getUploadDelay(SimSettings.GENERIC_EDGE_DEVICE_ID, SimSettings.GENERIC_EDGE_DEVICE_ID, task);
				if (manDelay > 0) {
					networkModel.uploadStarted(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID + 1);
					SimLogger.getInstance().setUploadDelay(task.getCloudletId(), manDelay, NETWORK_DELAY_TYPES.MAN_DELAY);
					schedule(getId(), manDelay, REQUEST_RECEIVED_BY_REMOTE_EDGE_DEVICE, task);
				} else {
					SimLogger.getInstance().rejectedDueToBandwidth(
							task.getCloudletId(),
							CloudSim.clock(),
							SimSettings.VM_TYPES.EDGE_VM.ordinal(),
							NETWORK_DELAY_TYPES.MAN_DELAY);
					reportReSACOOutcome(task, false);
				}
				break;
			}
			case RESPONSE_RECEIVED_BY_EDGE_DEVICE_TO_RELAY_MOBILE_DEVICE: {
				Task task = (Task) ev.getData();
				networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID + 1);

				double delay = networkModel.getDownloadDelay(task.getAssociatedHostId(), task.getMobileDeviceId(), task);

				if (delay > 0) {
					Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(), CloudSim.clock() + delay);
					if (task.getSubmittedLocation().getServingWlanId() == currentLocation.getServingWlanId()) {
						networkModel.downloadStarted(currentLocation, SimSettings.GENERIC_EDGE_DEVICE_ID);
						SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), delay, NETWORK_DELAY_TYPES.WLAN_DELAY);
						schedule(getId(), delay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);
					} else {
						SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
						reportReSACOOutcome(task, false);
					}
				} else {
					SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), NETWORK_DELAY_TYPES.WLAN_DELAY);
					reportReSACOOutcome(task, false);
				}
				break;
			}
			case RESPONSE_RECEIVED_BY_MOBILE_DEVICE: {
				Task task = (Task) ev.getData();

				if (task.getAssociatedDatacenterId() == SimSettings.CLOUD_DATACENTER_ID)
					networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.CLOUD_DATACENTER_ID);
				else
					networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);

				SimLogger.getInstance().taskEnded(task.getCloudletId(), CloudSim.clock());
				reportReSACOOutcome(task, true);
				break;
			}
			default:
				SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - event unknown by this DatacenterBroker. Terminating simulation...");
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

		Task task = createTask(edgeTask);

		Location currentLocation = SimManager.getInstance().getMobilityModel().
				getLocation(task.getMobileDeviceId(), CloudSim.clock());

		task.setSubmittedLocation(currentLocation);

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
		else if (nextHopId == SimSettings.GENERIC_EDGE_DEVICE_ID) {
			delay = networkModel.getUploadDelay(task.getMobileDeviceId(), nextHopId, task);
			vmType = SimSettings.VM_TYPES.EDGE_VM;
			nextEvent = REQUEST_RECEIVED_BY_EDGE_DEVICE;
			delayType = NETWORK_DELAY_TYPES.WLAN_DELAY;
			nextDeviceForNetworkModel = SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		else if (nextHopId == SimSettings.MOBILE_DATACENTER_ID) {
			vmType = VM_TYPES.MOBILE_VM;
			nextEvent = REQUEST_RECEIVED_BY_MOBILE_DEVICE;
		}
		else {
			SimLogger.printLine("Unknown nextHopId! Terminating simulation...");
			System.exit(0);
		}

		if (delay > 0 || nextHopId == SimSettings.MOBILE_DATACENTER_ID) {

			Vm selectedVM = SimManager.getInstance().getEdgeOrchestrator().getVmToOffload(task, nextHopId);

			if (selectedVM != null) {
				task.setAssociatedDatacenterId(nextHopId);
				task.setAssociatedHostId(selectedVM.getHost().getId());
				task.setAssociatedVmId(selectedVM.getId());

				getCloudletList().add(task);
				bindCloudletToVm(task.getCloudletId(), selectedVM.getId());

				SimLogger.getInstance().taskStarted(task.getCloudletId(), CloudSim.clock());

				if (nextHopId != SimSettings.MOBILE_DATACENTER_ID) {
					networkModel.uploadStarted(task.getSubmittedLocation(), nextDeviceForNetworkModel);
					SimLogger.getInstance().setUploadDelay(task.getCloudletId(), delay, delayType);
				}

				schedule(getId(), delay, nextEvent, task);
			} else {
				SimLogger.getInstance().rejectedDueToVMCapacity(task.getCloudletId(), CloudSim.clock(), vmType.ordinal());
				reportReSACOOutcome(task, false);
			}
		}
		else {
			SimLogger.getInstance().rejectedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), vmType.ordinal(), delayType);
			reportReSACOOutcome(task, false);
		}
	}

	private void submitTaskToVm(Task task, SimSettings.VM_TYPES vmType) {
		schedule(getVmsToDatacentersMap().get(task.getVmId()), 0, CloudSimTags.CLOUDLET_SUBMIT, task);

		SimLogger.getInstance().taskAssigned(task.getCloudletId(),
				task.getAssociatedDatacenterId(),
				task.getAssociatedHostId(),
				task.getAssociatedVmId(),
				vmType.ordinal());
	}

	private Task createTask(TaskProperty edgeTask) {
		UtilizationModel utilizationModel = new UtilizationModelFull();
		UtilizationModel utilizationModelCPU = getCpuUtilizationModel();

		Task task = new Task(edgeTask.getMobileDeviceId(), ++taskIdCounter,
				edgeTask.getLength(), edgeTask.getPesNumber(),
				edgeTask.getInputFileSize(), edgeTask.getOutputFileSize(),
				utilizationModelCPU, utilizationModel, utilizationModel);

		task.setUserId(this.getId());
		task.setTaskType(edgeTask.getTaskType());

		if (utilizationModelCPU instanceof CpuUtilizationModel_Custom) {
			((CpuUtilizationModel_Custom) utilizationModelCPU).setTask(task);
		}

		return task;
	}
}
