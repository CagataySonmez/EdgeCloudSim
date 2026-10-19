/* SPDX-License-Identifier: GPL-3.0-or-later */
/*
 * Title:        EdgeCloudSim - Simulation Settings class
 *
 * Description:
 * SimSettings provides system wide simulation settings. It is a
 * singleton class and provides all necessary information to other modules.
 * If you need to use another simulation setting variable in your
 * config file, add related getter method in this class.
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

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.boun.edgecloudsim.utils.SimLogger;

/**
 * Singleton class providing system-wide simulation settings for EdgeCloudSim.
 * Loads configuration from properties and XML files and provides centralized
 * access to all simulation parameters including network settings, device
 * configurations, and orchestration policies.
 */
public class SimSettings {
	private static SimSettings instance = null;
	private Document edgeDevicesDoc = null;

	/** Simulation time when client activities begin (in seconds) */
	public static final double CLIENT_ACTIVITY_START_TIME = 10;

	/** Identifies the execution tier of a virtual machine. */
	public static enum VM_TYPES {
		/** Virtual machine hosted by a mobile device. */
		MOBILE_VM,
		/** Virtual machine hosted by an edge server. */
		EDGE_VM,
		/** Virtual machine hosted by the cloud datacenter. */
		CLOUD_VM
	}

	/** Identifies the network segment associated with a measured delay. */
	public static enum NETWORK_DELAY_TYPES {
		/** Wireless-local-area-network delay. */
		WLAN_DELAY,
		/** Metropolitan-area-network delay. */
		MAN_DELAY,
		/** Wide-area-network delay. */
		WAN_DELAY,
		/** Cellular-network delay. */
		GSM_DELAY
	}

	/** Cloud datacenter entity identifier. */
	public static final int CLOUD_DATACENTER_ID = 1000;
	/** Mobile datacenter entity identifier. */
	public static final int MOBILE_DATACENTER_ID = 1001;
	/** Edge orchestrator entity identifier. */
	public static final int EDGE_ORCHESTRATOR_ID = 1002;
	/** Generic edge-device entity identifier. */
	public static final int GENERIC_EDGE_DEVICE_ID = 1003;

	/** Delimiter used in output files for data separation */
	public static final String DELIMITER = ";";

	// Simulation timing parameters (converted from minutes in properties file)
	private double SIMULATION_TIME;
	private double WARM_UP_PERIOD;
	private double INTERVAL_TO_GET_VM_LOAD_LOG;
	private double INTERVAL_TO_GET_LOCATION_LOG;
	private double INTERVAL_TO_GET_AP_DELAY_LOG;

	// Logging configuration parameters
	private boolean FILE_LOG_ENABLED;
	private boolean DEEP_FILE_LOG_ENABLED;

	// Mobile device configuration parameters
	private int MIN_NUM_OF_MOBILE_DEVICES;
	private int MAX_NUM_OF_MOBILE_DEVICES;
	private int MOBILE_DEVICE_COUNTER_SIZE;
	private int WLAN_RANGE;

	// Edge infrastructure configuration parameters
	private int NUM_OF_EDGE_DATACENTERS;
	private int NUM_OF_EDGE_HOSTS;
	private int NUM_OF_EDGE_VMS;
	private int NUM_OF_PLACE_TYPES;

	// Network delay and bandwidth parameters (converted from properties file units)
	private double WAN_PROPAGATION_DELAY;  // Wide Area Network delay (seconds)
	private double GSM_PROPAGATION_DELAY;  // GSM network delay (seconds)
	private double LAN_INTERNAL_DELAY;     // Local Area Network delay (seconds)
	private int bandwidthWlan;              // WLAN bandwidth (Kbps)
	private int bandwidthMan;               // Metropolitan Area Network bandwidth (Kbps)
	private int bandwidthWan;               // Wide Area Network bandwidth (Kbps)
	private int bandwidthGsm;               // GSM network bandwidth (Kbps)

	// Cloud infrastructure configuration parameters
	private int NUM_OF_HOST_ON_CLOUD_DATACENTER;
	private int NUM_OF_VM_ON_CLOUD_HOST;
	private int CORE_FOR_CLOUD_VM;         // CPU cores for cloud VMs
	private int MIPS_FOR_CLOUD_VM;         // Processing power (MIPS)
	private int RAM_FOR_CLOUD_VM;          // Memory allocation (MB)
	private int STORAGE_FOR_CLOUD_VM;     // Storage allocation (Bytes)

