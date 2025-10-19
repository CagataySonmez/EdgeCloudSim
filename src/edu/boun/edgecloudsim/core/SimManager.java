/*
 * Title:        EdgeCloudSim - Simulation Manager
 * 
 * Description: 
 * SimManager is an singleton class providing many abstract classeses such as
 * Network Model, Mobility Model, Edge Orchestrator to other modules
 * Critical simulation related information would be gathered via this class 
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.core;

import java.io.IOException;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;

import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.EdgeServerManager;
import edu.boun.edgecloudsim.edge_server.EdgeVmAllocationPolicy_Custom;
import edu.boun.edgecloudsim.cloud_server.CloudServerManager;
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileServerManager;
import edu.boun.edgecloudsim.mobility.MobilityModel;
import edu.boun.edgecloudsim.task_generator.LoadGeneratorModel;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.utils.TaskProperty;
import edu.boun.edgecloudsim.utils.SimLogger;

/**
 * Simulation Manager - Main coordination entity for EdgeCloudSim simulations.
 * 
 * This singleton class serves as the central coordinator for all simulation components,
 * managing the lifecycle and interactions between network models, mobility models,
 * edge orchestrators, and infrastructure managers. It extends SimEntity to participate
 * in the discrete event simulation engine and handles critical simulation events.
 * 
 * Key responsibilities:
 * - Coordinates simulation initialization and execution flow
 * - Manages task generation and distribution across the simulation environment  
 * - Monitors VM states and resource utilization across edge and cloud infrastructure
 * - Handles simulation progress tracking and logging operations
 * - Provides centralized access to all simulation components and models
 */
public class SimManager extends SimEntity {
	// Event type constants for discrete event simulation
	private static final int CREATE_TASK = 0;        // Task generation event
	private static final int CHECK_ALL_VM = 1;       // VM monitoring event  
	private static final int GET_LOAD_LOG = 2;       // Load logging event
	private static final int PRINT_PROGRESS = 3;     // Progress reporting event
	private static final int STOP_SIMULATION = 4;    // Simulation termination event
	
	// Simulation configuration parameters
	private String simScenario;            // Current simulation scenario name
	private String orchestratorPolicy;     // Selected orchestration policy
	private int numOfMobileDevice;         // Number of mobile devices in simulation
	
	// Core simulation models and components
	private NetworkModel networkModel;              // Network delay and bandwidth model
	private MobilityModel mobilityModel;            // Mobile device movement model  
	private ScenarioFactory scenarioFactory;       // Factory for creating scenario-specific components
	private EdgeOrchestrator edgeOrchestrator;      // Task orchestration and placement logic
	
	// Infrastructure management components
	private EdgeServerManager edgeServerManager;     // Edge datacenter and VM management
	private CloudServerManager cloudServerManager;   // Cloud datacenter and VM management
	private MobileServerManager mobileServerManager; // Mobile device processing unit management
	
	// Task generation and device management
	private LoadGeneratorModel loadGeneratorModel;   // Task generation patterns and workload simulation
	private MobileDeviceManager mobileDeviceManager; // Mobile device lifecycle management
	
	// Singleton instance
	private static SimManager instance = null;
	
	/**
	 * Constructs the SimManager and initializes all simulation components.
	 * Sets up task generation, mobility models, network models, and infrastructure managers
	 * according to the specified scenario and orchestration policy.
	 * 
	 * @param _scenarioFactory Factory for creating scenario-specific simulation components
	 * @param _numOfMobileDevice Number of mobile devices to simulate
	 * @param _simScenario Name of the simulation scenario to execute
	 * @param _orchestratorPolicy Task orchestration policy to use
	 * @throws Exception if initialization of any component fails
	 */
	public SimManager(ScenarioFactory _scenarioFactory, int _numOfMobileDevice, String _simScenario, String _orchestratorPolicy) throws Exception {
		super("SimManager");
		simScenario = _simScenario;
		scenarioFactory = _scenarioFactory;
		numOfMobileDevice = _numOfMobileDevice;
		orchestratorPolicy = _orchestratorPolicy;

		SimLogger.print("Creating tasks...");
		loadGeneratorModel = scenarioFactory.getLoadGeneratorModel();
		loadGeneratorModel.initializeModel();
		SimLogger.printLine("Done, ");
		
		SimLogger.print("Creating device locations...");
		mobilityModel = scenarioFactory.getMobilityModel();
		mobilityModel.initialize();
		SimLogger.printLine("Done.");

		//Generate network model
		networkModel = scenarioFactory.getNetworkModel();
		networkModel.initialize();
		
		//Generate edge orchestrator
		edgeOrchestrator = scenarioFactory.getEdgeOrchestrator();
		edgeOrchestrator.initialize();
		
		//Create Physical Servers
		edgeServerManager = scenarioFactory.getEdgeServerManager();
		edgeServerManager.initialize();
		
		//Create Physical Servers on cloud
		cloudServerManager = scenarioFactory.getCloudServerManager();
		cloudServerManager.initialize();
		
		// Create Physical Servers on mobile devices for local processing
		mobileServerManager = scenarioFactory.getMobileServerManager();
		mobileServerManager.initialize();

		// Create Client Manager for mobile device lifecycle management
		mobileDeviceManager = scenarioFactory.getMobileDeviceManager();
		mobileDeviceManager.initialize();
		
		instance = this;
	}
	
