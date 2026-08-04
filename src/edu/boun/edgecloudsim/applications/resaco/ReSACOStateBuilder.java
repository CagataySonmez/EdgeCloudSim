/*
 * Title:        EdgeCloudSim - ReSACO State Builder
 *
 * Description:
 * Builds the state vector s_t = (L, U, D, mu_d, mu_e1..mu_eN, mu_c,
 * b_wlan, b_man, b_wan) described in Section IV-A-2 of the ReSACO paper,
 * so it can be sent to the Python inference/online-learning bridge
 * (ReSACO/bridge/inference_server.py). Edge slot e holds edge host e's own
 * average VM utilization (matching resaco/env.py's per-server mu_e state,
 * so the trained policy's per-server action choice actually means
 * something at deployment time); if edge_devices.xml defines fewer hosts
 * than RESACO_NUM_EDGE_SLOTS, the leftover slots fall back to the
 * network-wide average (an in-distribution filler; hosts beyond the slot
 * count are invisible to the policy but still reachable through
 * ReSACOEdgeOrchestrator's least-loaded fallback). RESACO_NUM_EDGE_SLOTS
 * must match resaco/config.py's NUM_EDGE_SERVERS for the state vector
 * length to line up with the trained network.
 *
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 */

package edu.boun.edgecloudsim.applications.resaco;

import java.util.List;

import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileVM;
import edu.boun.edgecloudsim.edge_server.EdgeServerManager;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.network.NetworkModel;

public class ReSACOStateBuilder {
	public static final int RESACO_NUM_EDGE_SLOTS = 10;
	/** Must match resaco/config.py's TMAX_SECONDS (network failure delay threshold, Eq. 2/9). */
	public static final double RESACO_TMAX_SECONDS = 5.0;
	/**
	 * Must match resaco/config.py's DELAY_SENSITIVITY_BASE. Rewards reported to
	 * the bridge are scaled by (base + the app's delay_sensitivity), same as the
	 * training environment's reward -- online learning (Algorithm 4) would
	 * otherwise adapt the policy against rewards on a different scale than the
	 * one it was trained on.
	 */
	public static final double RESACO_DELAY_SENSITIVITY_BASE = 0.5;
	/**
	 * Must match resaco/config.py's DELAY_SENSITIVITY_REWARD. false recovers the
	 * paper's Eq. (9) exactly (unweighted -T / -(Tmax+1)); flip both sides
	 * together and retrain -- a policy trained on one reward scale must not be
	 * online-adapted against the other.
	 */
	public static final boolean RESACO_DELAY_SENSITIVITY_REWARD = true;
	/** Column indices in SimSettings.getTaskLookUpTable() (see its JavaDoc). */
	private static final int TASK_PROPERTY_DATA_UPLOAD = 5;
	private static final int TASK_PROPERTY_DATA_DOWNLOAD = 6;
	private static final int TASK_PROPERTY_TASK_LENGTH = 7;
	private static final int TASK_PROPERTY_DELAY_SENSITIVITY = 12;
	private static final int TASK_PROPERTY_MAX_DELAY_REQUIREMENT = 13;

	/*
	 * Industry-direction reward objectives -- every constant below must match
	 * its resaco/config.py counterpart (same names minus the RESACO_ prefix):
	 * the bridge's OUTCOME rewards feed Algorithm 4's online updates, which
	 * must be on the same scale the policy was trained on. Change both sides
	 * or neither, then retrain.
	 */
	public static final boolean RESACO_ENERGY_AWARE_REWARD = true;
	public static final double RESACO_ENERGY_REWARD_WEIGHT = 0.02;
	public static final double RESACO_MOBILE_COMPUTE_POWER_W = 4.0;
	public static final double RESACO_WLAN_TX_POWER_W = 1.3;
	public static final double RESACO_EDGE_VM_POWER_W = 30.0;
	public static final double RESACO_CLOUD_VM_POWER_W = 90.0;
	public static final double RESACO_WAN_TRANSPORT_ENERGY_PER_MBIT = 0.05;
	public static final boolean RESACO_COST_AWARE_REWARD = true;
	public static final double RESACO_COST_REWARD_WEIGHT = 0.05;
	public static final double RESACO_EDGE_COST_PER_CPU_SEC = 3.0;
	public static final double RESACO_CLOUD_COST_PER_CPU_SEC = 1.0;
	public static final double RESACO_WAN_EGRESS_COST_PER_MB = 0.1;
	public static final double RESACO_SLA_PENALTY_WEIGHT = 1.0;

