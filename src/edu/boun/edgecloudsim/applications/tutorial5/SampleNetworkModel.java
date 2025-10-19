package edu.boun.edgecloudsim.applications.tutorial5;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.utils.Location;;

// Simplified analytical network model for tutorial5:
// - Uses closed-form M/M/1 (single server) or M/M/2 (two parallel servers) queueing delay
//   plus (optional) propagation component (here 0) to estimate WLAN transfer latency.
// - Upload and download delays assumed symmetric -> download delegates to upload.
// - Input parameters (mean inter-arrival and average task sizes) are derived as
//   weighted averages from task lookup table usage percentages.
// - Device population per datacenter approximates offered load: λ_total = (1/poissonMean) * (#mobiles / #datacenters)
// - Stability not explicitly guarded; if system utilization ρ approaches 1,
//   formulas can explode (would be a place to clamp or return 0 to indicate congestion).

public class SampleNetworkModel extends NetworkModel {
	private double poissonMean;        // Mean inter-arrival time (seconds) per task type (weighted)
	private double avgTaskInputSize;   // Average input size (KB) used for modeling (after weighting)
	private double avgTaskOutputSize;  // Average output size (KB) used for modeling (after weighting)
	
	public SampleNetworkModel(int _numberOfMobileDevices, String _simScenario) {
		super(_numberOfMobileDevices, _simScenario);
	}
	
	@Override
	public void initialize() {
		poissonMean=0;
		avgTaskInputSize=0;
		avgTaskOutputSize=0;

		// Aggregate weighted metrics across task types (skip zero-weight entries)
		// weight = usagePercentage / 100
		double numOfTaskType = 0;
		SimSettings SS = SimSettings.getInstance();
		for (int i=0; i<SimSettings.getInstance().getTaskLookUpTable().length; i++) {
			double weight = SS.getTaskLookUpTable()[i][0]/(double)100;
			if(weight != 0) {
				// Inter-arrival (seconds), input/output sizes (KB) all weighted
				poissonMean += (SS.getTaskLookUpTable()[i][2])*weight;
				avgTaskInputSize += SS.getTaskLookUpTable()[i][5]*weight;
				avgTaskOutputSize += SS.getTaskLookUpTable()[i][6]*weight;
				numOfTaskType++;
			}
		}

		// Normalize by number of contributing task types
		poissonMean = poissonMean/numOfTaskType;
		avgTaskInputSize = avgTaskInputSize/numOfTaskType;
		avgTaskOutputSize = avgTaskOutputSize/numOfTaskType;
	}
	
	// M/M/1 expected system time: 1 / (μ - λ) + propagation.
	// λ = (1/PoissonMean)*deviceCount  (tasks/s)
	// μ = linkCapacity(Bps) / avgTaskSize(Bytes)
	// Units:
	//   bandwidth Kbps -> Bps
	//   avgTaskSize KB -> Bytes
	// NOTE: No explicit check for μ <= λ; a negative/large result would imply instability.
	private double calculateMM1(double propagationDelay, int bandwidth /*Kbps*/, double PoissonMean, double avgTaskSize /*KB*/, int deviceCount){
		double Bps=0, mu=0, lamda=0;
		avgTaskSize = avgTaskSize * (double)1000; // KB -> Bytes
		Bps = bandwidth * (double)1000 / (double)8; // Kbps -> Bytes/sec
		lamda = ((double)1/(double)PoissonMean)*(double)deviceCount;
		mu = Bps / avgTaskSize;
		double result = (double)1 / (mu-lamda);
		result += propagationDelay;
		return result;
	}
	
	// M/M/2 expected system time: 4μ / ((2μ - λ)(2μ + λ)) + propagation.
	// Same unit conversions and caveats apply as M/M/1.
	private double calculateMM2(double propagationDelay, int bandwidth /*Kbps*/, double PoissonMean, double avgTaskSize /*KB*/, int deviceCount){
		double Bps=0, mu=0, lamda=0;
		double four = 4, two = 2;
		avgTaskSize = avgTaskSize * (double)1000; // KB -> Bytes
		Bps = bandwidth * (double)1000 / (double)8; // Kbps -> Bytes/sec
		lamda = ((double)1/(double)PoissonMean)*(double)deviceCount;
		mu = Bps / avgTaskSize;
		double result = (four*mu) / ((two*mu-lamda) * (two*mu+lamda));
		result += propagationDelay;
		return result;
	}

	@Override
	public double getUploadDelay(int sourceDeviceId, int destDeviceId, Task task) {
		double result = 0;
		int numDatacenter = SimSettings.getInstance().getNumOfEdgeDatacenters();

		// Scenario toggle: SCENARIO2 uses two-server (M/M/2) approximation; others use single-server (M/M/1)
		if(simScenario.equals("SCENARIO2")) {
			result = calculateMM2(0,
					SimSettings.getInstance().getWlanBandwidth(),
					poissonMean,
					avgTaskOutputSize,
					numberOfMobileDevices/numDatacenter);
		}
		else {
			result = calculateMM1(0,
					SimSettings.getInstance().getWlanBandwidth(),
					poissonMean,
					avgTaskOutputSize,
					numberOfMobileDevices/numDatacenter);
		}
		return result;
	}

	@Override
	public double getDownloadDelay(int sourceDeviceId, int destDeviceId, Task task) {
		// Symmetric assumption: reuse upload delay model
		return getUploadDelay(sourceDeviceId, destDeviceId, task);
	}

	@Override
	public void uploadStarted(Location accessPointLocation, int destDeviceId) {
		// No contention tracking in this simplified model (stub)
	}

	@Override
	public void uploadFinished(Location accessPointLocation, int destDeviceId) {
		// No-op
	}

	@Override
	public void downloadStarted(Location accessPointLocation, int sourceDeviceId) {
		// No-op
	}

	@Override
	public void downloadFinished(Location accessPointLocation, int sourceDeviceId) {
		// No-op
	}
}
