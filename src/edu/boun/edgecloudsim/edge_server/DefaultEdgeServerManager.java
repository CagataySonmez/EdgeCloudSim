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

package edu.boun.edgecloudsim.edge_server;

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
import edu.boun.edgecloudsim.utils.Location;

/**
 * Default implementation of EdgeServerManager for standard edge computing scenarios.
 * 
 * This class provides a concrete implementation of edge infrastructure management
 * based on XML configuration files. It creates CloudSim datacenters, hosts, and VMs
 * according to specifications defined in the edge devices configuration file.
 * 
 * Key features:
 * - XML-based datacenter configuration parsing
 * - Automatic host and VM creation from configuration specifications
 * - Location-aware edge server deployment with attractiveness modeling
 * - Custom VM allocation policy integration for edge-specific requirements
 * - Resource utilization monitoring across distributed edge infrastructure
 * 
 * The implementation supports multi-datacenter edge deployments with
 * geographically distributed hosts and configurable resource characteristics.
 */
public class DefaultEdgeServerManager extends EdgeServerManager{
	private int hostIdCounter;    // Global counter for unique host ID assignment

	/**
	 * Constructs a DefaultEdgeServerManager and initializes host ID tracking.
	 */
	public DefaultEdgeServerManager() {
		hostIdCounter = 0;
	}

	/**
	 * Initializes the DefaultEdgeServerManager.
	 * Currently no additional initialization is required beyond constructor setup.
	 */
	@Override
	public void initialize() {
	}

	/**
	 * Creates a custom VM allocation policy for edge datacenters.
	 * Returns an edge-specific allocation policy that considers edge computing
	 * requirements such as proximity, load balancing, and resource constraints.
	 * 
	 * @param hostList List of hosts available in the target datacenter
	 * @param dataCenterIndex Index of the datacenter requiring allocation policy
	 * @return Custom edge VM allocation policy instance
	 */
	@Override
	public VmAllocationPolicy getVmAllocationPolicy(List<? extends Host> hostList, int dataCenterIndex) {
		return new EdgeVmAllocationPolicy_Custom(hostList,dataCenterIndex);
	}
	
	/**
	 * Starts all edge datacenters by parsing XML configuration and creating CloudSim entities.
	 * Reads the edge devices configuration document and creates a datacenter for each
	 * datacenter element defined in the XML, including hosts, VMs, and location information.
	 * 
	 * @throws Exception if XML parsing fails or datacenter creation encounters errors
	 */
	public void startDatacenters() throws Exception{
		Document doc = SimSettings.getInstance().getEdgeDevicesDocument();
		NodeList datacenterList = doc.getElementsByTagName("datacenter");
		for (int i = 0; i < datacenterList.getLength(); i++) {
			Node datacenterNode = datacenterList.item(i);
			Element datacenterElement = (Element) datacenterNode;
			localDatacenters.add(createDatacenter(i, datacenterElement));
		}
	}