	/**
	 * Seconds-equivalent penalty for the industry-direction objectives
	 * (energy, monetary cost, SLA deadline), subtracted from a successful
	 * task's reward -- mirrors resaco/env.py's _task_energy/_task_cost/
	 * sla_violated terms. Java-side approximations, documented and accepted:
	 * process time is derived as cloudlet length / the tier's live VM MIPS
	 * (deterministic in both simulators), the whole serviceTime remainder is
	 * treated as the radio-transfer window at WLAN TX power (training only
	 * bills the WLAN leg), and a lookup-table max_delay_requirement of 0
	 * (attribute absent from applications*.xml) means "no deadline".
	 */
	public static double industryPenalty(Task task, double serviceTime) {
		int tier = task.getAssociatedDatacenterId();
		double lengthMi = task.getCloudletLength();
		double mips = tierVmMips(tier, task.getMobileDeviceId());
		double processTime = (mips > 0) ? Math.min(lengthMi / mips, serviceTime) : serviceTime;
		double transferTime = Math.max(serviceTime - processTime, 0);
		double dataMbit = (task.getCloudletFileSize() + task.getCloudletOutputSize()) / 1000.0 * 8.0;

		double energy;
		double cost;
		if (tier == SimSettings.MOBILE_DATACENTER_ID) {
			energy = RESACO_MOBILE_COMPUTE_POWER_W * processTime;
			cost = 0;
		} else if (tier == SimSettings.CLOUD_DATACENTER_ID) {
			energy = RESACO_WLAN_TX_POWER_W * transferTime
					+ RESACO_CLOUD_VM_POWER_W * processTime
					+ RESACO_WAN_TRANSPORT_ENERGY_PER_MBIT * dataMbit;
			cost = RESACO_CLOUD_COST_PER_CPU_SEC * processTime
					+ RESACO_WAN_EGRESS_COST_PER_MB * task.getCloudletOutputSize() / 1000.0;
		} else {
			energy = RESACO_WLAN_TX_POWER_W * transferTime + RESACO_EDGE_VM_POWER_W * processTime;
			cost = RESACO_EDGE_COST_PER_CPU_SEC * processTime;
		}

		double penalty = 0;
		if (RESACO_ENERGY_AWARE_REWARD) {
			penalty += RESACO_ENERGY_REWARD_WEIGHT * energy;
		}
		if (RESACO_COST_AWARE_REWARD) {
			penalty += RESACO_COST_REWARD_WEIGHT * cost;
		}
		double deadline = SimSettings.getInstance()
				.getTaskLookUpTable()[task.getTaskType()][TASK_PROPERTY_MAX_DELAY_REQUIREMENT];
		if (RESACO_SLA_PENALTY_WEIGHT > 0 && deadline > 0 && serviceTime > deadline) {
			penalty += RESACO_SLA_PENALTY_WEIGHT * (serviceTime - deadline);
		}
		return penalty;
	}

	/** The named tier's live per-VM MIPS (first VM as the representative --
	 * all VMs within a tier are identical in every config here). */
	private static double tierVmMips(int tier, int mobileDeviceId) {
		try {
			if (tier == SimSettings.MOBILE_DATACENTER_ID) {
				List<MobileVM> vms = SimManager.getInstance().getMobileServerManager().getVmList(mobileDeviceId);
				return (vms == null || vms.isEmpty()) ? 0 : vms.get(0).getMips();
			} else if (tier == SimSettings.CLOUD_DATACENTER_ID) {
				return SimManager.getInstance().getCloudServerManager().getVmList(0).get(0).getMips();
			}
			return SimManager.getInstance().getEdgeServerManager().getVmList(0).get(0).getMips();
		} catch (Exception e) {
			return 0; // industryPenalty falls back to processTime = serviceTime
		}
	}

