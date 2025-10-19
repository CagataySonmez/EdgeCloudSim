/*
 * Title:        EdgeCloudSim - Task
 * 
 * Description: 
 * Task adds app type, task submission location, mobile device id and host id
 * information to CloudSim's Cloudlet class.
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_client;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.utils.Location;

/**
 * Task extends CloudSim's Cloudlet class to provide EdgeCloudSim-specific task functionality.
 * Adds mobile device context, location tracking, and resource assignment information
 * for edge computing task management and orchestration.
 */
public class Task extends Cloudlet {
	private Location submittedLocation;
	private double creationTime;
	private int type;
	private int mobileDeviceId;
	private int hostIndex;
	private int vmIndex;
	private int datacenterId;

	/**
	 * Constructor for Task with specified parameters.
	 * 
	 * @param _mobileDeviceId ID of the mobile device that generated this task
	 * @param cloudletId Unique identifier for this task/cloudlet
	 * @param cloudletLength Processing length required in Million Instructions (MI)
	 * @param pesNumber Number of processing elements required
	 * @param cloudletFileSize Input data size in bytes
	 * @param cloudletOutputSize Output data size in bytes
	 * @param utilizationModelCpu CPU utilization model for this task
	 * @param utilizationModelRam RAM utilization model for this task
	 * @param utilizationModelBw Bandwidth utilization model for this task
	 */
	public Task(int _mobileDeviceId, int cloudletId, long cloudletLength, int pesNumber,
			long cloudletFileSize, long cloudletOutputSize,
			UtilizationModel utilizationModelCpu,
			UtilizationModel utilizationModelRam,
			UtilizationModel utilizationModelBw) {
		super(cloudletId, cloudletLength, pesNumber, cloudletFileSize,
				cloudletOutputSize, utilizationModelCpu, utilizationModelRam,
				utilizationModelBw);
		
		mobileDeviceId = _mobileDeviceId;
		creationTime = CloudSim.clock();
	}

	
	/**
	 * Sets the location where this task was submitted.
	 * @param _submittedLocation Geographic location of task submission
	 */
	public void setSubmittedLocation(Location _submittedLocation){
		submittedLocation =_submittedLocation;
	}

	/**
	 * Associates this task with a specific datacenter for execution.
	 * @param _datacenterId ID of the datacenter (cloud, edge, or mobile)
	 */
	public void setAssociatedDatacenterId(int _datacenterId){
		datacenterId=_datacenterId;
	}
	
	/**
	 * Associates this task with a specific host for execution.
	 * @param _hostIndex ID of the host where this task will be executed
	 */
	public void setAssociatedHostId(int _hostIndex){
		hostIndex=_hostIndex;
	}

	/**
	 * Associates this task with a specific VM for execution.
	 * @param _vmIndex ID of the VM where this task will be executed
	 */
	public void setAssociatedVmId(int _vmIndex){
		vmIndex=_vmIndex;
	}
	
	/**
	 * Sets the application type of this task.
	 * @param _type Task type identifier for application classification
	 */
	public void setTaskType(int _type){
		type=_type;
	}

	/**
	 * Gets the ID of the mobile device that generated this task.
	 * @return Mobile device identifier
	 */
	public int getMobileDeviceId(){
		return mobileDeviceId;
	}
	
	/**
	 * Gets the location where this task was submitted.
	 * @return Geographic location of task submission
	 */
	public Location getSubmittedLocation(){
		return submittedLocation;
	}
	
	/**
	 * Gets the datacenter associated with this task's execution.
	 * @return Datacenter ID where task is being processed
	 */
	public int getAssociatedDatacenterId(){
		return datacenterId;
	}
	
	/**
	 * Gets the host associated with this task's execution.
	 * @return Host ID where task is being processed
	 */
	public int getAssociatedHostId(){
		return hostIndex;
	}

	/**
	 * Gets the VM associated with this task's execution.
	 * @return VM ID where task is being processed
	 */
	public int getAssociatedVmId(){
		return vmIndex;
	}
	
	/**
	 * Gets the application type of this task.
	 * @return Task type identifier for application classification
	 */
	public int getTaskType(){
		return type;
	}
	
	/**
	 * Gets the simulation time when this task was created.
	 * @return Creation timestamp in simulation time
	 */
	public double getCreationTime() {
		return creationTime;
	}
}
