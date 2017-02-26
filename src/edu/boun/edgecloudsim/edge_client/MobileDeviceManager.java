/*
 * Title:        EdgeCloudSim - Mobile Device Manager
 * 
 * Description: 
 * MobileDeviceManager is responsible for submitting the tasks to the related
 * device by using the Edge Orchestrator. It also takes proper actions 
 * when the execution of the tasks are finished.
 * By default, MobileDeviceManager sends tasks to the edge servers or
 * cloud servers. If you want to use different topology, for example
 * MAN edge server, you should modify the flow defined in this class.
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_client;

import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.utils.EdgeTask;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;


public class MobileDeviceManager extends DatacenterBroker {
	private static final int BASE = 100000; //start from base in order not to conflict cloudsim tag!
	private static final int REQUEST_RECEIVED_BY_CLOUD = BASE + 1;
	private static final int REQUEST_PROCESSED_BY_CLOUD = BASE + 2;
	private static final int REQUEST_RECIVED_BY_EDGE_DEVICE = BASE + 3;
	private static final int RESPONSE_RECEIVED_BY_MOBILE_DEVICE = BASE + 4;
	private static final int REQUEST_RECIVED_BY_EDGE_ORCHESTRATOR = BASE + 5;
	private int taskIdCounter=0;
	
	public MobileDeviceManager() throws Exception {
		super("Global_Broker");
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
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		Task task = (Task) ev.getData();

		Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock());
		if(task.getSubmittedLocation().equals(currentLocation))
		{
			//SimLogger.printLine(CloudSim.clock() + ": " + getName() + ": Cloudlet " + task.getCloudletId() + " received");
			double WlanDelay = networkModel.getDownloadDelay(task.getAssociatedHostId(), task.getMobileDeviceId());
			if(WlanDelay > 0)
			{
				schedule(getId(), WlanDelay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);
				SimLogger.getInstance().downloadStarted(task.getCloudletId(), WlanDelay);
			}
			else
			{
				SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock());
			}
		}
		else
		{
			//SimLogger.printLine("task cannot be finished due to mobility of user!");
			//SimLogger.printLine("device: " +task.getMobileDeviceId()+" - submitted " + task.getSubmissionTime() + " @ " + task.getSubmittedLocation().getXPos() + " handled " + CloudSim.clock() + " @ " + currentLocation.getXPos());
			SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
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
			case REQUEST_RECEIVED_BY_CLOUD:
			{
				Task task = (Task) ev.getData();
				
				Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock());
				
				task.setSubmittedLocation(currentLocation);
				task.setAssociatedHostId(SimSettings.CLOUD_HOST_ID);
				
				SimLogger.getInstance().uploaded(task.getCloudletId(),
						SimSettings.CLOUD_DATACENTER_ID,
						SimSettings.CLOUD_HOST_ID,
						SimSettings.CLOUD_VM_ID,
						SimSettings.VM_TYPES.CLOUD_VM.ordinal());
				
				//calculate computational delay in cloud
				double ComputationDelay = (double)task.getCloudletLength() / (double)SimSettings.getInstance().getMipsForCloud();
				
				schedule(getId(), ComputationDelay, REQUEST_PROCESSED_BY_CLOUD, task);
				
				break;
			}
			case REQUEST_PROCESSED_BY_CLOUD:
			{
				Task task = (Task) ev.getData();
				
				Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock());
				
				if(task.getSubmittedLocation().equals(currentLocation))
				{
					//SimLogger.printLine(CloudSim.clock() + ": " + getName() + ": Cloudlet " + task.getCloudletId() + " received");
					double WanDelay = networkModel.getDownloadDelay(SimSettings.CLOUD_DATACENTER_ID, task.getMobileDeviceId());
					if(WanDelay > 0)
					{
						schedule(getId(), WanDelay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);
						SimLogger.getInstance().downloadStarted(task.getCloudletId(), WanDelay);
					}
					else
					{
						SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock());
					}
				}
				else
				{
					//SimLogger.printLine("task cannot be finished due to mobility of user!");
					//SimLogger.printLine("device: " +task.getMobileDeviceId()+" - submitted " + task.getSubmissionTime() + " @ " + task.getSubmittedLocation().getXPos() + " handled " + CloudSim.clock() + " @ " + currentLocation.getXPos());
					SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
				}
				
				break;
			}
			case REQUEST_RECIVED_BY_EDGE_ORCHESTRATOR:
			{
				Task task = (Task) ev.getData();
				double internalDelay = networkModel.getDownloadDelay(
						SimSettings.EDGE_ORCHESTRATOR_ID,
						SimSettings.GENERIC_EDGE_DEVICE_ID);

				submitTaskToEdgeDevice(task,internalDelay);

				break;
			}
			case REQUEST_RECIVED_BY_EDGE_DEVICE:
			{
				Task task = (Task) ev.getData();
				submitTaskToEdgeDevice(task,0);
				
				break;
			}
			case RESPONSE_RECEIVED_BY_MOBILE_DEVICE:
			{
				Cloudlet cloudlet = (Cloudlet) ev.getData();
				//SimLogger.printLine(CloudSim.clock() + ": " + getName() + ": Cloudlet " + cloudlet.getCloudletId() + " is received");
				SimLogger.getInstance().downloaded(cloudlet.getCloudletId(), CloudSim.clock());
				break;
			}
			default:
				SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - event unknown by this DatacenterBroker. Terminating simulation...");
				System.exit(0);
				break;
		}
	}
	
	public void submitTaskToEdgeDevice(Task task, double delay) {
		//select a VM
		EdgeVM selectedVM = SimManager.getInstance().getEdgeOrchestrator().getVmToOffload(task);
		
		if(selectedVM != null){
			Location currentLocation = SimManager.getInstance().getMobilityModel().
					getLocation(task.getMobileDeviceId(),CloudSim.clock());
			
			//save task info
			task.setSubmittedLocation(currentLocation);
			task.setAssociatedHostId(selectedVM.getHost().getId());
			
			//bind task to related VM
			getCloudletList().add(task);
			bindCloudletToVm(task.getCloudletId(),selectedVM.getId());
			
			//SimLogger.printLine(CloudSim.clock() + ": Cloudlet#" + task.getCloudletId() + " is submitted to VM#" + task.getVmId());
			schedule(getVmsToDatacentersMap().get(task.getVmId()), delay, CloudSimTags.CLOUDLET_SUBMIT, task);
			
			SimLogger.getInstance().uploaded(task.getCloudletId(),
					selectedVM.getHost().getDatacenter().getId(),
					selectedVM.getHost().getId(),
					selectedVM.getId(),
					selectedVM.getVmType().ordinal());
		}
		else{
			//SimLogger.printLine("Task #" + task.getCloudletId() + " cannot assign to any VM");
			SimLogger.getInstance().rejectedDueToVMCapacity(task.getCloudletId(), CloudSim.clock());
		}
	}
	
	public void submitTask(EdgeTask edgeTask) {
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		
		//create a task
		Task task = createTask(edgeTask);

		//add related task to log list
		SimLogger.getInstance().addLog(CloudSim.clock(),
				task.getCloudletId(),
				task.getTaskType().ordinal(),
				(int)task.getCloudletLength(),
				(int)task.getCloudletFileSize(),
				(int)task.getCloudletOutputSize());

		int nextHopId = SimManager.getInstance().getEdgeOrchestrator().getDeviceToOffload(task);
		
		if(nextHopId == SimSettings.CLOUD_DATACENTER_ID){
			double WanDelay = networkModel.getUploadDelay(task.getMobileDeviceId(), nextHopId);
			
			if(WanDelay>0){
				schedule(getId(), WanDelay, REQUEST_RECEIVED_BY_CLOUD, task);
				SimLogger.getInstance().uploadStarted(task.getCloudletId(),WanDelay);
			}
			else
			{
				//SimLogger.printLine("Task #" + task.getCloudletId() + " cannot assign to any VM");
				SimLogger.getInstance().rejectedDueToBandwidth(task.getCloudletId(), CloudSim.clock());
			}
		}
		else if(nextHopId == SimSettings.EDGE_ORCHESTRATOR_ID){
			double WlanDelay = networkModel.getUploadDelay(task.getMobileDeviceId(), nextHopId);
			
			if(WlanDelay > 0){
				schedule(getId(), WlanDelay, REQUEST_RECIVED_BY_EDGE_ORCHESTRATOR, task);
				SimLogger.getInstance().uploadStarted(task.getCloudletId(),WlanDelay);
			}
			else {
				SimLogger.getInstance().rejectedDueToBandwidth(task.getCloudletId(), CloudSim.clock());
			}
		}
		else if(nextHopId == SimSettings.GENERIC_EDGE_DEVICE_ID) {
			double WlanDelay = networkModel.getUploadDelay(task.getMobileDeviceId(), nextHopId);
			
			if(WlanDelay > 0){
				schedule(getId(), WlanDelay, REQUEST_RECIVED_BY_EDGE_DEVICE, task);
				SimLogger.getInstance().uploadStarted(task.getCloudletId(),WlanDelay);
			}
			else {
				SimLogger.getInstance().rejectedDueToBandwidth(task.getCloudletId(), CloudSim.clock());
			}
		}
		else {
			SimLogger.printLine("Unknown nextHopId! Terminating simulation...");
			System.exit(0);
		}
	}
	
	public Task createTask(EdgeTask edgeTask){
		UtilizationModel utilizationModel = new UtilizationModelFull(); /*UtilizationModelStochastic*/
		UtilizationModel utilizationModelCPU = SimManager.getInstance().getScenarioFactory().getCpuUtilizationModel(edgeTask.taskType);

		Task task = new Task(edgeTask.mobileDeviceId, ++taskIdCounter,
				edgeTask.length, edgeTask.pesNumber,
				edgeTask.inputFileSize, edgeTask.outputFileSize,
				utilizationModelCPU, utilizationModel, utilizationModel);
		
		//set the owner of this task
		task.setUserId(this.getId());
		task.setTaskType(edgeTask.taskType);
		
		return task;
	}

	public void taskEnded(){
		clearDatacenters();
		finishExecution();
	}
}