	// Edge VM resource configuration parameters
	private int CORE_FOR_VM;               // CPU cores for edge VMs
	private int MIPS_FOR_VM;               // Processing power (MIPS)
	private int RAM_FOR_VM;                // Memory allocation (MB)
	private int STORAGE_FOR_VM;           // Storage allocation (Bytes)

	// Simulation scenario and policy configuration
	private String[] SIMULATION_SCENARIOS;
	private String[] ORCHESTRATOR_POLICIES;

	// Geographic simulation boundaries
	private double NORTHERN_BOUND;
	private double EASTERN_BOUND;
	private double SOUTHERN_BOUND;
	private double WESTERN_BOUND;

	// Mobility model configuration - mean waiting time (minutes) for each place type
	private double[] mobilityLookUpTable;

	/**
	 * Application task lookup table storing parameters for each application type defined in applications.xml
	 * Array indices represent different application characteristics:
	 * [0] Usage percentage (%)
	 * [1] Probability of selecting cloud (%)
	 * [2] Poisson mean arrival rate (seconds)
	 * [3] Active period duration (seconds)
	 * [4] Idle period duration (seconds)
	 * [5] Average data upload size (KB)
	 * [6] Average data download size (KB)
	 * [7] Average task length (MI - Million Instructions)
	 * [8] Required number of CPU cores
	 * [9] VM utilization on edge nodes (%)
	 * [10] VM utilization on cloud (%)
	 * [11] VM utilization on mobile devices (%)
	 * [12] Delay sensitivity factor [0-1]
	 */
	private double[][] taskLookUpTable = null;

	// Application type names corresponding to taskLookUpTable entries
	private String[] taskNames = null;

	/**
	 * Private constructor for singleton pattern implementation.
	 * Initializes place type counter for mobility configuration.
	 */
	private SimSettings() {
		NUM_OF_PLACE_TYPES = 0;
	}

	/**
	 * Gets the singleton instance of SimSettings.
	 * Creates a new instance if one doesn't already exist (lazy initialization).
	 *
	 * @return The singleton SimSettings instance for global configuration access
	 */
	public static SimSettings getInstance() {
		if(instance == null) {
			instance = new SimSettings();
		}
		return instance;
	}