	/**
	 * Creates VM lists for all edge hosts based on XML configuration.
	 * Parses the edge devices document and creates EdgeVM instances for each
	 * VM definition within host elements. Assigns bandwidth proportionally
	 * based on the number of hosts and VMs sharing WLAN resources.
	 * 	 *
	 * @param brokerId ID of the broker that will manage the created VMs
	 */
	public void createVmList(int brokerId){
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
				
				// Initialize VM list for current host
				vmList.add(hostCounter, new ArrayList<EdgeVM>());
				
				Node hostNode = hostNodeList.item(j);
				Element hostElement = (Element) hostNode;
				NodeList vmNodeList = hostElement.getElementsByTagName("VM");
				for (int k = 0; k < vmNodeList.getLength(); k++) {
					Node vmNode = vmNodeList.item(k);					
					Element vmElement = (Element) vmNode;

					// Extract VM configuration parameters from XML
					String vmm = vmElement.getAttribute("vmm");
					int numOfCores = Integer.parseInt(vmElement.getElementsByTagName("core").item(0).getTextContent());
					double mips = Double.parseDouble(vmElement.getElementsByTagName("mips").item(0).getTextContent());
					int ram = Integer.parseInt(vmElement.getElementsByTagName("ram").item(0).getTextContent());
					long storage = Long.parseLong(vmElement.getElementsByTagName("storage").item(0).getTextContent());
					// Calculate proportional bandwidth sharing across hosts and VMs
					long bandwidth = SimSettings.getInstance().getWlanBandwidth() / (hostNodeList.getLength()+vmNodeList.getLength());
					
					// Create EdgeVM with time-shared cloudlet scheduler
					EdgeVM vm = new EdgeVM(vmCounter, brokerId, mips, numOfCores, ram, bandwidth, storage, vmm, new CloudletSchedulerTimeShared());
					vmList.get(hostCounter).add(vm);
					vmCounter++;
				}

				hostCounter++;
			}
		}
	}
	
	/**
	 * Terminates all edge datacenters and shuts down their entities.
	 * Properly closes all datacenter instances to clean up simulation resources.
	 */
	public void terminateDatacenters(){
		for (Datacenter datacenter : localDatacenters) {
			datacenter.shutdownEntity();
		}
	}

	/**
	 * Calculates the average CPU utilization across all edge VMs.
	 * Iterates through all datacenters, hosts, and VMs to compute
	 * the overall resource usage across the edge infrastructure.
	 * 
	 * @return Average utilization percentage (0-100) across all edge VMs
	 */
	public double getAvgUtilization(){
		double totalUtilization = 0;
		double vmCounter = 0;
		
		// Iterate through each datacenter in the edge infrastructure
		for(int i= 0; i<localDatacenters.size(); i++) {
			List<? extends Host> list = localDatacenters.get(i).getHostList();
			// Iterate through each host within the datacenter
			for (int j=0; j < list.size(); j++) {
				Host host = list.get(j);
				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(host.getId());
				// Accumulate utilization from each VM on the host
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					totalUtilization += vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					vmCounter++;
				}
			}
		}
		return totalUtilization / vmCounter;
	}

	/**
	 * Creates a CloudSim datacenter from XML configuration element.
	 * Extracts datacenter characteristics, creates hosts, and assembles
	 * a complete datacenter entity with proper cost modeling and allocation policies.
	 * 
	 * @param index Unique index for datacenter naming
	 * @param datacenterElement XML element containing datacenter configuration
	 * @return Configured CloudSim Datacenter instance
	 * @throws Exception if datacenter creation fails
	 */
	private Datacenter createDatacenter(int index, Element datacenterElement) throws Exception{
		// Extract datacenter characteristics from XML configuration
		String arch = datacenterElement.getAttribute("arch");
		String os = datacenterElement.getAttribute("os");
		String vmm = datacenterElement.getAttribute("vmm");
		double costPerBw = Double.parseDouble(datacenterElement.getElementsByTagName("costPerBw").item(0).getTextContent());
		double costPerSec = Double.parseDouble(datacenterElement.getElementsByTagName("costPerSec").item(0).getTextContent());
		double costPerMem = Double.parseDouble(datacenterElement.getElementsByTagName("costPerMem").item(0).getTextContent());
		double costPerStorage = Double.parseDouble(datacenterElement.getElementsByTagName("costPerStorage").item(0).getTextContent());
		
		// Create hosts for this datacenter based on XML specification
		List<EdgeHost> hostList=createHosts(datacenterElement);
		
		// Configure datacenter basic properties
		String name = "Datacenter_" + Integer.toString(index);
		double time_zone = 3.0;         // Time zone for this edge datacenter location
		LinkedList<Storage> storageList = new LinkedList<Storage>();	// No SAN devices configured

		// Create datacenter characteristics object containing infrastructure properties:
		// architecture, OS, VM monitor, host list, time zone, and cost model
		DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, time_zone, costPerSec, costPerMem, costPerStorage, costPerBw);

		// Create the datacenter entity with custom VM allocation policy
		Datacenter datacenter = null;
	
		VmAllocationPolicy vm_policy = getVmAllocationPolicy(hostList,index);
		datacenter = new Datacenter(name, characteristics, vm_policy, storageList, 0);
		
		return datacenter;
	}
	
	/**
	 * Creates edge hosts from XML datacenter configuration element.
	 * Extracts location information and host specifications to create
	 * EdgeHost instances with proper geographic placement and resource configuration.
	 * 
	 * @param datacenterElement XML element containing datacenter and host definitions
	 * @return List of configured EdgeHost instances for the datacenter
	 */
	private List<EdgeHost> createHosts(Element datacenterElement){

		// Initialize list to store edge hosts for this datacenter
		List<EdgeHost> hostList = new ArrayList<EdgeHost>();
		
		// Extract location information for geographic placement
		Element location = (Element)datacenterElement.getElementsByTagName("location").item(0);
		String attractiveness = location.getElementsByTagName("attractiveness").item(0).getTextContent();
		int wlan_id = Integer.parseInt(location.getElementsByTagName("wlan_id").item(0).getTextContent());
		int x_pos = Integer.parseInt(location.getElementsByTagName("x_pos").item(0).getTextContent());
		int y_pos = Integer.parseInt(location.getElementsByTagName("y_pos").item(0).getTextContent());
		int placeTypeIndex = Integer.parseInt(attractiveness);

		// Process each host definition within the datacenter
		NodeList hostNodeList = datacenterElement.getElementsByTagName("host");
		for (int j = 0; j < hostNodeList.getLength(); j++) {
			Node hostNode = hostNodeList.item(j);
			
			// Extract host resource specifications from XML
			Element hostElement = (Element) hostNode;
			int numOfCores = Integer.parseInt(hostElement.getElementsByTagName("core").item(0).getTextContent());
			double mips = Double.parseDouble(hostElement.getElementsByTagName("mips").item(0).getTextContent());
			int ram = Integer.parseInt(hostElement.getElementsByTagName("ram").item(0).getTextContent());
			long storage = Long.parseLong(hostElement.getElementsByTagName("storage").item(0).getTextContent());
			// Calculate proportional bandwidth sharing across hosts
			long bandwidth = SimSettings.getInstance().getWlanBandwidth() / hostNodeList.getLength();
			
			// Create processing elements (PEs) for the host based on core count
			List<Pe> peList = new ArrayList<Pe>();

			// Create individual PEs (CPU cores) with specified MIPS rating
			for(int i=0; i<numOfCores; i++){
				peList.add(new Pe(i, new PeProvisionerSimple(mips))); // PE with unique ID and MIPS rating
			}
			
			// Create EdgeHost with resource provisioners and space-shared VM scheduling
			EdgeHost host = new EdgeHost(
					hostIdCounter,
					new RamProvisionerSimple(ram),        // Simple RAM allocation
					new BwProvisionerSimple(bandwidth),   // Bandwidth allocation (kbps)
					storage,                              // Storage capacity
					peList,                               // List of processing elements
					new VmSchedulerSpaceShared(peList)    // Space-shared VM scheduling policy
				);
			
			// Set geographic location for edge-aware placement decisions
			host.setPlace(new Location(placeTypeIndex, wlan_id, x_pos, y_pos));
			hostList.add(host);
			hostIdCounter++;
		}

		return hostList;
	}
}