	/**
	 * Gets the singleton instance of SimManager.
	 * 
	 * @return The current SimManager instance
	 */
	public static SimManager getInstance(){
		return instance;
	}
	
	/**
	 * Starts the EdgeCloudSim simulation by triggering CloudSim engine.
	 * Initializes all datacenters, creates VMs, schedules initial events,
	 * and begins the discrete event simulation loop.
	 * 
	 * @throws Exception if simulation initialization or execution fails
	 */
	public void startSimulation() throws Exception{
		//Starts the simulation
		SimLogger.print(super.getName()+" is starting...");
		
		//Start Edge Datacenters & Generate VMs
		edgeServerManager.startDatacenters();
		edgeServerManager.createVmList(mobileDeviceManager.getId());
		
		//Start Edge Datacenters & Generate VMs
		cloudServerManager.startDatacenters();
		cloudServerManager.createVmList(mobileDeviceManager.getId());
		
		//Start Mobile Datacenters & Generate VMs
		mobileServerManager.startDatacenters();
		mobileServerManager.createVmList(mobileDeviceManager.getId());
		
		CloudSim.startSimulation();
	}

	public String getSimulationScenario(){
		return simScenario;
	}

	/**
	 * Gets the current orchestration policy name.
	 * 
	 * @return Name of the task orchestration policy being used
	 */
	public String getOrchestratorPolicy(){
		return orchestratorPolicy;
	}
	
	/**
	 * Gets the scenario factory used for creating simulation components.
	 * 
	 * @return ScenarioFactory instance for component creation
	 */
	public ScenarioFactory getScenarioFactory(){
		return scenarioFactory;
	}
	
	/**
	 * Gets the number of mobile devices in the simulation.
	 * 
	 * @return Total number of simulated mobile devices
	 */
	public int getNumOfMobileDevice(){
		return numOfMobileDevice;
	}
	
	/**
	 * Gets the network model for delay and bandwidth simulation.
	 * 
	 * @return NetworkModel instance managing network characteristics
	 */
	public NetworkModel getNetworkModel(){
		return networkModel;
	}

	/**
	 * Gets the mobility model for mobile device movement simulation.
	 * 
	 * @return MobilityModel instance managing device locations and movement
	 */
	public MobilityModel getMobilityModel(){
		return mobilityModel;
	}
	
	/**
	 * Gets the edge orchestrator for task placement decisions.
	 * 
	 * @return EdgeOrchestrator instance managing task scheduling and placement
	 */
	public EdgeOrchestrator getEdgeOrchestrator(){
		return edgeOrchestrator;
	}
	
	/**
	 * Gets the edge server manager for edge infrastructure control.
	 * 
	 * @return EdgeServerManager instance managing edge datacenters and VMs
	 */
	public EdgeServerManager getEdgeServerManager(){
		return edgeServerManager;
	}
	
	/**
	 * Gets the cloud server manager for cloud infrastructure control.
	 * 
	 * @return CloudServerManager instance managing cloud datacenters and VMs
	 */
	public CloudServerManager getCloudServerManager(){
		return cloudServerManager;
	}
	
	/**
	 * Gets the mobile server manager for mobile device processing units.
	 * 
	 * @return MobileServerManager instance managing mobile computing resources
	 */
	public MobileServerManager getMobileServerManager(){
		return mobileServerManager;
	}

	/**
	 * Gets the load generator model for task generation patterns.
	 * 
	 * @return LoadGeneratorModel instance managing workload simulation
	 */
	public LoadGeneratorModel getLoadGeneratorModel(){
		return loadGeneratorModel;
	}
	
	/**
	 * Gets the mobile device manager for device lifecycle management.
	 * 
	 * @return MobileDeviceManager instance managing mobile device operations
	 */
	public MobileDeviceManager getMobileDeviceManager(){
		return mobileDeviceManager;
	}
	
