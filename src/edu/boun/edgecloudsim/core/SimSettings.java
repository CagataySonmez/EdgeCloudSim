/*
 * Title:        EdgeCloudSim - Simulation Settings class
 * 
 * Description: 
 * SimSettings provides system wide simulation settings. It is a
 * singleton class and provides all necessary information to other modules.
 * If you need to use another simulation setting variable in your
 * config file, add related getter methot in this class.
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.boun.edgecloudsim.utils.SimLogger;

public class SimSettings {
	private static SimSettings instance = null;
	private Document edgeDevicesDoc = null;
	
	public static final double CLIENT_ACTIVITY_START_TIME = 10;
	
	//enumarations for the VM types
	public static enum VM_TYPES { MOBILE_VM, EDGE_VM, CLOUD_VM }
	
	//enumarations for the VM types
	public static enum NETWORK_DELAY_TYPES { WLAN_DELAY, MAN_DELAY, WAN_DELAY }
	
	//predifined IDs for the components.
	public static final int CLOUD_DATACENTER_ID = 1000;
	public static final int MOBILE_DATACENTER_ID = 1001;
	public static final int EDGE_ORCHESTRATOR_ID = 1002;
	public static final int GENERIC_EDGE_DEVICE_ID = 1003;

	//delimiter for output file.
	public static final String DELIMITER = ";";
	
    private double SIMULATION_TIME; //minutes unit in properties file
    private double WARM_UP_PERIOD; //minutes unit in properties file
    private double INTERVAL_TO_GET_VM_LOAD_LOG; //minutes unit in properties file
    private double INTERVAL_TO_GET_VM_LOCATION_LOG; //minutes unit in properties file
    private boolean FILE_LOG_ENABLED; //boolean to check file logging option
    private boolean DEEP_FILE_LOG_ENABLED; //boolean to check deep file logging option

    private int MIN_NUM_OF_MOBILE_DEVICES;
    private int MAX_NUM_OF_MOBILE_DEVICES;
    private int MOBILE_DEVICE_COUNTER_SIZE;
    
    private int NUM_OF_EDGE_DATACENTERS;
    private int NUM_OF_EDGE_HOSTS;
    private int NUM_OF_EDGE_VMS;
    private int NUM_OF_PLACE_TYPES;
    
    private double WAN_PROPOGATION_DELAY; //seconds unit in properties file
    private double LAN_INTERNAL_DELAY; //seconds unit in properties file
    private int BANDWITH_WLAN; //Mbps unit in properties file
    private int BANDWITH_WAN; //Mbps unit in properties file
    private int BANDWITH_GSM; //Mbps unit in properties file

    private int NUM_OF_HOST_ON_CLOUD_DATACENTER;
    private int NUM_OF_VM_ON_CLOUD_HOST;
    private int CORE_FOR_CLOUD_VM;
    private int MIPS_FOR_CLOUD_VM; //MIPS
    private int RAM_FOR_CLOUD_VM; //MB
	private int STORAGE_FOR_CLOUD_VM; //Byte
    
    private int CORE_FOR_VM;
    private int MIPS_FOR_VM; //MIPS
    private int RAM_FOR_VM; //MB
    private int STORAGE_FOR_VM; //Byte
    
    private String[] SIMULATION_SCENARIOS;
    private String[] ORCHESTRATOR_POLICIES;
    
    // mean waiting time (minute) is stored for each place types
    private double[] mobilityLookUpTable;
    
    // following values are stored for each applications defined in applications.xml
    // [0] usage percentage (%)
    // [1] prob. of selecting cloud (%)
    // [2] poisson mean (sec)
    // [3] active period (sec)
    // [4] idle period (sec)
    // [5] avg data upload (KB)
    // [6] avg data download (KB)
    // [7] avg task length (MI)
    // [8] required # of cores
    // [9] vm utilization on edge (%)
    // [10] vm utilization on cloud (%)
    // [11] vm utilization on mobile (%)
    // [12] delay sensitivity [0-1]
    private double[][] taskLookUpTable = null;
    
    private String[] taskNames = null;

	private SimSettings() {
		NUM_OF_PLACE_TYPES = 0;
	}
	
	public static SimSettings getInstance() {
		if(instance == null) {
			instance = new SimSettings();
		}
		return instance;
	}
	
	/**
	 * Reads configuration file and stores information to local variables
	 * @param propertiesFile
	 * @return
	 */
	public boolean initialize(String propertiesFile, String edgeDevicesFile, String applicationsFile){
		boolean result = false;
		InputStream input = null;
		try {
			input = new FileInputStream(propertiesFile);

			// load a properties file
			Properties prop = new Properties();
			prop.load(input);

			SIMULATION_TIME = (double)60 * Double.parseDouble(prop.getProperty("simulation_time")); //seconds
			WARM_UP_PERIOD = (double)60 * Double.parseDouble(prop.getProperty("warm_up_period")); //seconds
			INTERVAL_TO_GET_VM_LOAD_LOG = (double)60 * Double.parseDouble(prop.getProperty("vm_load_check_interval")); //seconds
			INTERVAL_TO_GET_VM_LOCATION_LOG = (double)60 * Double.parseDouble(prop.getProperty("vm_location_check_interval")); //seconds
			FILE_LOG_ENABLED = Boolean.parseBoolean(prop.getProperty("file_log_enabled"));
			DEEP_FILE_LOG_ENABLED = Boolean.parseBoolean(prop.getProperty("deep_file_log_enabled"));
			
			MIN_NUM_OF_MOBILE_DEVICES = Integer.parseInt(prop.getProperty("min_number_of_mobile_devices"));
			MAX_NUM_OF_MOBILE_DEVICES = Integer.parseInt(prop.getProperty("max_number_of_mobile_devices"));
			MOBILE_DEVICE_COUNTER_SIZE = Integer.parseInt(prop.getProperty("mobile_device_counter_size"));
			
			WAN_PROPOGATION_DELAY = Double.parseDouble(prop.getProperty("wan_propogation_delay"));
			LAN_INTERNAL_DELAY = Double.parseDouble(prop.getProperty("lan_internal_delay"));
			BANDWITH_WLAN = 1000 * Integer.parseInt(prop.getProperty("wlan_bandwidth"));
			BANDWITH_WAN = 1000 * Integer.parseInt(prop.getProperty("wan_bandwidth"));
			BANDWITH_GSM =  1000 * Integer.parseInt(prop.getProperty("gsm_bandwidth"));

		    NUM_OF_HOST_ON_CLOUD_DATACENTER = Integer.parseInt(prop.getProperty("number_of_host_on_cloud_datacenter"));
		    NUM_OF_VM_ON_CLOUD_HOST = Integer.parseInt(prop.getProperty("number_of_vm_on_cloud_host"));
		    CORE_FOR_CLOUD_VM = Integer.parseInt(prop.getProperty("core_for_cloud_vm"));
		    MIPS_FOR_CLOUD_VM = Integer.parseInt(prop.getProperty("mips_for_cloud_vm"));
		    RAM_FOR_CLOUD_VM = Integer.parseInt(prop.getProperty("ram_for_cloud_vm"));
			STORAGE_FOR_CLOUD_VM = Integer.parseInt(prop.getProperty("storage_for_cloud_vm"));

			RAM_FOR_VM = Integer.parseInt(prop.getProperty("ram_for_mobile_vm"));
			CORE_FOR_VM = Integer.parseInt(prop.getProperty("core_for_mobile_vm"));
			MIPS_FOR_VM = Integer.parseInt(prop.getProperty("mips_for_mobile_vm"));
			STORAGE_FOR_VM = Integer.parseInt(prop.getProperty("storage_for_mobile_vm"));

			ORCHESTRATOR_POLICIES = prop.getProperty("orchestrator_policies").split(",");
			
			SIMULATION_SCENARIOS = prop.getProperty("simulation_scenarios").split(",");
			
			//avg waiting time in a place (min)
			double place1_mean_waiting_time = Double.parseDouble(prop.getProperty("attractiveness_L1_mean_waiting_time"));
			double place2_mean_waiting_time = Double.parseDouble(prop.getProperty("attractiveness_L2_mean_waiting_time"));
			double place3_mean_waiting_time = Double.parseDouble(prop.getProperty("attractiveness_L3_mean_waiting_time"));
			
			//mean waiting time (minute)
			mobilityLookUpTable = new double[]{
				place1_mean_waiting_time, //ATTRACTIVENESS_L1
				place2_mean_waiting_time, //ATTRACTIVENESS_L2
				place3_mean_waiting_time  //ATTRACTIVENESS_L3
		    };
			

		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			if (input != null) {
				try {
					input.close();
					result = true;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		parseApplicatinosXML(applicationsFile);
		parseEdgeDevicesXML(edgeDevicesFile);
		
		return result;
	}
	
	/**
	 * returns the parsed XML document for edge_devices.xml
	 */
	public Document getEdgeDevicesDocument(){
		return edgeDevicesDoc;
	}


	/**
	 * returns simulation time (in seconds unit) from properties file
	 */
	public double getSimulationTime()
	{
		return SIMULATION_TIME;
	}

	/**
	 * returns warm up period (in seconds unit) from properties file
	 */
	public double getWarmUpPeriod()
	{
		return WARM_UP_PERIOD; 
	}

	/**
	 * returns VM utilization log collection interval (in seconds unit) from properties file
	 */
	public double getVmLoadLogInterval()
	{
		return INTERVAL_TO_GET_VM_LOAD_LOG; 
	}

	/**
	 * returns VM location log collection interval (in seconds unit) from properties file
	 */
	public double getVmLocationLogInterval()
	{
		return INTERVAL_TO_GET_VM_LOCATION_LOG; 
	}

	/**
	 * returns deep statistics logging status from properties file
	 */
	public boolean getDeepFileLoggingEnabled()
	{
		return DEEP_FILE_LOG_ENABLED; 
	}

	/**
	 * returns deep statistics logging status from properties file
	 */
	public boolean getFileLoggingEnabled()
	{
		return FILE_LOG_ENABLED; 
	}
	
	/**
	 * returns WAN propogation delay (in second unit) from properties file
	 */
	public double getWanPropogationDelay()
	{
		return WAN_PROPOGATION_DELAY;
	}

	/**
	 * returns internal LAN propogation delay (in second unit) from properties file
	 */
	public double getInternalLanDelay()
	{
		return LAN_INTERNAL_DELAY;
	}

	/**
	 * returns WLAN bandwidth (in Mbps unit) from properties file
	 */
	public int getWlanBandwidth()
	{
		return BANDWITH_WLAN;
	}

	/**
	 * returns WAN bandwidth (in Mbps unit) from properties file
	 */
	public int getWanBandwidth()
	{
		return BANDWITH_WAN; 
	}

	/**
	 * returns GSM bandwidth (in Mbps unit) from properties file
	 */
	public int getGsmBandwidth()
	{
		return BANDWITH_GSM;
	}
	
	/**
	 * returns the minimum number of the mobile devices used in the simulation
	 */
	public int getMinNumOfMobileDev()
	{
		return MIN_NUM_OF_MOBILE_DEVICES;
	}

	/**
	 * returns the maximunm number of the mobile devices used in the simulation
	 */
	public int getMaxNumOfMobileDev()
	{
		return MAX_NUM_OF_MOBILE_DEVICES;
	}

	/**
	 * returns the number of increase on mobile devices
	 * while iterating from min to max mobile device
	 */
	public int getMobileDevCounterSize()
	{
		return MOBILE_DEVICE_COUNTER_SIZE;
	}

	/**
	 * returns the number of edge datacenters
	 */
	public int getNumOfEdgeDatacenters()
	{
		return NUM_OF_EDGE_DATACENTERS;
	}

	/**
	 * returns the number of edge hosts running on the datacenters
	 */
	public int getNumOfEdgeHosts()
	{
		return NUM_OF_EDGE_HOSTS;
	}

	/**
	 * returns the number of edge VMs running on the hosts
	 */
	public int getNumOfEdgeVMs()
	{
		return NUM_OF_EDGE_VMS;
	}
	
	/**
	 * returns the number of different place types
	 */
	public int getNumOfPlaceTypes()
	{
		return NUM_OF_PLACE_TYPES;
	}

	/**
	 * returns the number of cloud datacenters
	 */
	public int getNumOfCoudHost()
	{
		return NUM_OF_HOST_ON_CLOUD_DATACENTER;
	}
	
	/**
	 * returns the number of cloud VMs per Host
	 */
	public int getNumOfCloudVMsPerHost()
	{
		return NUM_OF_VM_ON_CLOUD_HOST;
	}
	
	/**
	 * returns the total number of cloud VMs
	 */
	public int getNumOfCloudVMs()
	{
		return NUM_OF_VM_ON_CLOUD_HOST * NUM_OF_HOST_ON_CLOUD_DATACENTER;
	}
	
	/**
	 * returns the number of cores for cloud VMs
	 */
	public int getCoreForCloudVM()
	{
		return CORE_FOR_CLOUD_VM;
	}
	
	/**
	 * returns MIPS of the central cloud VMs
	 */
	public int getMipsForCloudVM()
	{
		return MIPS_FOR_CLOUD_VM;
	}
	
	/**
	 * returns RAM of the central cloud VMs
	 */
	public int getRamForCloudVM()
	{
		return RAM_FOR_CLOUD_VM;
	}
	
	/**
	 * returns Storage of the central cloud VMs
	 */
	public int getStorageForCloudVM()
	{
		return STORAGE_FOR_CLOUD_VM;
	}
	
	/**
	 * returns RAM of the mobile (processing unit) VMs
	 */
	public int getRamForMobileVM()
	{
		return RAM_FOR_VM;
	}
	
	/**
	 * returns the number of cores for mobile VMs
	 */
	public int getCoreForMobileVM()
	{
		return CORE_FOR_VM;
	}
	
	/**
	 * returns MIPS of the mobile (processing unit) VMs
	 */
	public int getMipsForMobileVM()
	{
		return MIPS_FOR_VM;
	}
	
	/**
	 * returns Storage of the mobile (processing unit) VMs
	 */
	public int getStorageForMobileVM()
	{
		return STORAGE_FOR_VM;
	}

	/**
	 * returns simulation screnarios as string
	 */
	public String[] getSimulationScenarios()
	{
		return SIMULATION_SCENARIOS;
	}

	/**
	 * returns orchestrator policies as string
	 */
	public String[] getOrchestratorPolicies()
	{
		return ORCHESTRATOR_POLICIES;
	}
	
	/**
	 * returns mobility characteristic within an array
	 * the result includes mean waiting time (minute) or each place type
	 */ 
	public double[] getMobilityLookUpTable()
	{
		return mobilityLookUpTable;
	}

	/**
	 * returns application characteristic within two dimensional array
	 * the result includes the following values for each application type
	 * [0] usage percentage (%)
	 * [1] prob. of selecting cloud (%)
	 * [2] poisson mean (sec)
	 * [3] active period (sec)
	 * [4] idle period (sec)
	 * [5] avg data upload (KB)
	 * [6] avg data download (KB)
	 * [7] avg task length (MI)
	 * [8] required # of cores
	 * [9] vm utilization on edge (%)
	 * [10] vm utilization on cloud (%)
	 * [11] vm utilization on mobile (%)
	 * [12] delay sensitivity [0-1]
	 */ 
	public double[][] getTaskLookUpTable()
	{
		return taskLookUpTable;
	}
	
	public String getTaskName(int taskType)
	{
		return taskNames[taskType];
	}
	
	private void isAttribtuePresent(Element element, String key) {
        String value = element.getAttribute(key);
        if (value.isEmpty() || value == null){
        	throw new IllegalArgumentException("Attribure '" + key + "' is not found in '" + element.getNodeName() +"'");
        }
	}

	private void isElementPresent(Element element, String key) {
		try {
			String value = element.getElementsByTagName(key).item(0).getTextContent();
	        if (value.isEmpty() || value == null){
	        	throw new IllegalArgumentException("Element '" + key + "' is not found in '" + element.getNodeName() +"'");
	        }
		} catch (Exception e) {
			throw new IllegalArgumentException("Element '" + key + "' is not found in '" + element.getNodeName() +"'");
		}
	}
	
	private void parseApplicatinosXML(String filePath)
	{
		Document doc = null;
		try {	
			File devicesFile = new File(filePath);
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			doc = dBuilder.parse(devicesFile);
			doc.getDocumentElement().normalize();

			NodeList appList = doc.getElementsByTagName("application");
			taskLookUpTable = new double[appList.getLength()][13];
			taskNames = new String[appList.getLength()];
			for (int i = 0; i < appList.getLength(); i++) {
				Node appNode = appList.item(i);
	
				Element appElement = (Element) appNode;
				isAttribtuePresent(appElement, "name");
				isElementPresent(appElement, "usage_percentage");
				isElementPresent(appElement, "prob_cloud_selection");
				isElementPresent(appElement, "poisson_interarrival");
				isElementPresent(appElement, "active_period");
				isElementPresent(appElement, "idle_period");
				isElementPresent(appElement, "data_upload");
				isElementPresent(appElement, "data_download");
				isElementPresent(appElement, "task_length");
				isElementPresent(appElement, "required_core");
				isElementPresent(appElement, "vm_utilization_on_edge");
				isElementPresent(appElement, "vm_utilization_on_cloud");
				isElementPresent(appElement, "vm_utilization_on_mobile");
				isElementPresent(appElement, "delay_sensitivity");

				String taskName = appElement.getAttribute("name");
				taskNames[i] = taskName;
				
				double usage_percentage = Double.parseDouble(appElement.getElementsByTagName("usage_percentage").item(0).getTextContent());
				double prob_cloud_selection = Double.parseDouble(appElement.getElementsByTagName("prob_cloud_selection").item(0).getTextContent());
				double poisson_interarrival = Double.parseDouble(appElement.getElementsByTagName("poisson_interarrival").item(0).getTextContent());
				double active_period = Double.parseDouble(appElement.getElementsByTagName("active_period").item(0).getTextContent());
				double idle_period = Double.parseDouble(appElement.getElementsByTagName("idle_period").item(0).getTextContent());
				double data_upload = Double.parseDouble(appElement.getElementsByTagName("data_upload").item(0).getTextContent());
				double data_download = Double.parseDouble(appElement.getElementsByTagName("data_download").item(0).getTextContent());
				double task_length = Double.parseDouble(appElement.getElementsByTagName("task_length").item(0).getTextContent());
				double required_core = Double.parseDouble(appElement.getElementsByTagName("required_core").item(0).getTextContent());
				double vm_utilization_on_edge = Double.parseDouble(appElement.getElementsByTagName("vm_utilization_on_edge").item(0).getTextContent());
				double vm_utilization_on_cloud = Double.parseDouble(appElement.getElementsByTagName("vm_utilization_on_cloud").item(0).getTextContent());
				double vm_utilization_on_mobile = Double.parseDouble(appElement.getElementsByTagName("vm_utilization_on_mobile").item(0).getTextContent());
				double delay_sensitivity = Double.parseDouble(appElement.getElementsByTagName("delay_sensitivity").item(0).getTextContent());
				
			    taskLookUpTable[i][0] = usage_percentage; //usage percentage [0-100]
			    taskLookUpTable[i][1] = prob_cloud_selection; //prob. of selecting cloud [0-100]
			    taskLookUpTable[i][2] = poisson_interarrival; //poisson mean (sec)
			    taskLookUpTable[i][3] = active_period; //active period (sec)
			    taskLookUpTable[i][4] = idle_period; //idle period (sec)
			    taskLookUpTable[i][5] = data_upload; //avg data upload (KB)
			    taskLookUpTable[i][6] = data_download; //avg data download (KB)
			    taskLookUpTable[i][7] = task_length; //avg task length (MI)
			    taskLookUpTable[i][8] = required_core; //required # of core
			    taskLookUpTable[i][9] = vm_utilization_on_edge; //vm utilization on edge vm [0-100]
			    taskLookUpTable[i][10] = vm_utilization_on_cloud; //vm utilization on cloud vm [0-100]
			    taskLookUpTable[i][11] = vm_utilization_on_mobile; //vm utilization on mobile vm [0-100]
			    taskLookUpTable[i][12] = delay_sensitivity; //delay_sensitivity [0-1]
			}
	
		} catch (Exception e) {
			SimLogger.printLine("Edge Devices XML cannot be parsed! Terminating simulation...");
			e.printStackTrace();
			System.exit(0);
		}
	}

	private void parseEdgeDevicesXML(String filePath)
	{
		try {	
			File devicesFile = new File(filePath);
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			edgeDevicesDoc = dBuilder.parse(devicesFile);
			edgeDevicesDoc.getDocumentElement().normalize();

			NodeList datacenterList = edgeDevicesDoc.getElementsByTagName("datacenter");
			for (int i = 0; i < datacenterList.getLength(); i++) {
			    NUM_OF_EDGE_DATACENTERS++;
				Node datacenterNode = datacenterList.item(i);
	
				Element datacenterElement = (Element) datacenterNode;
				isAttribtuePresent(datacenterElement, "arch");
				isAttribtuePresent(datacenterElement, "os");
				isAttribtuePresent(datacenterElement, "vmm");
				isElementPresent(datacenterElement, "costPerBw");
				isElementPresent(datacenterElement, "costPerSec");
				isElementPresent(datacenterElement, "costPerMem");
				isElementPresent(datacenterElement, "costPerStorage");

				Element location = (Element)datacenterElement.getElementsByTagName("location").item(0);
				isElementPresent(location, "attractiveness");
				isElementPresent(location, "wlan_id");
				isElementPresent(location, "x_pos");
				isElementPresent(location, "y_pos");
				
				String attractiveness = location.getElementsByTagName("attractiveness").item(0).getTextContent();
				int placeTypeIndex = Integer.parseInt(attractiveness);
				if(NUM_OF_PLACE_TYPES < placeTypeIndex+1)
					NUM_OF_PLACE_TYPES = placeTypeIndex+1;

				NodeList hostList = datacenterElement.getElementsByTagName("host");
				for (int j = 0; j < hostList.getLength(); j++) {
				    NUM_OF_EDGE_HOSTS++;
					Node hostNode = hostList.item(j);
					
					Element hostElement = (Element) hostNode;
					isElementPresent(hostElement, "core");
					isElementPresent(hostElement, "mips");
					isElementPresent(hostElement, "ram");
					isElementPresent(hostElement, "storage");

					NodeList vmList = hostElement.getElementsByTagName("VM");
					for (int k = 0; k < vmList.getLength(); k++) {
					    NUM_OF_EDGE_VMS++;
						Node vmNode = vmList.item(k);
						
						Element vmElement = (Element) vmNode;
						isAttribtuePresent(vmElement, "vmm");
						isElementPresent(vmElement, "core");
						isElementPresent(vmElement, "mips");
						isElementPresent(vmElement, "ram");
						isElementPresent(vmElement, "storage");
					}
				}
			}
	
		} catch (Exception e) {
			SimLogger.printLine("Edge Devices XML cannot be parsed! Terminating simulation...");
			e.printStackTrace();
			System.exit(0);
		}
	}
}
