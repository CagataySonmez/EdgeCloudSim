/*
 * Title:        EdgeCloudSim - ReSACO Edge Orchestrator
 *
 * Description:
 * Offloading decisions are delegated to a trained policy served by the
 * Python bridge (ReSACO/bridge/inference_server.py) over
 * ReSACOBridgeClient. Which policy is up to the "policy" (orchestrator
 * policy) string passed in from the config file -- RESACO (the
 * Reptile-meta-trained SAC, Deployment Phase / Algorithm 4),
 * SAC_BASELINE, DDPG_BASELINE, A2C_BASELINE or A3C_BASELINE (see
 * ReSACO/scripts/train_baselines.py) -- so listing all five as
 * orchestrator_policies runs all five through the same real CloudSim
 * simulation for a like-for-like comparison. The bridge's action space is
 * {0=device, 1..N=edge, N+1=cloud}; an edge action e routes to edge host
 * e-1 specifically (matching resaco/env.py's per-server semantics -- the
 * per-slot utilizations ReSACOStateBuilder now sends make that choice
 * informed), with getVmToOffload() preferring the chosen host's
 * least-loaded VM and only widening to the global least-loaded search if
 * that host has no VM with capacity (or the host index exceeds what
 * edge_devices.xml defines). If the bridge or the requested algorithm is
 * unavailable, falls back to a static EDGE_PRIORITY-style heuristic so
 * the simulation never crashes.
 *
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 */

package edu.boun.edgecloudsim.applications.resaco;

import java.util.List;

/*CLOUDSIM*/
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;

/*CLOUD*/
import edu.boun.edgecloudsim.cloud_server.CloudVM;

/*EDGE*/
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.edge_client.CpuUtilizationModel_Custom;
import edu.boun.edgecloudsim.edge_client.Task;

/*MOBILE*/
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileVM;

/*UTIL*/
import edu.boun.edgecloudsim.utils.SimLogger;

public class ReSACOEdgeOrchestrator extends EdgeOrchestrator {

	private int numberOfHost; // used by load balancer

	/**
	 * Edge host chosen by the policy's last getDeviceToOffload() call (-1 =
	 * no specific host, i.e. non-edge action or fallback heuristic), consumed
	 * by the immediately-following getVmToOffload() call for the same task.
	 * A plain field is safe here because CloudSim's event loop is
	 * single-threaded and MobileDeviceManager.submitTask() always calls the
	 * two back-to-back with nothing in between.
	 */
	private int preferredEdgeHost = -1;

	public ReSACOEdgeOrchestrator(String _policy, String _simScenario) {
		super(_policy, _simScenario);
	}

	@Override
	public void initialize() {
		numberOfHost = SimSettings.getInstance().getNumOfEdgeHosts();
		// Each simulation run is a new deployment scenario S_new: per
		// Algorithm 4 line 1, adaptation starts from the meta-trained
		// theta*, not from wherever the previous run's online updates left
		// theta_adapt. Best-effort -- harmless if the bridge is down.
		ReSACOBridgeClient.getInstance().reset(policy);
	}

	@Override
	public int getDeviceToOffload(Task task) {
		int result;

		double[] state = ReSACOStateBuilder.buildStateForTask(task);
		int action = ReSACOBridgeClient.getInstance().selectAction(policy, ReSACOStateBuilder.requestIdFor(task), state);

		preferredEdgeHost = -1;
		if (action == ReSACOBridgeClient.NO_ACTION) {
			result = fallbackHeuristic(task);
		} else if (action == 0) {
			result = SimSettings.MOBILE_DATACENTER_ID;
		} else if (action >= 1 && action <= ReSACOStateBuilder.RESACO_NUM_EDGE_SLOTS) {
			result = SimSettings.GENERIC_EDGE_DEVICE_ID;
			// Edge action e means edge host e-1 (whose own utilization sits in
			// state slot e) -- honored by getVmToOffload() below. A slot index
			// beyond the hosts edge_devices.xml actually defines carries no
			// per-host meaning, so it stays a plain least-loaded edge dispatch.
			if (action - 1 < numberOfHost) {
				preferredEdgeHost = action - 1;
			}
		} else {
			result = SimSettings.CLOUD_DATACENTER_ID;
		}

		return result;
	}

	/** Least-loaded VM with capacity on one specific edge host, or null if none fits. */
	private Vm leastLoadedEdgeVmOnHost(Task task, int hostIndex) {
		Vm selectedVM = null;
		double selectedVmCapacity = 0;
		List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex);
		for (int vmIndex = 0; vmIndex < vmArray.size(); vmIndex++) {
			double requiredCapacity = ((CpuUtilizationModel_Custom) task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
			double targetVmCapacity = (double) 100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
			if (requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity) {
				selectedVM = vmArray.get(vmIndex);
				selectedVmCapacity = targetVmCapacity;
			}
		}
		return selectedVM;
	}