	/** The reward weight w = base + delay_sensitivity for this task's app type
	 * (1.0 when RESACO_DELAY_SENSITIVITY_REWARD is off, i.e. paper Eq. (9)). */
	public static double delaySensitivityWeight(Task task) {
		if (!RESACO_DELAY_SENSITIVITY_REWARD) {
			return 1.0;
		}
		double delaySensitivity = SimSettings.getInstance()
				.getTaskLookUpTable()[task.getTaskType()][TASK_PROPERTY_DELAY_SENSITIVITY];
		return RESACO_DELAY_SENSITIVITY_BASE + delaySensitivity;
	}

	/**
	 * ReSACOMainApp reuses the same JVM (and the same long-lived bridge
	 * connection/replay buffer) across many scenario runs (device count x
	 * scenario x policy loops), each of which starts a fresh SimManager and
	 * resets its own Task cloudlet-id counter back to 1. Scoping the bridge
	 * request id by the current SimManager's identity prevents cloudlet id
	 * "1" from a new run colliding with an in-flight (never-completed, e.g.
	 * still airborne when the previous scenario's simulation clock hit
	 * STOP_SIMULATION) request id "1" left over from an earlier run.
	 */
	public static String requestIdFor(Task task) {
		return System.identityHashCode(SimManager.getInstance()) + "-" + task.getCloudletId();
	}

	public static double[] buildStateForTask(Task task) {
		return build(task.getCloudletLength(), task.getCloudletFileSize(), task.getCloudletOutputSize(),
				mobileUtilization(task.getMobileDeviceId()), task.getMobileDeviceId());
	}

	/**
	 * State for an outcome report's s_{t+1}: no specific in-flight task is
	 * known, so L/U/D are filled with the reporting task's app-type *means*
	 * from applications.xml -- the expected next task of the device's own
	 * app type, and exactly the distribution the training env samples
	 * around. (A previous version zeroed all three, which training clamps
	 * to >= 1 and therefore never produces -- every online update's
	 * bootstrap target was evaluated out of distribution.)
	 */
	public static double[] buildStateForDevice(int mobileDeviceId, int taskType) {
		double[] taskProps = SimSettings.getInstance().getTaskLookUpTable()[taskType];
		return build(taskProps[TASK_PROPERTY_TASK_LENGTH],
				taskProps[TASK_PROPERTY_DATA_UPLOAD],
				taskProps[TASK_PROPERTY_DATA_DOWNLOAD],
				mobileUtilization(mobileDeviceId), mobileDeviceId);
	}

	/**
	 * The fixed 128 KB (~1 Mbit) task used to probe link delays. Shared
	 * rather than reallocated per probe: the network model only reads its
	 * file size (see ThreeTierNetworkModel.getUploadDelay), CloudSim's
	 * event loop is single-threaded, and this used to allocate one Task
	 * plus three UtilizationModelFull objects on each of the three probes
	 * of every state build -- i.e. 24 throwaway objects per simulated task.
	 */
	private static Task probe;

	static Task probeTask() {
		if (probe == null) {
			UtilizationModelFull full = new UtilizationModelFull();
			probe = new Task(0, 0, 0, 0, 128, 128, full, full, full);
		}
		return probe;
	}

	private static double mobileUtilization(int mobileDeviceId) {
		List<MobileVM> vmArray = SimManager.getInstance().getMobileServerManager().getVmList(mobileDeviceId);
		if (vmArray == null || vmArray.isEmpty()) {
			return 0;
		}
		return vmArray.get(0).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
	}

	/**
	 * Edge host `hostIndex`'s average VM CPU utilization (0-100) -- the
	 * per-host analogue of EdgeServerManager.getAvgUtilization(), matching
	 * resaco/env.py's per-server mu_e state feature. The manager and clock
	 * are passed in because this runs once per edge slot (10x) per state
	 * build, twice per simulated task.
	 */
	private static double edgeHostUtilization(EdgeServerManager edgeManager, double clock, int hostIndex) {
		List<EdgeVM> vmArray = edgeManager.getVmList(hostIndex);
		if (vmArray == null || vmArray.isEmpty()) {
			return 0;
		}
		double total = 0;
		for (int v = 0; v < vmArray.size(); v++) {
			total += vmArray.get(v).getCloudletScheduler().getTotalUtilizationOfCpu(clock);
		}
		return total / vmArray.size();
	}

