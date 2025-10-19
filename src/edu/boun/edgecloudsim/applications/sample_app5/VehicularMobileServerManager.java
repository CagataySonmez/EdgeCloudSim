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

package edu.boun.edgecloudsim.applications.sample_app5;

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

public class VehicularMobileServerManager extends MobileServerManager{
	private int numOfMobileDevices=0;  // Total number of mobile devices (vehicles)

	/**
	 * Constructor for vehicular mobile server manager.
	 * 
	 * @param _numOfMobileDevices number of mobile devices to manage
	 */
	public VehicularMobileServerManager(int _numOfMobileDevices) {
		numOfMobileDevices=_numOfMobileDevices;
	}

	@Override
	public void initialize() {
	}

	@Override
	public VmAllocationPolicy getVmAllocationPolicy(List<? extends Host> list, int dataCenterIndex) {
		return new MobileVmAllocationPolicy_Custom(list, dataCenterIndex);
	}

	/**
	 * Starts mobile datacenters for local processing capabilities.
	 * Uses a single datacenter for all mobile devices to avoid memory issues.
	 */
	@Override
	public void startDatacenters() throws Exception {
		// Originally, each mobile device had a separate datacenter
		// However, this approach encounters out of memory (OOM) problems
		// Therefore, we use a single datacenter for all mobile devices
		localDatacenter = createDatacenter(SimSettings.MOBILE_DATACENTER_ID);
	}

	@Override
	public void terminateDatacenters() {
		localDatacenter.shutdownEntity();
	}

	/**
	 * Creates VM list for mobile processing units.
	 * Each mobile device gets one VM for local task processing.
	 * 
	 * @param brockerId CloudSim broker identifier
	 */
	@Override
	public void createVmList(int brockerId) {
		// VMs should have unique IDs, so create Mobile VMs after Edge+Cloud VMs
		int vmCounter=SimSettings.getInstance().getNumOfEdgeVMs() + SimSettings.getInstance().getNumOfCloudVMs();

		// Create VMs for each mobile device
		// Note: Each mobile device has one host with one VM for local processing
		for (int i = 0; i < numOfMobileDevices; i++) {
			vmList.add(i, new ArrayList<MobileVM>());

			// VM configuration parameters
			String vmm = "Xen";  // Virtual Machine Monitor
			int numOfCores = SimSettings.getInstance().getCoreForMobileVM();
			double mips = SimSettings.getInstance().getMipsForMobileVM();
			int ram = SimSettings.getInstance().getRamForMobileVM();
			long storage = SimSettings.getInstance().getStorageForMobileVM();
			long bandwidth = 0;  // Local processing doesn't require network bandwidth

			// Create and add mobile VM with time-shared scheduling
			MobileVM vm = new MobileVM(vmCounter, brockerId, mips, numOfCores, ram, bandwidth, storage, vmm, new CloudletSchedulerTimeShared());
			vmList.get(i).add(vm);
			vmCounter++;
		}
	}

	/**
	 * Calculates average CPU utilization across all mobile VMs.
	 * 
	 * @return average CPU utilization as a percentage (0-100)
	 */
	@Override
	public double getAvgUtilization() {
		double totalUtilization = 0;
		double vmCounter = 0;

		List<? extends Host> list = localDatacenter.getHostList();
		// Iterate through each mobile host
		for (int hostIndex=0; hostIndex < list.size(); hostIndex++) {
			List<MobileVM> vmArray = SimManager.getInstance().getMobileServerManager().getVmList(hostIndex);
			// Calculate utilization for each VM on the host
			for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
				totalUtilization += vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				vmCounter++;
			}
		}

		return totalUtilization / vmCounter;
	}


	/**
	 * Creates a mobile datacenter for vehicle-based processing units.
	 * 
	 * @param index datacenter identifier
	 * @return configured Datacenter instance for mobile processing
	 * @throws Exception if datacenter creation fails
	 */
	private Datacenter createDatacenter(int index) throws Exception{
		// Datacenter configuration - standard mobile device setup
		String arch = "x86";      // Architecture
		String os = "Linux";      // Operating system
		String vmm = "Xen";       // Virtual machine monitor
		double costPerBw = 0;     // No cost for mobile processing
		double costPerSec = 0;    // No cost per second
		double costPerMem = 0;    // No memory cost
		double costPerStorage = 0; // No storage cost

		List<MobileHost> hostList=createHosts();

		String name = "MobileDatacenter_" + Integer.toString(index);
		double time_zone = 3.0;         // Time zone where resource is located
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // No SAN devices for mobile

		// Create DatacenterCharacteristics object that stores datacenter properties:
		// architecture, OS, list of machines, allocation policy, time zone and pricing
		DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
				arch, os, vmm, hostList, time_zone, costPerSec, costPerMem, costPerStorage, costPerBw);

		// Create the mobile datacenter with specified characteristics
		Datacenter datacenter = null;

		VmAllocationPolicy vm_policy = getVmAllocationPolicy(hostList,index);
		datacenter = new Datacenter(name, characteristics, vm_policy, storageList, 0);

		return datacenter;
	}

	/**
	 * Creates mobile hosts representing the processing units in each vehicle.
	 * Each mobile device gets one host with configured processing capabilities.
	 * 
	 * @return list of configured MobileHost instances
	 */
	private List<MobileHost> createHosts(){
		// Create list to store mobile hosts (one per mobile device)
		List<MobileHost> hostList = new ArrayList<MobileHost>();

		for (int i = 0; i < numOfMobileDevices; i++) {
			// Get mobile VM configuration parameters
			int numOfCores = SimSettings.getInstance().getCoreForMobileVM();
			double mips = SimSettings.getInstance().getMipsForMobileVM();
			int ram = SimSettings.getInstance().getRamForMobileVM();
			long storage = SimSettings.getInstance().getStorageForMobileVM();
			long bandwidth = 0; // Local processing doesn't require network bandwidth

			// Create list to store Processing Elements (PEs/CPU cores)
			List<Pe> peList = new ArrayList<Pe>();

			// Create PEs according to the number of cores configuration
			for(int j=0; j<numOfCores; j++){
				peList.add(new Pe(j, new PeProvisionerSimple(mips))); // PE with ID and MIPS rating
			}

			// Create mobile host with unique ID and processing capabilities
			MobileHost host = new MobileHost(
					// Hosts should have unique IDs, so create Mobile Hosts after Edge+Cloud Hosts
					i+SimSettings.getInstance().getNumOfEdgeHosts()+SimSettings.getInstance().getNumOfCloudHost(),
					new RamProvisionerSimple(ram),
					new BwProvisionerSimple(bandwidth), // Bandwidth in kbps
					storage,
					peList,
					new VmSchedulerSpaceShared(peList) // Space-shared VM scheduling
					);

			// Associate host with mobile device ID for tracking
			host.setMobileDeviceId(i);
			hostList.add(host);
		}

		return hostList;
	}

}
