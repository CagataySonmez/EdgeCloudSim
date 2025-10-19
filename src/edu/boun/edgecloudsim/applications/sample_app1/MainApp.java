/*
 * Title:        EdgeCloudSim - Main Application
 * 
 * Description:  Main application for Simple App
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app1;

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

public class MainApp {
	
	/**
	 * Main entry point for the EdgeCloudSim Simple App simulation.
	 * Executes multiple simulation scenarios with different device counts,
	 * orchestration policies, and simulation scenarios.
	 * 
	 * @param args Command line arguments:
	 *             [0] - Configuration file path
	 *             [1] - Edge devices file path  
	 *             [2] - Applications file path
	 *             [3] - Output folder path
	 *             [4] - Iteration number
	 */
	public static void main(String[] args) {
		// Disable console output of CloudSim library for cleaner logs
		Log.disable();
		
		// Enable console output and file output of this application
		SimLogger.enablePrintLog();
		
		// Initialize simulation parameters with default values
		int iterationNumber = 1;
		String configFile = "";
		String outputFolder = "";
		String edgeDevicesFile = "";
		String applicationsFile = "";
		
		// Parse command line arguments or use default configuration files
		if (args.length == 5){
			configFile = args[0];
			edgeDevicesFile = args[1];
			applicationsFile = args[2];
			outputFolder = args[3];
			iterationNumber = Integer.parseInt(args[4]);
		}
		else{
			SimLogger.printLine("Simulation setting file, output folder and iteration number are not provided! Using default ones...");
			configFile = "scripts/sample_app1/config/default_config.properties";
			applicationsFile = "scripts/sample_app1/config/applications.xml";
			edgeDevicesFile = "scripts/sample_app1/config/edge_devices.xml";
			outputFolder = "sim_results/ite" + iterationNumber;
		}

		// Load settings from configuration file
		SimSettings SS = SimSettings.getInstance();
		if(SS.initialize(configFile, edgeDevicesFile, applicationsFile) == false){
			SimLogger.printLine("cannot initialize simulation settings!");
			System.exit(0);
		}
		
		// Enable file logging and prepare output directory if configured
		if(SS.getFileLoggingEnabled()){
			SimLogger.enableFileLog();
			SimUtils.cleanOutputFolder(outputFolder);
		}
		
		// Initialize date formatter and log simulation start time
		DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date SimulationStartDate = Calendar.getInstance().getTime();
		String now = df.format(SimulationStartDate);
		SimLogger.printLine("Simulation started at " + now);
		SimLogger.printLine("----------------------------------------------------------------------");

		// Triple nested loop to run all combinations of mobile devices, scenarios, and policies
		for(int j=SS.getMinNumOfMobileDev(); j<=SS.getMaxNumOfMobileDev(); j+=SS.getMobileDevCounterSize())
		{
			for(int k=0; k<SS.getSimulationScenarios().length; k++)
			{
				for(int i=0; i<SS.getOrchestratorPolicies().length; i++)
				{
					// Get current simulation scenario and orchestrator policy
					String simScenario = SS.getSimulationScenarios()[k];
					String orchestratorPolicy = SS.getOrchestratorPolicies()[i];
					Date ScenarioStartDate = Calendar.getInstance().getTime();
					now = df.format(ScenarioStartDate);
					
					// Log scenario details and parameters
					SimLogger.printLine("Scenario started at " + now);
					SimLogger.printLine("Scenario: " + simScenario + " - Policy: " + orchestratorPolicy + " - #iteration: " + iterationNumber);
					SimLogger.printLine("Duration: " + SS.getSimulationTime()/3600 + " hour(s) - Poisson: " + SS.getTaskLookUpTable()[0][2] + " - #devices: " + j);
					SimLogger.getInstance().simStarted(outputFolder,"SIMRESULT_" + simScenario + "_"  + orchestratorPolicy + "_" + j + "DEVICES");
					
					try
					{
						// Initialize the CloudSim package - must be called before creating any entities
						int num_user = 2;   // Number of grid users for CloudSim
						Calendar calendar = Calendar.getInstance();
						boolean trace_flag = false;  // Disable trace events for performance
				
						// Initialize the CloudSim library with simulation parameters
						CloudSim.init(num_user, calendar, trace_flag, 0.01);
						
						// Create EdgeCloudSim scenario factory with current parameters
						ScenarioFactory sampleFactory = new SampleScenarioFactory(j,SS.getSimulationTime(), orchestratorPolicy, simScenario);
						
						// Create EdgeCloudSim simulation manager
						SimManager manager = new SimManager(sampleFactory, j, simScenario, orchestratorPolicy);
						
						// Execute the simulation
						manager.startSimulation();
					}
					catch (Exception e)
					{
						SimLogger.printLine("The simulation has been terminated due to an unexpected error");
						e.printStackTrace();
						System.exit(0);
					}
					
					// Log scenario completion time and duration
					Date ScenarioEndDate = Calendar.getInstance().getTime();
					now = df.format(ScenarioEndDate);
					SimLogger.printLine("Scenario finished at " + now +  ". It took " + SimUtils.getTimeDifference(ScenarioStartDate,ScenarioEndDate));
					SimLogger.printLine("----------------------------------------------------------------------");
				} // End of orchestrator policies loop
			} // End of simulation scenarios loop
		} // End of mobile devices loop

		// Log total simulation completion time
		Date SimulationEndDate = Calendar.getInstance().getTime();
		now = df.format(SimulationEndDate);
		SimLogger.printLine("Simulation finished at " + now +  ". It took " + SimUtils.getTimeDifference(SimulationStartDate,SimulationEndDate));
	}
}
