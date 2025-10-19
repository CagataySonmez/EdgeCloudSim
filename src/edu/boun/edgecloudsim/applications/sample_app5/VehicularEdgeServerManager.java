/*
 * Title:        EdgeCloudSim - Edge Server Manager
 * 
 * Description: 
 * VehicularEdgeServerManager is responsible for creating
 * Edge datacenters and hosts/VMs running on it.
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_server.EdgeHost;
import edu.boun.edgecloudsim.edge_server.EdgeServerManager;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.edge_server.EdgeVmAllocationPolicy_Custom;
import edu.boun.edgecloudsim.utils.Location;

public class VehicularEdgeServerManager extends EdgeServerManager{
	private int hostIdCounter;

	public VehicularEdgeServerManager() {
		hostIdCounter = 0;
	}

	@Override
	public void initialize() {
		// No specific initialization required for vehicular edge server manager
	}

	/**
	 * Returns the VM allocation policy for edge datacenters
	 * @param hostList List of hosts in the datacenter
	 * @param dataCenterIndex Index of the datacenter
	 * @return Custom VM allocation policy for edge computing
	 */
	@Override
	public VmAllocationPolicy getVmAllocationPolicy(List<? extends Host> hostList, int dataCenterIndex) {
		return new EdgeVmAllocationPolicy_Custom(hostList,dataCenterIndex);
	}

	/**
	 * Initializes and starts all edge datacenters based on configuration
	 * Creates datacenters from XML configuration file with location-specific parameters
	 */
	public void startDatacenters() throws Exception{
		// Parse edge devices configuration file to create datacenters
		Document doc = SimSettings.getInstance().getEdgeDevicesDocument();
		NodeList datacenterList = doc.getElementsByTagName("datacenter");
		for (int i = 0; i < datacenterList.getLength(); i++) {
			Node datacenterNode = datacenterList.item(i);
			Element datacenterElement = (Element) datacenterNode;
			localDatacenters.add(createDatacenter(i, datacenterElement));
		}
	}

	/**
	 * Creates virtual machines for all hosts across edge datacenters
	 * @param brockerId CloudSim broker ID for VM ownership
	 */
	public void createVmList(int brockerId){
		int hostCounter=0;
		int vmCounter=0;

		// Create VMs for each host based on XML configuration
		Document doc = SimSettings.getInstance().getEdgeDevicesDocument();
		NodeList datacenterList = doc.getElementsByTagName("datacenter");
		for (int i = 0; i < datacenterList.getLength(); i++) {
			Node datacenterNode = datacenterList.item(i);
			Element datacenterElement = (Element) datacenterNode;
			NodeList hostNodeList = datacenterElement.getElementsByTagName("host");
			for (int j = 0; j < hostNodeList.getLength(); j++) {

				vmList.add(hostCounter, new ArrayList<EdgeVM>());

				Node hostNode = hostNodeList.item(j);
				Element hostElement = (Element) hostNode;
				NodeList vmNodeList = hostElement.getElementsByTagName("VM");
				for (int k = 0; k < vmNodeList.getLength(); k++) {
					Node vmNode = vmNodeList.item(k);					
					Element vmElement = (Element) vmNode;

					String vmm = vmElement.getAttribute("vmm");
					int numOfCores = Integer.parseInt(vmElement.getElementsByTagName("core").item(0).getTextContent());
					double mips = Double.parseDouble(vmElement.getElementsByTagName("mips").item(0).getTextContent());
					int ram = Integer.parseInt(vmElement.getElementsByTagName("ram").item(0).getTextContent());
					long storage = Long.parseLong(vmElement.getElementsByTagName("storage").item(0).getTextContent());
					// Distribute WLAN bandwidth among hosts and VMs
					long bandwidth = SimSettings.getInstance().getWlanBandwidth() / (hostNodeList.getLength()+vmNodeList.getLength());

					// Create EdgeVM with specified resources and time-shared scheduling
					EdgeVM vm = new EdgeVM(vmCounter, brockerId, mips, numOfCores, ram, bandwidth, storage, vmm, new CloudletSchedulerTimeShared());
					vmList.get(hostCounter).add(vm);
					vmCounter++;
				}

				hostCounter++;
			}
		}
	}

	/**
	 * Gracefully shuts down all edge datacenters
	 */
	public void terminateDatacenters(){
		for (Datacenter datacenter : localDatacenters) {
			datacenter.shutdownEntity();
		}
	}

	/**
	 * Calculates average CPU utilization across all edge VMs
	 * @return Average utilization percentage (0-100)
	 */
	public double getAvgUtilization(){
		double totalUtilization = 0;
		int hostCounter = 0;
		int vmCounter = 0;

		// Iterate through all datacenters
		for(int i= 0; i<localDatacenters.size(); i++) {
			List<? extends Host> list = localDatacenters.get(i).getHostList();
			// Iterate through all hosts in datacenter
			for (int hostIndex=0; hostIndex < list.size(); hostIndex++) {
				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(hostCounter);
				// Sum CPU utilization for all VMs on this host
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					totalUtilization += vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					vmCounter++;
				}
				hostCounter++;
			}
		}
		return totalUtilization / (double)vmCounter;
	}

	/**
	 * Creates a single edge datacenter from XML configuration
	 * @param index Datacenter index for naming
	 * @param datacenterElement XML element containing datacenter configuration
	 * @return Configured CloudSim Datacenter instance
	 */
	private Datacenter createDatacenter(int index, Element datacenterElement) throws Exception{
		// Parse datacenter attributes from XML
		String arch = datacenterElement.getAttribute("arch");
		String os = datacenterElement.getAttribute("os");
		String vmm = datacenterElement.getAttribute("vmm");
		double costPerBw = Double.parseDouble(datacenterElement.getElementsByTagName("costPerBw").item(0).getTextContent());
		double costPerSec = Double.parseDouble(datacenterElement.getElementsByTagName("costPerSec").item(0).getTextContent());
		double costPerMem = Double.parseDouble(datacenterElement.getElementsByTagName("costPerMem").item(0).getTextContent());
		double costPerStorage = Double.parseDouble(datacenterElement.getElementsByTagName("costPerStorage").item(0).getTextContent());

		List<EdgeHost> hostList=createHosts(datacenterElement);

		String name = "EdgeDatacenter_" + Integer.toString(index);
		double time_zone = 3.0;         // Time zone for this datacenter location
		LinkedList<Storage> storageList = new LinkedList<Storage>();	// No SAN storage devices configured

		// Create datacenter characteristics with hardware specs and costs
		DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
				arch, os, vmm, hostList, time_zone, costPerSec, costPerMem, costPerStorage, costPerBw);

		// Create the datacenter with custom VM allocation policy
		Datacenter datacenter = null;

		VmAllocationPolicy vm_policy = getVmAllocationPolicy(hostList,index);
		datacenter = new Datacenter(name, characteristics, vm_policy, storageList, 0);

		return datacenter;
	}

	/**
	 * Creates hosts for a datacenter from XML configuration
	 * @param datacenterElement XML element containing host specifications
	 * @return List of configured EdgeHost instances with location information
	 */
	private List<EdgeHost> createHosts(Element datacenterElement){

		// Initialize list to store hosts for this datacenter
		List<EdgeHost> hostList = new ArrayList<EdgeHost>();

		// Parse location information for all hosts in this datacenter
		Element location = (Element)datacenterElement.getElementsByTagName("location").item(0);
		String attractiveness = location.getElementsByTagName("attractiveness").item(0).getTextContent();
		int wlan_id = Integer.parseInt(location.getElementsByTagName("wlan_id").item(0).getTextContent());
		int x_pos = Integer.parseInt(location.getElementsByTagName("x_pos").item(0).getTextContent());
		int y_pos = Integer.parseInt(location.getElementsByTagName("y_pos").item(0).getTextContent());
		int placeTypeIndex = Integer.parseInt(attractiveness);

		NodeList hostNodeList = datacenterElement.getElementsByTagName("host");
		for (int j = 0; j < hostNodeList.getLength(); j++) {
			Node hostNode = hostNodeList.item(j);

			Element hostElement = (Element) hostNode;
			int numOfCores = Integer.parseInt(hostElement.getElementsByTagName("core").item(0).getTextContent());
			double mips = Double.parseDouble(hostElement.getElementsByTagName("mips").item(0).getTextContent());
			int ram = Integer.parseInt(hostElement.getElementsByTagName("ram").item(0).getTextContent());
			long storage = Long.parseLong(hostElement.getElementsByTagName("storage").item(0).getTextContent());
			// Distribute WLAN bandwidth equally among all hosts
			long bandwidth = SimSettings.getInstance().getWlanBandwidth() / hostNodeList.getLength();

			// Create processing elements (CPU cores) for this host
			List<Pe> peList = new ArrayList<Pe>();

			// Create individual PEs with specified MIPS capacity
			for(int i=0; i<numOfCores; i++){
				peList.add(new Pe(i, new PeProvisionerSimple(mips))); // PE with unique ID and MIPS rating
			}

			// Create EdgeHost with resource provisioners and space-shared VM scheduler
			EdgeHost host = new EdgeHost(
					hostIdCounter,
					new RamProvisionerSimple(ram),
					new BwProvisionerSimple(bandwidth), // Bandwidth in kbps
					storage,
					peList,
					new VmSchedulerSpaceShared(peList)
					);

			// Set geographical location for vehicular mobility simulation
			host.setPlace(new Location(placeTypeIndex, wlan_id, x_pos, y_pos));
			hostList.add(host);
			hostIdCounter++;
		}

		return hostList;
	}
}
