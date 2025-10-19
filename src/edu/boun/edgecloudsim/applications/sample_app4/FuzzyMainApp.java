/*
 * Title:        EdgeCloudSim - Sample Application
 * 
 * Description:  Sample application for EdgeCloudSim
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app4;

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

public class FuzzyMainApp {
	
	/**
	 * Main entry point for the fuzzy logic-based EdgeCloudSim application.
	 * Configures simulation parameters and runs multiple scenarios with different orchestration policies.
	 * 
	 * @param args Command line arguments: [configFile, edgeDevicesFile, applicationsFile, outputFolder, iterationNumber]
	 */
	public static void main(String[] args) {
		// Disable CloudSim library console output to reduce noise
		Log.disable();
		
		// Enable console and file logging for this application
		SimLogger.enablePrintLog();
		
		// Initialize simulation parameters
		int iterationNumber = 1;
		String configFile = "";
		String outputFolder = "";
		String edgeDevicesFile = "";
		String applicationsFile = "";
		
		// Parse command line arguments for custom configuration files
		if (args.length == 5){
			configFile = args[0];          // Main simulation configuration
			edgeDevicesFile = args[1];     // Edge server topology definition
			applicationsFile = args[2];    // Application workload characteristics
			outputFolder = args[3];        // Results output directory
			iterationNumber = Integer.parseInt(args[4]); // Current simulation iteration
		}
		// Use default configuration files if not provided
		else{
			SimLogger.printLine("Simulation setting file, output folder and iteration number are not provided! Using default ones...");
			configFile = "scripts/sample_app4/config/default_config.properties";
			applicationsFile = "scripts/sample_app4/config/applications.xml";
			edgeDevicesFile = "scripts/sample_app4/config/edge_devices.xml";
			outputFolder = "sim_results/ite" + iterationNumber;
		}

		// Load and validate simulation settings from configuration files
		SimSettings SS = SimSettings.getInstance();
		if(SS.initialize(configFile, edgeDevicesFile, applicationsFile) == false){
			SimLogger.printLine("cannot initialize simulation settings!");
			System.exit(0);
		}
		
		// Configure output logging if enabled
		if(SS.getFileLoggingEnabled()){
			SimLogger.enableFileLog();
			SimUtils.cleanOutputFolder(outputFolder);
		}
		
		DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date SimulationStartDate = Calendar.getInstance().getTime();
		String now = df.format(SimulationStartDate);
		SimLogger.printLine("Simulation started at " + now);
		SimLogger.printLine("----------------------------------------------------------------------");

		// Triple nested loop to run all combinations of: device counts, scenarios, and policies
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

					// Log scenario configuration and start time
					SimLogger.printLine("Scenario started at " + now);
					SimLogger.printLine("Scenario: " + simScenario + " - Policy: " + orchestratorPolicy + " - #iteration: " + iterationNumber);
					SimLogger.printLine("Duration: " + SS.getSimulationTime()/60 + " min (warm up period: "+ SS.getWarmUpPeriod()/60 +" min) - #devices: " + j);
					SimLogger.getInstance().simStarted(outputFolder,"SIMRESULT_" + simScenario + "_"  + orchestratorPolicy + "_" + j + "DEVICES");
					
					try
					{
						// Initialize CloudSim simulation framework
						int num_user = 2;   // Number of grid users (standard CloudSim parameter)
						Calendar calendar = Calendar.getInstance();
						boolean trace_flag = false;  // Disable event tracing for performance
				
						// Initialize CloudSim library with time granularity of 0.01 seconds
						CloudSim.init(num_user, calendar, trace_flag, 0.01);
						
						// Create fuzzy logic-specific scenario factory
						ScenarioFactory sampleFactory = new FuzzyScenarioFactory(j,SS.getSimulationTime(), orchestratorPolicy, simScenario);
						
						// Create simulation manager with fuzzy orchestration components
						SimManager manager = new SimManager(sampleFactory, j, simScenario, orchestratorPolicy);
						
						// Execute the simulation scenario
						manager.startSimulation();
					}
					catch (Exception e)
					{
						SimLogger.printLine("The simulation has been terminated due to an unexpected error");
						e.printStackTrace();
						System.exit(0);
					}
					
					// Log scenario completion time and performance statistics
					Date ScenarioEndDate = Calendar.getInstance().getTime();
					now = df.format(ScenarioEndDate);
					SimLogger.printLine("Scenario finished at " + now +  ". It took " + SimUtils.getTimeDifference(ScenarioStartDate,ScenarioEndDate));
					SimLogger.printLine("----------------------------------------------------------------------");
				}// End of orchestrator policies loop (FUZZY_BASED, NETWORK_BASED, etc.)
			}// End of simulation scenarios loop (SINGLE_TIER, TWO_TIER_WITH_EO, etc.)
		}// End of mobile device count loop (varying number of devices)

		// Log total simulation completion time
		Date SimulationEndDate = Calendar.getInstance().getTime();
		now = df.format(SimulationEndDate);
		SimLogger.printLine("Simulation finished at " + now +  ". It took " + SimUtils.getTimeDifference(SimulationStartDate,SimulationEndDate));
	}
}