	/**
	 * Initializes simulation settings from configuration files.
	 * Loads properties from the main configuration file, edge device definitions,
	 * and application specifications to configure the simulation environment.
	 *
	 * @param propertiesFile Path to the main simulation properties file
	 * @param edgeDevicesFile Path to the edge devices configuration XML file
	 * @param applicationsFile Path to the applications configuration XML file
	 * @return true if all configuration files loaded successfully, false otherwise
	 */
	public boolean initialize(String propertiesFile, String edgeDevicesFile, String applicationsFile){
		try (InputStream input = new FileInputStream(propertiesFile)) {

			// load a properties file
			Properties prop = new Properties();
			prop.load(input);

			SIMULATION_TIME = (double)60 * Double.parseDouble(prop.getProperty("simulation_time")); //seconds
			WARM_UP_PERIOD = (double)60 * Double.parseDouble(prop.getProperty("warm_up_period")); //seconds
			INTERVAL_TO_GET_VM_LOAD_LOG = (double)60 * Double.parseDouble(prop.getProperty("vm_load_check_interval")); //seconds
			INTERVAL_TO_GET_LOCATION_LOG = (double)60 * Double.parseDouble(prop.getProperty("location_check_interval")); //seconds
			INTERVAL_TO_GET_AP_DELAY_LOG = (double)60 * Double.parseDouble(prop.getProperty("ap_delay_check_interval", "0")); //seconds
			FILE_LOG_ENABLED = Boolean.parseBoolean(prop.getProperty("file_log_enabled"));
			DEEP_FILE_LOG_ENABLED = Boolean.parseBoolean(prop.getProperty("deep_file_log_enabled"));

			MIN_NUM_OF_MOBILE_DEVICES = Integer.parseInt(prop.getProperty("min_number_of_mobile_devices"));
			MAX_NUM_OF_MOBILE_DEVICES = Integer.parseInt(prop.getProperty("max_number_of_mobile_devices"));
			MOBILE_DEVICE_COUNTER_SIZE = Integer.parseInt(prop.getProperty("mobile_device_counter_size"));
			WLAN_RANGE = Integer.parseInt(prop.getProperty("wlan_range", "0"));

			WAN_PROPAGATION_DELAY = Double.parseDouble(prop.getProperty("wan_propagation_delay", "0"));
			GSM_PROPAGATION_DELAY = Double.parseDouble(prop.getProperty("gsm_propagation_delay", "0"));
			LAN_INTERNAL_DELAY = Double.parseDouble(prop.getProperty("lan_internal_delay", "0"));
			bandwidthWlan = 1000 * Integer.parseInt(prop.getProperty("wlan_bandwidth"));
			bandwidthMan = 1000 * Integer.parseInt(prop.getProperty("man_bandwidth", "0"));
			bandwidthWan = 1000 * Integer.parseInt(prop.getProperty("wan_bandwidth", "0"));
			bandwidthGsm =  1000 * Integer.parseInt(prop.getProperty("gsm_bandwidth", "0"));

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

			ORCHESTRATOR_POLICIES = splitAndTrim(prop.getProperty("orchestrator_policies"));

			SIMULATION_SCENARIOS = splitAndTrim(prop.getProperty("simulation_scenarios"));

			NORTHERN_BOUND = Double.parseDouble(prop.getProperty("northern_bound", "0"));
			SOUTHERN_BOUND = Double.parseDouble(prop.getProperty("southern_bound", "0"));
			EASTERN_BOUND = Double.parseDouble(prop.getProperty("eastern_bound", "0"));
			WESTERN_BOUND = Double.parseDouble(prop.getProperty("western_bound", "0"));

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

			validateConfiguration();
			parseApplicationsXML(applicationsFile);
			validateApplicationTable();
			NUM_OF_EDGE_DATACENTERS = 0;
			NUM_OF_EDGE_HOSTS = 0;
			NUM_OF_EDGE_VMS = 0;
			NUM_OF_PLACE_TYPES = 0;
			parseEdgeDevicesXML(edgeDevicesFile);
			if (NUM_OF_EDGE_DATACENTERS <= 0 || NUM_OF_EDGE_HOSTS <= 0 || NUM_OF_EDGE_VMS <= 0) {
				throw new IllegalArgumentException("Edge-device XML must define at least one datacenter, host, and VM");
			}
			if (NUM_OF_PLACE_TYPES > mobilityLookUpTable.length) {
				throw new IllegalArgumentException("Edge-device attractiveness index exceeds the configured mobility levels");
			}
			return true;
		} catch (IOException | RuntimeException exception) {
			SimLogger.printLine("Simulation settings cannot be initialized: " + exception.getMessage());
			return false;
		}
	}

