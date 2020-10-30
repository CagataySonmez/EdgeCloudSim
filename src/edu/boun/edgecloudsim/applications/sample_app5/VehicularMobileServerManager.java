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
	private int numOfMobileDevices=0;

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

	@Override
	public void startDatacenters() throws Exception {
		//in the initial version, each mobile device has a separate datacenter
		//however, this approach encounters with out of memory (oom) problem.
		//therefore, we use single datacenter for all mobile devices!
		localDatacenter = createDatacenter(SimSettings.MOBILE_DATACENTER_ID);
	}

	@Override
	public void terminateDatacenters() {
		localDatacenter.shutdownEntity();
	}

	@Override
	public void createVmList(int brockerId) {
		//VMs should have unique IDs, so create Mobile VMs after Edge+Cloud VMs
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
			MobileVM vm = new MobileVM(vmCounter, brockerId, mips, numOfCores, ram, bandwidth, storage, vmm, new CloudletSchedulerTimeShared());
			vmList.get(i).add(vm);
			vmCounter++;
		}
	}

	@Override
	public double getAvgUtilization() {
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


	private Datacenter createDatacenter(int index) throws Exception{
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
