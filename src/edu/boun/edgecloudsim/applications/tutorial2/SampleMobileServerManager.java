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

package edu.boun.edgecloudsim.applications.tutorial2;

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

/*
 * Responsibilities:
 * - Provision a single shared "mobile" datacenter hosting one MobileHost+MobileVM per device
 * - Expose per-host and global utilization metrics for orchestrator heuristics
 * Assumptions:
 * - Exactly one VM per mobile device (simplifies selection and accounting)
 * - Mobile datacenter costs are zero (no billing model)
 * - Bandwidth set to 0 (network modeled externally)
 * Extension points: override createHosts/createVmList for heterogeneous devices.
 */

public class SampleMobileServerManager extends MobileServerManager{
	private int numOfMobileDevices=0; // total mobile devices => also number of mobile hosts/VMs
	
	public SampleMobileServerManager(int _numOfMobileDevices) {
		numOfMobileDevices=_numOfMobileDevices; // store population size
	}

	@Override
	public void initialize() {
	}
	
	@Override
	public VmAllocationPolicy getVmAllocationPolicy(List<? extends Host> list, int dataCenterIndex) {
		return new MobileVmAllocationPolicy_Custom(list, dataCenterIndex);
	}

	@Override
	public void startDatacenters() throws Exception {
		// Single shared datacenter avoids OOM risk of per-device datacenters
		localDatacenter = createDatacenter(SimSettings.MOBILE_DATACENTER_ID);
	}

	@Override
	public void terminateDatacenters() {
		localDatacenter.shutdownEntity();
	}

	@Override
	public void createVmList(int brokerId) {
		// VM IDs must be globally unique: offset after edge + cloud VMs
		// One MobileVM per device (index aligned with device id)
		int vmCounter=SimSettings.getInstance().getNumOfEdgeVMs() + SimSettings.getInstance().getNumOfCloudVMs();
		
		//Create VMs for each hosts
		//Note that each mobile device has one host with one VM!
		for (int i = 0; i < numOfMobileDevices; i++) {
			vmList.add(i, new ArrayList<MobileVM>());

			String vmm = "Xen";
			int numOfCores = SimSettings.getInstance().getCoreForMobileVM();
			double mips = SimSettings.getInstance().getMipsForMobileVM();
			int ram = SimSettings.getInstance().getRamForMobileVM();
			long storage = SimSettings.getInstance().getStorageForMobileVM();
			long bandwidth = 0;
			
			//VM Parameters		
			MobileVM vm = new MobileVM(vmCounter, brokerId, mips, numOfCores, ram, bandwidth, storage, vmm, new CloudletSchedulerTimeShared());
			vmList.get(i).add(vm);
			vmCounter++;
		}
	}

	@Override
	public double getAvgUtilization() {
		// Aggregate CPU utilization across all mobile VMs at current simulation time
		double totalUtilization = 0;
		double vmCounter = 0;

		List<? extends Host> list = localDatacenter.getHostList();
		// for each host...
		for (int hostIndex=0; hostIndex < list.size(); hostIndex++) {
			List<MobileVM> vmArray = SimManager.getInstance().getMobileServerManager().getVmList(hostIndex);
			//for each vm...
			for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
				totalUtilization += vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				vmCounter++;
			}
		}

		return totalUtilization / vmCounter;
	}

	public double getAvgHostUtilization(int hostIndex) {
		// Mean utilization across VMs on a specific mobile host (currently always 1 VM)
		double totalUtilization = 0;
		double vmCounter = 0;

		List<MobileVM> vmArray = SimManager.getInstance().getMobileServerManager().getVmList(hostIndex);
		//for each vm...
		for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
			totalUtilization += vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
			vmCounter++;
		}
		
		return totalUtilization / vmCounter;
	}

	private Datacenter createDatacenter(int index) throws Exception{
		// Build datacenter characteristics with zero pricing; hosts derived from createHosts()
		String arch = "x86";
		String os = "Linux";
		String vmm = "Xen";
		double costPerBw = 0;
		double costPerSec = 0;
		double costPerMem = 0;
		double costPerStorage = 0;
		
		List<MobileHost> hostList=createHosts();
		
		String name = "MobileDatacenter_" + Integer.toString(index);
		double time_zone = 3.0;         // time zone this resource located
		LinkedList<Storage> storageList = new LinkedList<Storage>();	//we are not adding SAN devices by now

		// 5. Create a DatacenterCharacteristics object that stores the
		//    properties of a data center: architecture, OS, list of
		//    Machines, allocation policy: time- or space-shared, time zone
		//    and its price (G$/Pe time unit).
		DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, time_zone, costPerSec, costPerMem, costPerStorage, costPerBw);

		// 6. Finally, we need to create a PowerDatacenter object.
		Datacenter datacenter = null;
	
		VmAllocationPolicy vm_policy = getVmAllocationPolicy(hostList,index);
		datacenter = new Datacenter(name, characteristics, vm_policy, storageList, 0);
		
		return datacenter;
	}
	
	private List<MobileHost> createHosts(){
		// Creates one MobileHost per device; IDs offset after edge+cloud hosts
		// Each host configured homogeneously using mobile VM settings
		// Steps:
		// 1) Build PE list
		// 2) Instantiate MobileHost with SpaceShared scheduler
		// 3) Tag host with its mobileDeviceId for lookup
		// Here are the steps needed to create a PowerDatacenter:
		// 1. We need to create a list to store one or more Machines
		List<MobileHost> hostList = new ArrayList<MobileHost>();
		
		for (int i = 0; i < numOfMobileDevices; i++) {

			int numOfCores = SimSettings.getInstance().getCoreForMobileVM();
			double mips = SimSettings.getInstance().getMipsForMobileVM();
			int ram = SimSettings.getInstance().getRamForMobileVM();
			long storage = SimSettings.getInstance().getStorageForMobileVM();
			long bandwidth = 0;
			
			// 2. A Machine contains one or more PEs or CPUs/Cores. Therefore, should
			//    create a list to store these PEs before creating
			//    a Machine.
			List<Pe> peList = new ArrayList<Pe>();

			// 3. Create PEs and add these into the list.
			//for a quad-core machine, a list of 4 PEs is required:
			for(int j=0; j<numOfCores; j++){
				peList.add(new Pe(j, new PeProvisionerSimple(mips))); // need to store Pe id and MIPS Rating
			}
			
			//4. Create Hosts with its id and list of PEs and add them to the list of machines
			MobileHost host = new MobileHost(
					//Hosts should have unique IDs, so create Mobile Hosts after Edge+Cloud Hosts
					i+SimSettings.getInstance().getNumOfEdgeHosts()+SimSettings.getInstance().getNumOfCloudHost(),
					new RamProvisionerSimple(ram),
					new BwProvisionerSimple(bandwidth), //kbps
					storage,
					peList,
					new VmSchedulerSpaceShared(peList)
				);
			
			host.setMobileDeviceId(i);
			hostList.add(host);
		}

		return hostList;
	}
	
}