	/**
	 * Live effective bandwidth (Mbps) of the link from `sourceId` to
	 * `destId`, derived from the network model's own MM1 queue state by
	 * probing the upload delay of a dummy ~1 Mbit task (128 KB) -- the same
	 * trick ReSACOEdgeOrchestrator.fallbackHeuristic() uses. A congested
	 * link (more concurrent clients) yields a longer probe delay and thus a
	 * lower effective bandwidth, finally making the three bandwidth state
	 * features *live* at deployment: previously they were the static config
	 * capacities, identical on every request, while training taught the
	 * policy that bandwidth variation is informative. Returns are clamped
	 * to [0, staticCapMbps] so values stay inside the trained range; a
	 * non-positive probe delay means the link is saturated/rejecting -> 0.
	 */
	private static double liveBandwidthMbps(NetworkModel networkModel, int sourceId, int destId,
			double staticCapMbps) {
		double delay = networkModel.getUploadDelay(sourceId, destId, probeTask());
		if (delay <= 0) {
			return 0;
		}
		double mbps = (128 * 8 / 1000.0) / delay; // 128 KB ~= 1.024 Mbit probe
		return Math.min(mbps, staticCapMbps);
	}

	private static double[] build(double length, double upload, double download,
			double mobileUtilization, int mobileDeviceId) {
		SimManager manager = SimManager.getInstance();
		EdgeServerManager edgeManager = manager.getEdgeServerManager();
		NetworkModel networkModel = manager.getNetworkModel();
		SimSettings settings = SimSettings.getInstance();
		int numEdgeHosts = settings.getNumOfEdgeHosts();
		double clock = CloudSim.clock();
		double cloudUtilization = manager.getCloudServerManager().getAvgUtilization();

		double[] state = new double[4 + RESACO_NUM_EDGE_SLOTS + 1 + 3];
		int i = 0;
		state[i++] = length;
		state[i++] = upload;
		state[i++] = download;
		state[i++] = mobileUtilization;
		// The network-wide average is only the filler for slots beyond the
		// hosts edge_devices.xml defines, so compute it lazily -- with the
		// paper's ten hosts it is never needed, and it walks every host's
		// every VM, doubling this method's utilization work when eager.
		double avgEdgeUtilization = Double.NaN;
		for (int e = 0; e < RESACO_NUM_EDGE_SLOTS; e++) {
			if (e < numEdgeHosts) {
				state[i++] = edgeHostUtilization(edgeManager, clock, e);
			} else {
				if (Double.isNaN(avgEdgeUtilization)) {
					avgEdgeUtilization = edgeManager.getAvgUtilization();
				}
				state[i++] = avgEdgeUtilization;
			}
		}
		state[i++] = cloudUtilization;
		// SimSettings.getXxxBandwidth()'s JavaDoc claims "Mbps unit" but that's
		// stale -- the underlying BANDWITH_XXX fields are the config file's Mbps
		// value pre-multiplied by 1000 for internal Kbps-based delay math (see
		// SimSettings.java's loadSimulationParameters() and
		// ThreeTierNetworkModel.calculateMM1()'s explicit /*Kbps*/ comment).
		// resaco/config.py's *_BANDWIDTH_MBPS constants (and everything trained
		// against them, including resaco/normalize.py's fixed scale factors) are
		// in Mbps, so the static caps below divide by 1000; the live values are
		// probed from the network model's MM1 state (see liveBandwidthMbps).
		double wlanCap = settings.getWlanBandwidth() / 1000.0;
		double manCap = settings.getManBandwidth() / 1000.0;
		double wanCap = settings.getWanBandwidth() / 1000.0;
		state[i++] = liveBandwidthMbps(networkModel, mobileDeviceId,
				SimSettings.GENERIC_EDGE_DEVICE_ID, wlanCap);
		// the MAN link only exists between edge hosts; probe it host-to-host
		state[i++] = (manCap > 0)
				? liveBandwidthMbps(networkModel, SimSettings.GENERIC_EDGE_DEVICE_ID,
						SimSettings.GENERIC_EDGE_DEVICE_ID, manCap)
				: 0;
		state[i++] = liveBandwidthMbps(networkModel, mobileDeviceId,
				SimSettings.CLOUD_DATACENTER_ID, wanCap);
		return state;
	}
}