	/**
	 * Starts the simulation entity and initializes all VM lists and event scheduling.
	 * Submits VM lists from edge, cloud, and mobile servers to the mobile device manager,
	 * schedules all task creation events, and initiates periodic monitoring events.
	 */
	@Override
	public void startEntity() {
		int hostCounter=0;

		// Submit edge server VM lists to mobile device manager
		for(int i= 0; i<edgeServerManager.getDatacenterList().size(); i++) {
			List<? extends Host> list = edgeServerManager.getDatacenterList().get(i).getHostList();
			for (int j=0; j < list.size(); j++) {
				mobileDeviceManager.submitVmList(edgeServerManager.getVmList(hostCounter));
				hostCounter++;
			}
		}
		
		// Submit cloud server VM lists to mobile device manager
		for(int i = 0; i<SimSettings.getInstance().getNumOfCloudHost(); i++) {
			mobileDeviceManager.submitVmList(cloudServerManager.getVmList(i));
		}

		// Submit mobile server VM lists to mobile device manager
		for(int i=0; i<numOfMobileDevice; i++){
			if(mobileServerManager.getVmList(i) != null)
				mobileDeviceManager.submitVmList(mobileServerManager.getVmList(i));
		}
		
		// Schedule all task creation events based on load generator model
		for(int i=0; i< loadGeneratorModel.getTaskList().size(); i++)
			schedule(getId(), loadGeneratorModel.getTaskList().get(i).getStartTime(), CREATE_TASK, loadGeneratorModel.getTaskList().get(i));
		
		// Schedule periodic monitoring and control events
		schedule(getId(), 5, CHECK_ALL_VM);                                                    // VM status monitoring
		schedule(getId(), SimSettings.getInstance().getSimulationTime()/100, PRINT_PROGRESS); // Progress reporting
		schedule(getId(), SimSettings.getInstance().getVmLoadLogInterval(), GET_LOAD_LOG);     // Load logging
		schedule(getId(), SimSettings.getInstance().getSimulationTime(), STOP_SIMULATION);     // Simulation termination
		
		SimLogger.printLine("Done.");
	}

	/**
	 * Processes discrete events during simulation execution.
	 * Handles task creation, VM monitoring, load logging, progress reporting,
	 * and simulation termination events in a synchronized manner.
	 * 
	 * @param ev SimEvent containing event type and associated data
	 */
	@Override
	public void processEvent(SimEvent ev) {
		synchronized(this){
			switch (ev.getTag()) {
			case CREATE_TASK:
				// Handle task creation and submission to mobile device manager
				try {
					TaskProperty edgeTask = (TaskProperty) ev.getData();
					mobileDeviceManager.submitTask(edgeTask);						
				} catch (Exception e) {
					e.printStackTrace();
					System.exit(1);
				}
				break;
			case CHECK_ALL_VM:
				// Verify all VMs have been successfully created
				int totalNumOfVm = SimSettings.getInstance().getNumOfEdgeVMs();
				if(EdgeVmAllocationPolicy_Custom.getCreatedVmNum() != totalNumOfVm){
					SimLogger.printLine("All VMs cannot be created! Terminating simulation...");
					System.exit(1);
				}
				break;
			case GET_LOAD_LOG:
				// Log utilization statistics for edge, cloud, and mobile servers
				SimLogger.getInstance().addVmUtilizationLog(
						CloudSim.clock(),
						edgeServerManager.getAvgUtilization(),
						cloudServerManager.getAvgUtilization(),
						mobileServerManager.getAvgUtilization());
				
				// Schedule next load logging event
				schedule(getId(), SimSettings.getInstance().getVmLoadLogInterval(), GET_LOAD_LOG);
				break;
			case PRINT_PROGRESS:
				// Display simulation progress as percentage
				int progress = (int)((CloudSim.clock()*100)/SimSettings.getInstance().getSimulationTime());
				if(progress % 10 == 0)
					SimLogger.print(Integer.toString(progress));
				else
					SimLogger.print(".");
				if(CloudSim.clock() < SimSettings.getInstance().getSimulationTime())
					schedule(getId(), SimSettings.getInstance().getSimulationTime()/100, PRINT_PROGRESS);

				break;
			case STOP_SIMULATION:
				// Terminate simulation and finalize logging
				SimLogger.printLine("100");
				CloudSim.terminateSimulation();
				try {
					SimLogger.getInstance().simStopped();
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
				}
				break;
			default:
				SimLogger.printLine(getName() + ": unknown event type");
				break;
			}
		}
	}

	/**
	 * Shuts down the simulation entity by terminating all datacenters.
	 * Properly cleans up edge, cloud, and mobile server infrastructures
	 * to ensure graceful simulation termination.
	 */
	@Override
	public void shutdownEntity() {
		edgeServerManager.terminateDatacenters();
		cloudServerManager.terminateDatacenters();
		mobileServerManager.terminateDatacenters();
	}
}
