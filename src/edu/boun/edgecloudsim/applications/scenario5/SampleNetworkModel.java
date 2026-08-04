package edu.boun.edgecloudsim.applications.scenario5;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.utils.Location;;

public class SampleNetworkModel extends NetworkModel {
	private double poissonMean; //seconds
	private double avgTaskInputSize; //bytes
	private double avgTaskOutputSize; //bytes
	
	public SampleNetworkModel(int _numberOfMobileDevices, String _simScenario) {
		super(_numberOfMobileDevices, _simScenario);
	}
	
	@Override
	public void initialize() {
		poissonMean=0;
		avgTaskInputSize=0;
		avgTaskOutputSize=0;

		//Calculate interarrival time and task sizes
		double numOfTaskType = 0;
		SimSettings SS = SimSettings.getInstance();
		for (int i=0; i<SimSettings.getInstance().getTaskLookUpTable().length; i++) {
			double weight = SS.getTaskLookUpTable()[i][0]/(double)100;
			if(weight != 0) {
				poissonMean += (SS.getTaskLookUpTable()[i][2])*weight;
				avgTaskInputSize += SS.getTaskLookUpTable()[i][5]*weight;
				avgTaskOutputSize += SS.getTaskLookUpTable()[i][6]*weight;

				numOfTaskType++;
			}
		}

		poissonMean = poissonMean/numOfTaskType;
		avgTaskInputSize = avgTaskInputSize/numOfTaskType;
		avgTaskOutputSize = avgTaskOutputSize/numOfTaskType;
	}
	
	private double calculateMM1(double propagationDelay, int bandwidth /*Kbps*/, double PoissonMean, double avgTaskSize /*KB*/, int deviceCount){
		double Bps=0, mu=0, lamda=0;

		avgTaskSize = avgTaskSize * (double)1000; //convert from KB to Byte

		Bps = bandwidth * (double)1000 / (double)8; //convert from Kbps to Byte per seconds
		lamda = ((double)1/(double)PoissonMean)*(double)deviceCount; //task per seconds
		mu = Bps / avgTaskSize ; //task per seconds
		double result = (double)1 / (mu-lamda);

		result += propagationDelay;

		return result;
	}
	
	private double calculateMM2(double propagationDelay, int bandwidth /*Kbps*/, double PoissonMean, double avgTaskSize /*KB*/, int deviceCount){
		double Bps=0, mu=0, lamda=0;
		double two = 2;

		avgTaskSize = avgTaskSize * (double)1000; //convert from KB to Byte

		Bps = bandwidth * (double)1000 / (double)8; //convert from Kbps to Byte per seconds
		lamda = ((double)1/(double)PoissonMean)*(double)deviceCount; //task per seconds
		mu = Bps / avgTaskSize ; //task per seconds
		double result = ((double)4*mu) / ((two*mu-lamda) * (two*mu+lamda));

		result += propagationDelay;

		return result;
	}


	@Override
	public double getUploadDelay(int sourceDeviceId, int destDeviceId, Task task) {
		double result = 0;

		if(simScenario.equals("SCENARIO1")) {
			result = calculateMM2(0,
					SimSettings.getInstance().getWlanBandwidth(),
					poissonMean,
					avgTaskOutputSize,
					numberOfMobileDevices);
		}
		else if(simScenario.equals("SCENARIO2")) {
			result = calculateMM2(0,
					SimSettings.getInstance().getWlanBandwidth(),
					poissonMean,
					avgTaskOutputSize,
					numberOfMobileDevices);
		}
		else {
			result = calculateMM1(0,
					SimSettings.getInstance().getWlanBandwidth(),
					poissonMean,
					avgTaskOutputSize,
					numberOfMobileDevices/2);
		}
		
		return result;
	}

	@Override
	public double getDownloadDelay(int sourceDeviceId, int destDeviceId, Task task) {
		return getUploadDelay(sourceDeviceId, destDeviceId, task);
	}

	@Override
	public void uploadStarted(Location accessPointLocation, int destDeviceId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void uploadFinished(Location accessPointLocation, int destDeviceId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void downloadStarted(Location accessPointLocation, int sourceDeviceId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void downloadFinished(Location accessPointLocation, int sourceDeviceId) {
		// TODO Auto-generated method stub
		
	}
}