	private static String[] splitAndTrim(String value) {
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException("Scenario and policy lists cannot be empty");
		}
		String[] values = value.split(",");
		for (int i = 0; i < values.length; i++) {
			values[i] = values[i].trim();
			if (values[i].isEmpty()) {
				throw new IllegalArgumentException("Scenario and policy list entries cannot be empty");
			}
		}
		return values;
	}

	private void validateConfiguration() {
		if (SIMULATION_TIME <= 0 || WARM_UP_PERIOD < 0 || WARM_UP_PERIOD >= SIMULATION_TIME) {
			throw new IllegalArgumentException("Simulation time must be positive and exceed the warm-up period");
		}
		if (MIN_NUM_OF_MOBILE_DEVICES < 0 || MAX_NUM_OF_MOBILE_DEVICES < MIN_NUM_OF_MOBILE_DEVICES
				|| MOBILE_DEVICE_COUNTER_SIZE <= 0) {
			throw new IllegalArgumentException("Mobile-device count settings are inconsistent");
		}
		if (bandwidthWlan < 0 || bandwidthMan < 0 || bandwidthWan < 0 || bandwidthGsm < 0
				|| NUM_OF_HOST_ON_CLOUD_DATACENTER <= 0
				|| NUM_OF_VM_ON_CLOUD_HOST <= 0 || CORE_FOR_CLOUD_VM <= 0
				|| MIPS_FOR_CLOUD_VM <= 0 || RAM_FOR_CLOUD_VM <= 0 || STORAGE_FOR_CLOUD_VM <= 0) {
			throw new IllegalArgumentException("Bandwidths cannot be negative and cloud resource values must be positive");
		}
	}

	private void validateApplicationTable() {
		if (taskLookUpTable == null || taskLookUpTable.length == 0) {
			throw new IllegalArgumentException("Applications XML must define at least one application");
		}
		double totalUsagePercentage = 0;
		for (int i = 0; i < taskLookUpTable.length; i++) {
			double[] application = taskLookUpTable[i];
			totalUsagePercentage += application[0];
			if (application[0] < 0 || application[0] > 100
					|| application[1] < 0 || application[1] > 100
					|| application[2] <= 0 || application[3] <= 0 || application[4] < 0
					|| application[5] <= 0 || application[6] <= 0 || application[7] <= 0
					|| application[8] <= 0
					|| application[9] < 0 || application[9] > 100
					|| application[10] < 0 || application[10] > 100
					|| application[11] < 0 || application[11] > 100
					|| application[12] < 0 || application[12] > 1
					|| application[13] < 0) {
				throw new IllegalArgumentException("Invalid numeric value for application " + taskNames[i]);
			}
		}
		if (Math.abs(totalUsagePercentage - 100) > 1.0e-9) {
			throw new IllegalArgumentException("Application usage percentages must sum to 100, but sum to "
					+ totalUsagePercentage);
		}
	}

	/**
	 * Returns the parsed edge-device configuration.
	 *
	 * @return DOM document loaded from {@code edge_devices.xml}
	 */
	public Document getEdgeDevicesDocument(){
		return edgeDevicesDoc;
	}


	/**
	 * Returns the configured simulation duration.
	 *
	 * @return simulation duration in seconds
	 */
	public double getSimulationTime()
	{
		return SIMULATION_TIME;
	}

	/**
	 * Returns the warm-up period excluded from steady-state statistics.
	 *
	 * @return warm-up duration in seconds
	 */
	public double getWarmUpPeriod()
	{
		return WARM_UP_PERIOD;
	}

	/**
	 * Returns the VM-utilization sampling interval.
	 *
	 * @return sampling interval in seconds
	 */
	public double getVmLoadLogInterval()
	{
		return INTERVAL_TO_GET_VM_LOAD_LOG;
	}

	/**
	 * Returns the device-location sampling interval.
	 *
	 * @return sampling interval in seconds
	 */
	public double getLocationLogInterval()
	{
		return INTERVAL_TO_GET_LOCATION_LOG;
	}

	/**
	 * Returns the access-point-delay sampling interval.
	 *
	 * @return sampling interval in seconds
	 */
	public double getApDelayLogInterval()
	{
		return INTERVAL_TO_GET_AP_DELAY_LOG;
	}

	/**
	 * Reports whether detailed file logging is enabled.
	 *
	 * @return {@code true} only when both file logging and detailed logging are enabled
	 */
	public boolean getDeepFileLoggingEnabled()
	{
		return FILE_LOG_ENABLED && DEEP_FILE_LOG_ENABLED;
	}

	/**
	 * Reports whether file logging is enabled.
	 *
	 * @return {@code true} when simulation results are written to files
	 */
	public boolean getFileLoggingEnabled()
	{
		return FILE_LOG_ENABLED;
	}

	/**
	 * Returns the configured WAN propagation delay.
	 *
	 * @return WAN propagation delay in seconds
	 */
	public double getWanPropagationDelay()
	{
		return WAN_PROPAGATION_DELAY;
	}

	/**
	 * Returns the configured cellular-network propagation delay.
	 *
	 * @return cellular-network propagation delay in seconds
	 */
	public double getGsmPropagationDelay()
	{
		return GSM_PROPAGATION_DELAY;
	}

	/**
	 * Returns the configured internal-LAN propagation delay.
	 *
	 * @return internal-LAN propagation delay in seconds
	 */
	public double getInternalLanDelay()
	{
		return LAN_INTERNAL_DELAY;
	}

	/**
	 * Returns the configured WLAN bandwidth.
	 *
	 * @return WLAN bandwidth in kilobits per second
	 */
	public int getWlanBandwidth()
	{
		return bandwidthWlan;
	}

	/**
	 * Returns the configured MAN bandwidth.
	 *
	 * @return MAN bandwidth in kilobits per second
	 */
	public int getManBandwidth()
	{
		return bandwidthMan;
	}

	/**
	 * Returns the configured WAN bandwidth.
	 *
	 * @return WAN bandwidth in kilobits per second
	 */
	public int getWanBandwidth()
	{
		return bandwidthWan;
	}

	/**
	 * Returns the configured cellular-network bandwidth.
	 *
	 * @return cellular-network bandwidth in kilobits per second
	 */
	public int getGsmBandwidth()
	{
		return bandwidthGsm;
	}

	/**
	 * Returns the lower bound of the configured mobile-device sweep.
	 *
	 * @return minimum number of mobile devices
	 */
	public int getMinNumOfMobileDev()
	{
		return MIN_NUM_OF_MOBILE_DEVICES;
	}

	/**
	 * Returns the upper bound of the configured mobile-device sweep.
	 *
	 * @return maximum number of mobile devices
	 */
	public int getMaxNumOfMobileDev()
	{
		return MAX_NUM_OF_MOBILE_DEVICES;
	}

	/**
	 * Returns the increment used by the mobile-device sweep.
	 *
	 * @return device-count increment
	 */
	public int getMobileDevCounterSize()
	{
		return MOBILE_DEVICE_COUNTER_SIZE;
	}

	/**
	 * Returns the configured WLAN coverage range.
	 *
	 * @return coverage range in metres
	 */
	public int getWlanRange()
	{
		return WLAN_RANGE;
	}

	/**
	 * Returns the number of edge datacenters parsed from the configuration.
	 *
	 * @return number of edge datacenters
	 */
	public int getNumOfEdgeDatacenters()
	{
		return NUM_OF_EDGE_DATACENTERS;
	}

	/**
	 * Returns the total number of configured edge hosts.
	 *
	 * @return number of edge hosts
	 */
	public int getNumOfEdgeHosts()
	{
		return NUM_OF_EDGE_HOSTS;
	}

	/**
	 * Returns the total number of configured edge VMs.
	 *
	 * @return number of edge VMs
	 */
	public int getNumOfEdgeVMs()
	{
		return NUM_OF_EDGE_VMS;
	}

	/**
	 * Returns the number of configured place types.
	 *
	 * @return number of place types
	 */
	public int getNumOfPlaceTypes()
	{
		return NUM_OF_PLACE_TYPES;
	}

	/**
	 * Returns the number of hosts in the cloud datacenter.
	 *
	 * @return number of cloud hosts
	 */
	public int getNumOfCloudHost()
	{
		return NUM_OF_HOST_ON_CLOUD_DATACENTER;
	}

	/**
	 * Returns the number of VMs assigned to each cloud host.
	 *
	 * @return number of VMs per cloud host
	 */
	public int getNumOfCloudVMsPerHost()
	{
		return NUM_OF_VM_ON_CLOUD_HOST;
	}

	/**
	 * Returns the total number of configured cloud VMs.
	 *
	 * @return product of the cloud-host and per-host VM counts
	 */
	public int getNumOfCloudVMs()
	{
		return NUM_OF_VM_ON_CLOUD_HOST * NUM_OF_HOST_ON_CLOUD_DATACENTER;
	}

	/**
	 * Returns the number of processing elements assigned to each cloud VM.
	 *
	 * @return processing-element count
	 */
	public int getCoreForCloudVM()
	{
		return CORE_FOR_CLOUD_VM;
	}

	/**
	 * Returns the processing capacity of each cloud VM.
	 *
	 * @return capacity in million instructions per second
	 */
	public int getMipsForCloudVM()
	{
		return MIPS_FOR_CLOUD_VM;
	}

	/**
	 * Returns the memory capacity of each cloud VM.
	 *
	 * @return memory in megabytes
	 */
	public int getRamForCloudVM()
	{
		return RAM_FOR_CLOUD_VM;
	}

	/**
	 * Returns storage capacity of the central cloud VMs.
	 *
	 * @return Storage allocation for cloud VMs in bytes
	 */
	public int getStorageForCloudVM()
	{
		return STORAGE_FOR_CLOUD_VM;
	}

	/**
	 * Returns the memory capacity of each mobile VM.
	 *
	 * @return memory in megabytes
	 */
	public int getRamForMobileVM()
	{
		return RAM_FOR_VM;
	}

	/**
	 * Returns the number of processing elements assigned to each mobile VM.
	 *
	 * @return processing-element count
	 */
	public int getCoreForMobileVM()
	{
		return CORE_FOR_VM;
	}

	/**
	 * Returns the processing capacity of each mobile VM.
	 *
	 * @return capacity in million instructions per second
	 */
	public int getMipsForMobileVM()
	{
		return MIPS_FOR_VM;
	}

	/**
	 * Returns storage capacity of the mobile (processing unit) VMs.
	 *
	 * @return Storage allocation for mobile VMs in bytes
	 */
	public int getStorageForMobileVM()
	{
		return STORAGE_FOR_VM;
	}

	/**
	 * Returns the configured simulation scenario identifiers.
	 *
	 * @return scenario identifiers in configuration order
	 */
	public String[] getSimulationScenarios()
	{
		return SIMULATION_SCENARIOS;
	}

	/**
	 * Returns the configured orchestration-policy identifiers.
	 *
	 * @return policy identifiers in configuration order
	 */
	public String[] getOrchestratorPolicies()
	{
		return ORCHESTRATOR_POLICIES;
	}


	/**
	 * Returns the northern coordinate bound.
	 *
	 * @return northern coordinate bound of the simulation area
	 */
	public double getNorthernBound() {
		return NORTHERN_BOUND;
	}

	/**
	 * Returns the eastern coordinate bound.
	 *
	 * @return eastern coordinate bound of the simulation area
	 */
	public double getEasternBound() {
		return EASTERN_BOUND;
	}

	/**
	 * Returns the southern coordinate bound.
	 *
	 * @return southern coordinate bound of the simulation area
	 */
	public double getSouthernBound() {
		return SOUTHERN_BOUND;
	}

	/**
	 * Returns the western coordinate bound.
	 *
	 * @return western coordinate bound of the simulation area
	 */
	public double getWesternBound() {
		return WESTERN_BOUND;
	}

	/**
	 * Returns mobility characteristics lookup table.
	 * Contains mean waiting time (in minutes) for each place type in the mobility model.
	 *
	 * @return Array of waiting times indexed by place type
	 */
	public double[] getMobilityLookUpTable()
	{
		return mobilityLookUpTable;
	}

	/**
	 * Returns application characteristics lookup table containing all task parameters.
	 * Each row represents an application type with the following characteristics:
	 * [0] Usage percentage (%)
	 * [1] Probability of selecting cloud (%)
	 * [2] Poisson mean arrival rate (seconds)
	 * [3] Active period duration (seconds)
	 * [4] Idle period duration (seconds)
	 * [5] Average data upload size (KB)
	 * [6] Average data download size (KB)
	 * [7] Average task length (Million Instructions)
	 * [8] Required number of CPU cores
	 * [9] VM utilization on edge nodes (%)
	 * [10] VM utilization on cloud (%)
	 * [11] VM utilization on mobile devices (%)
	 * [12] Delay sensitivity factor [0-1]
	 * [13] Maximum delay requirement (seconds)
	 *
	 * @return Two-dimensional array containing task characteristics for all application types
	 */
	public double[][] getTaskLookUpTable()
	{
		return taskLookUpTable;
	}

	/**
	 * Returns task properties for a specific application type by name.
	 * Searches through the task names array to find matching application.
	 *
	 * @param taskName Name of the application type to retrieve properties for
	 * @return Array of task characteristics, or null if taskName not found
	 */
	public double[] getTaskProperties(String taskName) {
		double[] result = null;
		int index = -1;
		for (int i=0;i<taskNames.length;i++) {
			if (taskNames[i].equals(taskName)) {
				index = i;
				break;
			}
		}

		if(index >= 0 && index < taskLookUpTable.length)
			result = taskLookUpTable[index];

		return result;
	}

	/**
	 * Returns the application name for a given task type index.
	 *
	 * @param taskType Index of the task type in the lookup table
	 * @return Name of the application type
	 */
	public String getTaskName(int taskType)
	{
		return taskNames[taskType];
	}

	/**
	 * Validates that a required attribute exists in an XML element.
	 * Throws exception if the attribute is missing or empty.
	 *
	 * @param element XML element to check
	 * @param key Attribute name to validate
	 * @throws IllegalArgumentException if attribute is missing or empty
	 */
	private void isAttributePresent(Element element, String key) {
		String value = element.getAttribute(key);
		if (value.trim().isEmpty()){
			throw new IllegalArgumentException("Attribute '" + key + "' is not found in '" + element.getNodeName() +"'");
		}
	}

	/**
	 * Validates that a required child element exists in an XML element.
	 * Throws exception if the child element is missing or empty.
	 *
	 * @param element Parent XML element to check
	 * @param key Child element name to validate
	 * @throws IllegalArgumentException if child element is missing or empty
	 */
	private void isElementPresent(Element element, String key) {
		Node node = element.getElementsByTagName(key).item(0);
		if (node == null || node.getTextContent().trim().isEmpty()) {
			throw new IllegalArgumentException("Element '" + key + "' is not found in '" + element.getNodeName() +"'");
		}
	}

	private boolean checkElement(Element element, String key) {
		Node node = element.getElementsByTagName(key).item(0);
		return node != null && !node.getTextContent().trim().isEmpty();
	}

	private DocumentBuilderFactory createSecureDocumentBuilderFactory()
			throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		return factory;
	}

	private void parseApplicationsXML(String filePath)
	{
		Document doc = null;
		try {
			File devicesFile = new File(filePath);
			DocumentBuilderFactory dbFactory = createSecureDocumentBuilderFactory();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			doc = dBuilder.parse(devicesFile);
			doc.getDocumentElement().normalize();

			String mandatoryAttributes[] = {
					"usage_percentage", //usage percentage [0-100]
					"prob_cloud_selection", //prob. of selecting cloud [0-100]
					"poisson_interarrival", //poisson mean (sec)
					"active_period", //active period (sec)
					"idle_period", //idle period (sec)
					"data_upload", //avg data upload (KB)
					"data_download", //avg data download (KB)
					"task_length", //avg task length (MI)
					"required_core", //required # of core
					"vm_utilization_on_edge", //vm utilization on edge vm [0-100]
					"vm_utilization_on_cloud", //vm utilization on cloud vm [0-100]
					"vm_utilization_on_mobile", //vm utilization on mobile vm [0-100]
			"delay_sensitivity"}; //delay_sensitivity [0-1]

			String optionalAttributes[] = {
			"max_delay_requirement"}; //maximum delay requirement (sec)

			NodeList appList = doc.getElementsByTagName("application");
			taskLookUpTable = new double[appList.getLength()]
					[mandatoryAttributes.length + optionalAttributes.length];

			taskNames = new String[appList.getLength()];
			for (int i = 0; i < appList.getLength(); i++) {
				Node appNode = appList.item(i);

				Element appElement = (Element) appNode;
				isAttributePresent(appElement, "name");
				String taskName = appElement.getAttribute("name");
				taskNames[i] = taskName;

				for(int m=0; m<mandatoryAttributes.length; m++){
					isElementPresent(appElement, mandatoryAttributes[m]);
					taskLookUpTable[i][m] = Double.parseDouble(appElement.
							getElementsByTagName(mandatoryAttributes[m]).item(0).getTextContent());
				}

				for(int o=0; o<optionalAttributes.length; o++){
					double value = 0;
					if(checkElement(appElement, optionalAttributes[o]))
						value =  Double.parseDouble(appElement.getElementsByTagName(optionalAttributes[o]).item(0).getTextContent());

					taskLookUpTable[i][mandatoryAttributes.length + o] = value;
				}
			}
		} catch (Exception e) {
			throw new IllegalArgumentException("Applications XML cannot be parsed: " + filePath, e);
		}
	}

	private void parseEdgeDevicesXML(String filePath)
	{
		try {
			File devicesFile = new File(filePath);
			DocumentBuilderFactory dbFactory = createSecureDocumentBuilderFactory();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			edgeDevicesDoc = dBuilder.parse(devicesFile);
			edgeDevicesDoc.getDocumentElement().normalize();

			NodeList datacenterList = edgeDevicesDoc.getElementsByTagName("datacenter");
			for (int i = 0; i < datacenterList.getLength(); i++) {
				NUM_OF_EDGE_DATACENTERS++;
				Node datacenterNode = datacenterList.item(i);

				Element datacenterElement = (Element) datacenterNode;
				isAttributePresent(datacenterElement, "arch");
				isAttributePresent(datacenterElement, "os");
				isAttributePresent(datacenterElement, "vmm");
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
						isAttributePresent(vmElement, "vmm");
						isElementPresent(vmElement, "core");
						isElementPresent(vmElement, "mips");
						isElementPresent(vmElement, "ram");
						isElementPresent(vmElement, "storage");
					}
				}
			}

		} catch (Exception e) {
			throw new IllegalArgumentException("Edge devices XML cannot be parsed: " + filePath, e);
		}
	}
}
