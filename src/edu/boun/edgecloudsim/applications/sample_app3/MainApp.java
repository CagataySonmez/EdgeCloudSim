/*
 * Title:        EdgeCloudSim - Main Application
 * 
 * Description:  Main application for Sample App3
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app3;

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
 * Main application class for Sample App 3 - Mobile Device Processing Scenario.
 * This application demonstrates EdgeCloudSim's capability to handle mobile device
 * local processing alongside edge server offloading for hybrid computing scenarios.
 */
public class MainApp {
	
	/**
	 * Main entry point for the mobile-edge hybrid processing simulation.
	 * Supports command-line arguments for configuration files and output settings.
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
			configFile = "scripts/sample_app3/config/default_config.properties";
			applicationsFile = "scripts/sample_app3/config/applications.xml";
			edgeDevicesFile = "scripts/sample_app3/config/edge_devices.xml";
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
		// 2. Different simulation scenarios 
		// 3. Various orchestrator policies (ONLY_EDGE, ONLY_MOBILE, HYBRID)
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
						
						// Create scenario factory for mobile-edge hybrid processing
						ScenarioFactory sampleFactory = new SampleScenarioFactory(j,SS.getSimulationTime(), orchestratorPolicy, simScenario);
						
						// Initialize EdgeCloudSim simulation manager with mobile device support
						SimManager manager = new SimManager(sampleFactory, j, simScenario, orchestratorPolicy);
						
						// Execute the mobile-edge hybrid simulation
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
