/*
 * Title:        EdgeCloudSim - Edge Server Manager
 * 
 * Description: 
 * DefaultEdgeServerManager is responsible for creating datacenters, hosts and VMs.
 * It also provides the list of VMs running on the hosts.
 * This information is critical for the edge orchestrator.
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.tutorial4;

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
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

public class SampleEdgeServerManager extends EdgeServerManager{
	private int hostIdCounter;
	String simScenario;
	List<Integer> randomMIPS;

	public SampleEdgeServerManager(String _simScenario) {
		// Manager builds edge hosts according to scenario-driven capacity distribution strategy.
		hostIdCounter = 0;
		simScenario = _simScenario;
	}

	@Override
	public void initialize() {
		// Pool used only in RANDOM_CAPACITY scenario. Each value consumed exactly once.
		// NOTE: Ensure the count matches number of hosts defined in XML.
		randomMIPS = new ArrayList<Integer>();
		randomMIPS.add(10000);
		randomMIPS.add(10000);
		randomMIPS.add(15000);
		randomMIPS.add(15000);
		randomMIPS.add(20000);
		randomMIPS.add(20000);
		randomMIPS.add(20000);
		randomMIPS.add(25000);
		randomMIPS.add(25000);
		randomMIPS.add(30000);
		randomMIPS.add(30000);
	}

	@Override
	public VmAllocationPolicy getVmAllocationPolicy(List<? extends Host> hostList, int dataCenterIndex) {
		return new EdgeVmAllocationPolicy_Custom(hostList,dataCenterIndex);
	}
	
	public void startDatacenters() throws Exception{
		Document doc = SimSettings.getInstance().getEdgeDevicesDocument();
		NodeList datacenterList = doc.getElementsByTagName("datacenter");
		for (int i = 0; i < datacenterList.getLength(); i++) {
			Node datacenterNode = datacenterList.item(i);
			Element datacenterElement = (Element) datacenterNode;
			localDatacenters.add(createDatacenter(i, datacenterElement));
		}
	}

	public void createVmList(int brokerId){
		// One VM list per host; VMs inside a host equally share host MIPS (simple static partition).
		// TODO: Consider using dynamic share based on active task load instead of static division.
		int hostCounter=0;
		int vmCounter=0;
		
		//Create VMs for each hosts
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
					double mips = 0;//Double.parseDouble(vmElement.getElementsByTagName("mips").item(0).getTextContent());
					int ram = Integer.parseInt(vmElement.getElementsByTagName("ram").item(0).getTextContent());
					long storage = Long.parseLong(vmElement.getElementsByTagName("storage").item(0).getTextContent());
					long bandwidth = SimSettings.getInstance().getWlanBandwidth() / (hostNodeList.getLength()+vmNodeList.getLength());
					
					/**
					 * In this scenario each VM equally shares the MIPS value of corresponding Host
					 * WARNING: getHostList().get(0) always chooses the first host of the datacenter.
					 * If multiple hosts per datacenter are expected, replace with proper index mapping.
					 */
					Host host = getDatacenterList().get(i).getHostList().get(0);
					mips = host.getMaxAvailableMips() / vmNodeList.getLength();
					
					//VM Parameters		
					EdgeVM vm = new EdgeVM(vmCounter, brokerId, mips, numOfCores, ram, bandwidth, storage, vmm, new CloudletSchedulerTimeShared());
					vmList.get(hostCounter).add(vm);
					vmCounter++;
				}

				hostCounter++;
			}
		}
	}
	
	public void terminateDatacenters(){
		for (Datacenter datacenter : localDatacenters) {
			datacenter.shutdownEntity();
		}
	}

	//average utilization of all VMs
	public double getAvgUtilization(){
		double totalUtilization = 0;
		double vmCounter = 0;
		
		// for each datacenter...
		for(int i= 0; i<localDatacenters.size(); i++) {
			List<? extends Host> list = localDatacenters.get(i).getHostList();
			// for each host...
			for (int j=0; j < list.size(); j++) {
				Host host = list.get(j);
				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(host.getId());
				//for each vm...
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					totalUtilization += vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					vmCounter++;
				}
			}
		}
		return totalUtilization / vmCounter;
	}

	private Datacenter createDatacenter(int index, Element datacenterElement) throws Exception{
		String arch = datacenterElement.getAttribute("arch");
		String os = datacenterElement.getAttribute("os");
		String vmm = datacenterElement.getAttribute("vmm");
		double costPerBw = Double.parseDouble(datacenterElement.getElementsByTagName("costPerBw").item(0).getTextContent());
		double costPerSec = Double.parseDouble(datacenterElement.getElementsByTagName("costPerSec").item(0).getTextContent());
		double costPerMem = Double.parseDouble(datacenterElement.getElementsByTagName("costPerMem").item(0).getTextContent());
		double costPerStorage = Double.parseDouble(datacenterElement.getElementsByTagName("costPerStorage").item(0).getTextContent());
		
		List<EdgeHost> hostList=createHosts(datacenterElement);
		
		String name = "Datacenter_" + Integer.toString(index);
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
	
	private List<EdgeHost> createHosts(Element datacenterElement){
		// Builds hosts from XML, assigning capacity based on scenario:
		// EQUAL_CAPACITY -> uniform MIPS
		// RANDOM_CAPACITY -> random draw from predefined pool
		// TRAFFIC_HEURISTIC -> heuristic mapping based on road segment type (placeTypeIndex)
		// Here are the steps needed to create a PowerDatacenter:
		// 1. We need to create a list to store one or more Machines
		List<EdgeHost> hostList = new ArrayList<EdgeHost>();
		
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
			double mips = 0;// Double.parseDouble(hostElement.getElementsByTagName("mips").item(0).getTextContent());
			int ram = Integer.parseInt(hostElement.getElementsByTagName("ram").item(0).getTextContent());
			long storage = Long.parseLong(hostElement.getElementsByTagName("storage").item(0).getTextContent());
			long bandwidth = SimSettings.getInstance().getWlanBandwidth() / hostNodeList.getLength();
			
			/**
			 * Please note that total MIPS value of all scenario should be the same!
			 * In this scenario we have 11 edge server, and total MIPS value is 22000
			 * Each scenario allocates this value to edge hosts at different rates
			 */
			if(simScenario.equals("EQUAL_CAPACITY")) {
				// Uniform capacity for all hosts. Adjust to keep total system MIPS consistent across scenarios.
				mips = 20000;
			}
			else if(simScenario.equals("RANDOM_CAPACITY")) {
				// Randomly assign one unused MIPS value; ensures reproducible total if seed fixed externally.
				if(randomMIPS.isEmpty()) {
					// Defensive check: XML host count must not exceed pool size.
					SimLogger.printLine("Impossible is occurred, ramdom mips list is empty! The simulation has been terminated.");
					System.exit(0);
				}

				int randomIndex = SimUtils.getRandomNumber(0, randomMIPS.size()-1);
				mips = randomMIPS.remove(randomIndex);
			}
			else if(simScenario.equals("TRAFFIC_HEURISTIC")) {
				// Heuristic: slower traffic zones allocated higher compute to absorb denser task arrivals.
				switch (placeTypeIndex) {
				//There is only one section with type 1 on the road
				//The average speed of vehicles on these sections of the road is 20
				case 0: {
					mips = 44000;
					break;
				}
				//There are six sections with type 1 on the road
				//The average speed of vehicles on these sections of the road is 40
				case 1: {
					mips = 20000;
					break;
				}
				//There are four sections with type 2 on the road
				//The average speed of vehicles on these sections of the road is 60
				case 2: {
					mips = 14000;
					break;
				}
				default:
					// Unknown mapping implies XML mismatch or new type not integrated.
					SimLogger.printLine("Unknown place type! The simulation has been terminated.");
					System.exit(0);
				}
			}
			else {
				// Scenario label not recognized. Validate config file field 'simScenario'.
				SimLogger.printLine("Unknown simulation scenario! The simulation has been terminated.");
				System.exit(0);
			}
			
			// 2. A Machine contains one or more PEs or CPUs/Cores. Therefore, should
			//    create a list to store these PEs before creating
			//    a Machine.
			List<Pe> peList = new ArrayList<Pe>();

			// 3. Create PEs and add these into the list.
			//for a quad-core machine, a list of 4 PEs is required:
			for(int i=0; i<numOfCores; i++){
				peList.add(new Pe(i, new PeProvisionerSimple(mips))); // need to store Pe id and MIPS Rating
			}
			
			//4. Create Hosts with its id and list of PEs and add them to the list of machines
			EdgeHost host = new EdgeHost(
					hostIdCounter,
					new RamProvisionerSimple(ram),
					new BwProvisionerSimple(bandwidth), //kbps
					storage,
					peList,
					new VmSchedulerSpaceShared(peList)
				);
			// Location ties host to WLAN id and spatial coordinates for proximity calculations.
			host.setPlace(new Location(placeTypeIndex, wlan_id, x_pos, y_pos));
			hostList.add(host);
			hostIdCounter++;
		}

		return hostList;
	}
}
