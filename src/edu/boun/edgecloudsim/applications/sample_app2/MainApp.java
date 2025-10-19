/*
 * Title:        EdgeCloudSim - Main Application
 * 
 * Description:  Main application for Sample App2
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app2;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.core.ScenarioFactory;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

/**
 * Main application class for Sample App 2 - Multi-tier Edge-Cloud Computing Scenario.
 * This application demonstrates EdgeCloudSim's capability to handle complex network topologies
 * including WLAN, MAN, and WAN connections with empirical delay models and orchestration policies.
 */
public class MainApp {

	/**
	 * Main entry point for the multi-tier edge-cloud simulation.
	 * Supports SINGLE_TIER and TWO_TIER_WITH_EO scenarios with various orchestration policies.
	 * 
	 * @param args Command line arguments: [configFile, edgeDevicesFile, applicationsFile, outputFolder, iterationNumber]
	 */
	public static void main(String[] args) {
		// Disable console output of CloudSim library to reduce noise
		Log.disable();

		// Enable console and file logging for this EdgeCloudSim application
		SimLogger.enablePrintLog();

		// Initialize simulation parameters with default values
		int iterationNumber = 1;
		String configFile = "";
		String outputFolder = "";
		String edgeDevicesFile = "";
		String applicationsFile = "";
		
		// Parse command line arguments for custom configuration
		if (args.length == 5){
			configFile = args[0];          // Configuration properties file
			edgeDevicesFile = args[1];     // Edge device topology definition
			applicationsFile = args[2];    // Application characteristics file
			outputFolder = args[3];        // Output directory for results
			iterationNumber = Integer.parseInt(args[4]); // Current iteration number
		}
		else{
			// Use default configuration files if no arguments provided
			SimLogger.printLine("Simulation setting file, output folder and iteration number are not provided! Using default ones...");
			configFile = "scripts/sample_app2/config/default_config.properties";
			applicationsFile = "scripts/sample_app2/config/applications.xml";
			edgeDevicesFile = "scripts/sample_app2/config/edge_devices.xml";
			outputFolder = "sim_results/ite" + iterationNumber;
		}

		// Load simulation settings from configuration files
		SimSettings SS = SimSettings.getInstance();
		if(SS.initialize(configFile, edgeDevicesFile, applicationsFile) == false){
			SimLogger.printLine("cannot initialize simulation settings!");
			System.exit(0);
		}

		// Setup file logging and clean output directory if file logging is enabled
		if(SS.getFileLoggingEnabled()){
			SimLogger.enableFileLog();
			SimUtils.cleanOutputFolder(outputFolder);
		}

		DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date SimulationStartDate = Calendar.getInstance().getTime();
		String now = df.format(SimulationStartDate);
		SimLogger.printLine("Simulation started at " + now);
		SimLogger.printLine("----------------------------------------------------------------------");

		// Triple nested loop for comprehensive simulation coverage:
		// 1. Mobile device count variations
		// 2. Different simulation scenarios (SINGLE_TIER, TWO_TIER_WITH_EO)
		// 3. Various orchestrator policies (NETWORK_BASED, UTILIZATION_BASED, HYBRID)
		for(int j=SS.getMinNumOfMobileDev(); j<=SS.getMaxNumOfMobileDev(); j+=SS.getMobileDevCounterSize())
		{
			for(int k=0; k<SS.getSimulationScenarios().length; k++)
			{
				for(int i=0; i<SS.getOrchestratorPolicies().length; i++)
				{
					String simScenario = SS.getSimulationScenarios()[k];
					String orchestratorPolicy = SS.getOrchestratorPolicies()[i];
					Date ScenarioStartDate = Calendar.getInstance().getTime();
					now = df.format(ScenarioStartDate);

					SimLogger.printLine("Scenario started at " + now);
					SimLogger.printLine("Scenario: " + simScenario + " - Policy: " + orchestratorPolicy + " - #iteration: " + iterationNumber);
					SimLogger.printLine("Duration: " + SS.getSimulationTime()/60 + " min (warm up period: "+ SS.getWarmUpPeriod()/60 +" min) - #devices: " + j);
					SimLogger.getInstance().simStarted(outputFolder,"SIMRESULT_" + simScenario + "_"  + orchestratorPolicy + "_" + j + "DEVICES");

					try
					{
						// Initialize the CloudSim simulation framework
						int num_user = 2;   // Number of CloudSim users (brokers)
						Calendar calendar = Calendar.getInstance();
						boolean trace_flag = false;  // Disable event tracing for performance

						// Initialize CloudSim with timing precision and user settings
						CloudSim.init(num_user, calendar, trace_flag, 0.01);

						// Create scenario factory for multi-tier edge-cloud computing
						ScenarioFactory sampleFactory = new SampleScenarioFactory(j,SS.getSimulationTime(), orchestratorPolicy, simScenario);

						// Initialize EdgeCloudSim simulation manager with empirical network models
						SimManager manager = new SimManager(sampleFactory, j, simScenario, orchestratorPolicy);

						// Execute the multi-tier edge-cloud simulation
						manager.startSimulation();
					}
					catch (Exception e)
					{
						SimLogger.printLine("The simulation has been terminated due to an unexpected error");
						e.printStackTrace();
						System.exit(0);
					}

					Date ScenarioEndDate = Calendar.getInstance().getTime();
					now = df.format(ScenarioEndDate);
					SimLogger.printLine("Scenario finished at " + now +  ". It took " + SimUtils.getTimeDifference(ScenarioStartDate,ScenarioEndDate));
					SimLogger.printLine("----------------------------------------------------------------------");
				}//End of orchestrators loop
			}//End of scenarios loop
		}//End of mobile devices loop

		Date SimulationEndDate = Calendar.getInstance().getTime();
		now = df.format(SimulationEndDate);
		SimLogger.printLine("Simulation finished at " + now +  ". It took " + SimUtils.getTimeDifference(SimulationStartDate,SimulationEndDate));
	}
}
