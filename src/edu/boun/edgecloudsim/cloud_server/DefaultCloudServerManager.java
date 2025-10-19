/*
 * Title:        EdgeCloudSim - Cloud Server Manager
 * 
 * Description: 
 * DefaultCloudServerManager is responsible for creating datacenters, hosts and VMs.
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.cloud_server;

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

/**
 * Default implementation of CloudServerManager for standard cloud infrastructure.
 * Manages cloud datacenters, hosts, and VMs with default EdgeCloudSim configurations.
 */
public class DefaultCloudServerManager extends CloudServerManager{

	/**
	 * Constructor for default cloud server manager.
	 */
	public DefaultCloudServerManager() {

	}

	/**
	 * Initializes the cloud server manager.
	 * No special initialization required for default implementation.
	 */
	@Override
	public void initialize() {
	}
	
	/**
	 * Creates and returns the VM allocation policy for cloud datacenters.
	 * @param hostList List of hosts available for VM allocation
	 * @param dataCenterIndex Index of the datacenter
	 * @return Custom VM allocation policy for cloud resources
	 */
	@Override
	public VmAllocationPolicy getVmAllocationPolicy(List<? extends Host> hostList, int dataCenterIndex) {
		return new CloudVmAllocationPolicy_Custom(hostList,dataCenterIndex);
	}
	
	/**
	 * Starts the cloud datacenter infrastructure.
	 * Creates and initializes the main cloud datacenter.
	 * @throws Exception if datacenter creation fails
	 */
	public void startDatacenters() throws Exception{
		localDatacenter = createDatacenter(SimSettings.CLOUD_DATACENTER_ID);
	}

	/**
	 * Terminates all cloud datacenters and releases resources.
	 */
	public void terminateDatacenters(){
		localDatacenter.shutdownEntity();
	}

	/**
	 * Creates the complete list of cloud VMs for all hosts.
	 * Ensures unique VM IDs by starting after edge VM ID range.
	 * @param brokerId The broker ID that will manage these cloud VMs
	 */
	public void createVmList(int brokerId){
		// VMs should have unique IDs, so create Cloud VMs after Edge VMs
		int vmCounter=SimSettings.getInstance().getNumOfEdgeVMs();
		
		// Create VMs for each cloud host
		for (int i = 0; i < SimSettings.getInstance().getNumOfCloudHost(); i++) {
			vmList.add(i, new ArrayList<CloudVM>());
			for(int j = 0; j < SimSettings.getInstance().getNumOfCloudVMsPerHost(); j++){
				String vmm = "Xen";
				int numOfCores = SimSettings.getInstance().getCoreForCloudVM();
				double mips = SimSettings.getInstance().getMipsForCloudVM();
				int ram = SimSettings.getInstance().getRamForCloudVM();
				long storage = SimSettings.getInstance().getStorageForCloudVM();
				long bandwidth = 0;
				
				// Create cloud VM with configured parameters
				CloudVM vm = new CloudVM(vmCounter, brokerId, mips, numOfCores, ram, bandwidth, storage, vmm, new CloudletSchedulerTimeShared());
				vmList.get(i).add(vm);
				vmCounter++;
			}
		}
	}
	
	/**
	 * Calculates the average CPU utilization across all cloud VMs.
	 * Iterates through all hosts and their VMs to compute overall utilization.
	 * @return Average utilization percentage (0.0 to 1.0)
	 */
	public double getAvgUtilization(){
		double totalUtilization = 0;
		double vmCounter = 0;

		List<? extends Host> list = localDatacenter.getHostList();
		// Iterate through each host in the datacenter
		for (int hostIndex=0; hostIndex < list.size(); hostIndex++) {
			List<CloudVM> vmArray = SimManager.getInstance().getCloudServerManager().getVmList(hostIndex);
			// Calculate utilization for each VM on this host
			for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
				totalUtilization += vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				vmCounter++;
			}
		}

		return totalUtilization / vmCounter;
	}

	/**
	 * Creates a cloud datacenter with specified configuration.
	 * Configures datacenter characteristics including hosts, policies, and costs.
	 * 
	 * @param index The datacenter index identifier
	 * @return Configured Datacenter instance
	 * @throws Exception if datacenter creation fails
	 */
	private Datacenter createDatacenter(int index) throws Exception{
		String arch = "x86";
		String os = "Linux";
		String vmm = "Xen";
		double costPerBw = 0;
		double costPerSec = 0;
		double costPerMem = 0;
		double costPerStorage = 0;
		
		List<Host> hostList=createHosts();
		
		String name = "CloudDatacenter_" + Integer.toString(index);
		double time_zone = 3.0;         // Time zone this resource is located
		LinkedList<Storage> storageList = new LinkedList<Storage>();	// No SAN devices added currently

		// Create datacenter characteristics object with infrastructure properties:
		// architecture, OS, VMM, host list, time zone, and pricing model
		DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, time_zone, costPerSec, costPerMem, costPerStorage, costPerBw);

		// Create the datacenter with VM allocation policy
		Datacenter datacenter = null;
	
		VmAllocationPolicy vm_policy = getVmAllocationPolicy(hostList,index);
		datacenter = new Datacenter(name, characteristics, vm_policy, storageList, 0);
		
		return datacenter;
	}
	
	/**
	 * Creates the list of hosts for the cloud datacenter.
	 * Each host is configured with processing elements, RAM, storage, and bandwidth
	 * based on simulation settings and number of VMs per host.
	 * 
	 * @return List of configured Host objects for the datacenter
	 */
	private List<Host> createHosts(){
		// Step 1: Create list to store cloud hosts
		List<Host> hostList = new ArrayList<Host>();
		
		for (int i = 0; i < SimSettings.getInstance().getNumOfCloudHost(); i++) {
			int numOfVMPerHost = SimSettings.getInstance().getNumOfCloudVMsPerHost();
			// Calculate total resources needed (host resources = sum of VM resources)
			int numOfCores = SimSettings.getInstance().getCoreForCloudVM() * numOfVMPerHost;
			double mips = SimSettings.getInstance().getMipsForCloudVM() * numOfVMPerHost;
			int ram = SimSettings.getInstance().getRamForCloudVM() * numOfVMPerHost;
			long storage = SimSettings.getInstance().getStorageForCloudVM() * numOfVMPerHost;
			long bandwidth = 0;
			
			// Step 2: Create processing elements (PEs) for this host
			List<Pe> peList = new ArrayList<Pe>();

			// Step 3: Add PEs to the list with MIPS provisioner
			for(int j=0; j<numOfCores; j++){
				peList.add(new Pe(j, new PeProvisionerSimple(mips))); // PE ID and MIPS capacity
			}
			
			// Step 4: Create host with unique ID and resource provisioners
			Host host = new Host(
					// Hosts should have unique IDs, so create Cloud Hosts after Edge Hosts
					i+SimSettings.getInstance().getNumOfEdgeHosts(),
					new RamProvisionerSimple(ram),
					new BwProvisionerSimple(bandwidth), // Bandwidth in kbps
					storage,
					peList,
					new VmSchedulerSpaceShared(peList)  // Space-shared VM scheduling
				);
			hostList.add(host);
		}

		return hostList;
	}
}