	/** EDGE_PRIORITY-style heuristic used only while the ReSACO bridge is unreachable. */
	private int fallbackHeuristic(Task task) {
		double wanDelay = SimManager.getInstance().getNetworkModel().getUploadDelay(task.getMobileDeviceId(),
				SimSettings.CLOUD_DATACENTER_ID, ReSACOStateBuilder.probeTask() /* 1 Mbit */);
		double wanBW = (wanDelay == 0) ? 0 : (1 / wanDelay); /* Mbps */
		double edgeUtilization = SimManager.getInstance().getEdgeServerManager().getAvgUtilization();

		if (wanBW > 6) {
			return (edgeUtilization > 90) ? SimSettings.CLOUD_DATACENTER_ID : SimSettings.GENERIC_EDGE_DEVICE_ID;
		} else if (wanBW > 3) {
			if (edgeUtilization > 90) return SimSettings.CLOUD_DATACENTER_ID;
			if (edgeUtilization < 20) return SimSettings.MOBILE_DATACENTER_ID;
			return SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		return SimSettings.MOBILE_DATACENTER_ID;
	}

	@Override
	public Vm getVmToOffload(Task task, int deviceId) {
		Vm selectedVM = null;

		if (deviceId == SimSettings.MOBILE_DATACENTER_ID) {
			List<MobileVM> vmArray = SimManager.getInstance().getMobileServerManager().getVmList(task.getMobileDeviceId());
			double requiredCapacity = ((CpuUtilizationModel_Custom) task.getUtilizationModelCpu()).predictUtilization(vmArray.get(0).getVmType());
			double targetVmCapacity = (double) 100 - vmArray.get(0).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());

			if (requiredCapacity <= targetVmCapacity)
				selectedVM = vmArray.get(0);
		}

		else if (deviceId == SimSettings.GENERIC_EDGE_DEVICE_ID) {
			// Prefer the specific edge host the policy chose (its action index
			// maps 1:1 to the per-host utilization slot it observed); consume
			// the preference so it can never leak onto a later task.
			int chosenHost = preferredEdgeHost;
			preferredEdgeHost = -1;

			if (chosenHost >= 0) {
				selectedVM = leastLoadedEdgeVmOnHost(task, chosenHost);
			}
			if (selectedVM == null) {
				// No host preference (fallback heuristic) or the chosen host has
				// no VM with capacity left -- widen to the least-loaded VM across
				// all edge hosts (including hosts beyond the policy's slot count)
				// rather than failing a task the edge tier as a whole could serve.
				double selectedVmCapacity = 0; //start with min value
				for (int hostIndex = 0; hostIndex < numberOfHost; hostIndex++) {
					List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex);
					for (int vmIndex = 0; vmIndex < vmArray.size(); vmIndex++) {
						double requiredCapacity = ((CpuUtilizationModel_Custom) task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
						double targetVmCapacity = (double) 100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
						if (requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity) {
							selectedVM = vmArray.get(vmIndex);
							selectedVmCapacity = targetVmCapacity;
						}
					}
				}
			}
		}

		else if (deviceId == SimSettings.CLOUD_DATACENTER_ID) {
			// Select VM on cloud devices via Least Loaded algorithm!
			double selectedVmCapacity = 0; // start with min value
			List<Host> list = SimManager.getInstance().getCloudServerManager().getDatacenter().getHostList();
			for (int hostIndex = 0; hostIndex < list.size(); hostIndex++) {
				List<CloudVM> vmArray = SimManager.getInstance().getCloudServerManager().getVmList(hostIndex);
				for (int vmIndex = 0; vmIndex < vmArray.size(); vmIndex++) {
					double requiredCapacity = ((CpuUtilizationModel_Custom) task.getUtilizationModelCpu())
							.predictUtilization(vmArray.get(vmIndex).getVmType());
					double targetVmCapacity = (double) 100
							- vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					if (requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity) {
						selectedVM = vmArray.get(vmIndex);
						selectedVmCapacity = targetVmCapacity;
					}
				}
			}
		}

		else {
			SimLogger.printLine("Unknown device id! The simulation has been terminated.");
			System.exit(0);
		}

		return selectedVM;
	}

	@Override
	public void processEvent(SimEvent arg0) {
		// Nothing to do!
	}

	@Override
	public void shutdownEntity() {
		// Nothing to do!
	}

	@Override
	public void startEntity() {
		// Nothing to do!
	}

}
