/*
 * Title:        EdgeCloudSim - Mobile Server Manager
 * 
 * Description: 
 * VehicularDefaultMobileServerManager is responsible for creating
 * mobile datacenters, hosts and VMs.
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app3;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.VmSchedulerSpaceShared;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileHost;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileServerManager;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileVM;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileVmAllocationPolicy_Custom;

/**
 * Sample mobile server manager for creating and managing mobile device processing units.
 * Handles creation of mobile datacenters, hosts, and VMs for local task execution.
 * Uses single datacenter approach to avoid memory issues with large mobile device counts.
 */
public class SampleMobileServerManager extends MobileServerManager{
	/** Number of mobile devices in the simulation */
	private int numOfMobileDevices=0;
	
	/**
	 * Constructor for sample mobile server manager.
	 * @param _numOfMobileDevices Number of mobile devices to create processing units for
	 */
	public SampleMobileServerManager(int _numOfMobileDevices) {
		numOfMobileDevices=_numOfMobileDevices;
	}

	/**
	 * Initialize mobile server manager - no specific initialization needed.
	 */
	@Override
	public void initialize() {
	}
	
	/**
	 * Returns custom VM allocation policy for mobile device VMs.
	 * @param list List of mobile hosts
	 * @param dataCenterIndex Datacenter index for policy configuration
	 * @return Custom VM allocation policy for mobile devices
	 */
	@Override
	public VmAllocationPolicy getVmAllocationPolicy(List<? extends Host> list, int dataCenterIndex) {
		return new MobileVmAllocationPolicy_Custom(list, dataCenterIndex);
	}

	/**
	 * Start mobile datacenters for device processing units.
	 * Uses single datacenter approach to avoid memory issues with large device counts.
	 * @throws Exception if datacenter creation fails
	 */
	@Override
	public void startDatacenters() throws Exception {
		// Single datacenter approach prevents out-of-memory issues
		// with large numbers of mobile devices (vs. separate datacenter per device)
		localDatacenter = createDatacenter(SimSettings.MOBILE_DATACENTER_ID);
	}

	/**
	 * Terminate all mobile datacenters when simulation ends.
	 */
	@Override
	public void terminateDatacenters() {
		localDatacenter.shutdownEntity();
	}

	/**
	 * Create VM list for all mobile devices with unique IDs.
	 * Each mobile device gets one host with one VM for local processing.
	 * @param brokerId CloudSim broker ID for VM ownership
	 */
	@Override
	public void createVmList(int brokerId) {
		// Ensure unique VM IDs by starting after edge and cloud VMs
		int vmCounter=SimSettings.getInstance().getNumOfEdgeVMs() + SimSettings.getInstance().getNumOfCloudVMs();
		
		// Create one VM per mobile device for local task processing
		for (int i = 0; i < numOfMobileDevices; i++) {
			vmList.add(i, new ArrayList<MobileVM>());

			// VM configuration parameters
			String vmm = "Xen";
			int numOfCores = SimSettings.getInstance().getCoreForMobileVM();
			double mips = SimSettings.getInstance().getMipsForMobileVM();
			int ram = SimSettings.getInstance().getRamForMobileVM();
			long storage = SimSettings.getInstance().getStorageForMobileVM();
			long bandwidth = 0;
			
			// Create mobile VM with time-shared scheduling for multitasking		
			MobileVM vm = new MobileVM(vmCounter, brokerId, mips, numOfCores, ram, bandwidth, storage, vmm, new CloudletSchedulerTimeShared());
			vmList.get(i).add(vm);
			vmCounter++;
		}
	}

	/**
	 * Calculate average CPU utilization across all mobile device VMs.
	 * Used for performance monitoring and load balancing decisions.
	 * @return Average CPU utilization percentage (0-100)
	 */
	@Override
	public double getAvgUtilization() {
		double totalUtilization = 0;
		double vmCounter = 0;

		List<? extends Host> list = localDatacenter.getHostList();
		// Iterate through all mobile device hosts
		for (int hostIndex=0; hostIndex < list.size(); hostIndex++) {
			List<MobileVM> vmArray = SimManager.getInstance().getMobileServerManager().getVmList(hostIndex);
			// Calculate utilization for each mobile VM
			for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
				totalUtilization += vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				vmCounter++;
			}
		}

		return totalUtilization / vmCounter;
	}
	

	/**
	 * Create mobile datacenter with configured hosts and characteristics.
	 * @param index Datacenter index for naming
	 * @return Configured mobile datacenter
	 * @throws Exception if datacenter creation fails
	 */
	private Datacenter createDatacenter(int index) throws Exception{
		// Datacenter architecture and platform configuration
		String arch = "x86";
		String os = "Linux";
		String vmm = "Xen";
		double costPerBw = 0;      // No bandwidth cost for mobile devices
		double costPerSec = 0;     // No processing cost for mobile devices
		double costPerMem = 0;     // No memory cost for mobile devices
		double costPerStorage = 0; // No storage cost for mobile devices
		
		List<MobileHost> hostList=createHosts();
		
		String name = "MobileDatacenter_" + Integer.toString(index);
		double time_zone = 3.0;    // Time zone for datacenter location
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // No SAN devices for mobile

		// Create datacenter characteristics with mobile device specifications
		DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, time_zone, costPerSec, costPerMem, costPerStorage, costPerBw);

		// Create mobile datacenter with custom VM allocation policy
		Datacenter datacenter = null;
	
		VmAllocationPolicy vm_policy = getVmAllocationPolicy(hostList,index);
		datacenter = new Datacenter(name, characteristics, vm_policy, storageList, 0);
		
		return datacenter;
	}
	
	/**
	 * Create mobile hosts representing individual mobile devices.
	 * Each mobile device gets one host for local processing capabilities.
	 * @return List of configured mobile hosts
	 */
	private List<MobileHost> createHosts(){
		// Create host list for mobile device processing units
		List<MobileHost> hostList = new ArrayList<MobileHost>();
		
		// Create one host per mobile device
		for (int i = 0; i < numOfMobileDevices; i++) {

			// Mobile device hardware specifications from configuration
			int numOfCores = SimSettings.getInstance().getCoreForMobileVM();
			double mips = SimSettings.getInstance().getMipsForMobileVM();
			int ram = SimSettings.getInstance().getRamForMobileVM();
			long storage = SimSettings.getInstance().getStorageForMobileVM();
			long bandwidth = 0; // No bandwidth limitation for mobile processing
			
			// Create processing elements (cores) for mobile device
			List<Pe> peList = new ArrayList<Pe>();

			// Add processing cores based on mobile device configuration
			for(int j=0; j<numOfCores; j++){
				peList.add(new Pe(j, new PeProvisionerSimple(mips)));
			}
			
			// Create mobile host with unique ID and resource provisioners
			MobileHost host = new MobileHost(
					// Ensure unique host IDs by offsetting after edge and cloud hosts
					i+SimSettings.getInstance().getNumOfEdgeHosts()+SimSettings.getInstance().getNumOfCloudHost(),
					new RamProvisionerSimple(ram),
					new BwProvisionerSimple(bandwidth),
					storage,
					peList,
					new VmSchedulerSpaceShared(peList) // Space-shared scheduling for mobile VMs
				);
			
			// Associate host with specific mobile device ID
			host.setMobileDeviceId(i);
			hostList.add(host);
		}

		return hostList;
	}
	
}
