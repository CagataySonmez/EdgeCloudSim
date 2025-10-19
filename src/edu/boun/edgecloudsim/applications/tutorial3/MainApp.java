/*
 * Title:        EdgeCloudSim - Main Application
 * 
 * Description:  Main application for this scenario
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.tutorial3;

import java.io.File;
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
	
	public static final int EXPECTED_NUM_OF_ARGS = 5;
	public static final String APPLICATION_FOLDER = "tutorial3";

	// Execution roadmap:
	// 1) Parse CLI args (or apply defaults for IDE runs)
	// 2) Initialize SimSettings (config, devices, applications)
	// 3) For each iteration:
	//      - Prepare/clean output folder (if file logging enabled)
	//      - Loop over mobile device population sizes
	//          - Loop over scenario names
	//              - Loop over orchestrator policies
	//                  * Init CloudSim kernel
	//                  * Build ScenarioFactory
	//                  * Create SimManager
	//                  * Run simulation
	//                  * Log per-scenario timing
	// 4) Log total wall-clock duration of full batch
	// Fail-fast strategy: terminate on any configuration or runtime exception to preserve result integrity.

	/**
	 * Creates main() to run this example
	 */
	public static void main(String[] args) {
		// Suppress CloudSim internal console logging (EdgeCloudSim uses SimLogger)
		Log.disable();

		// Enable EdgeCloudSim console logging (file logging optionally added later)
		SimLogger.enablePrintLog();

		int iterationStart;
		int iterationEnd;
		String configFile = null;
		String outputFolder = null;
		String edgeDevicesFile = null;
		String applicationsFile = null;

		// Prepare iteration boundaries (supports batch runs if user alters values manually)
		// Command line arguments will be properly provided by our simulation runner scripts.
		// IDE users mostly do not provide simulation configuration files and iteration value!
		if (args.length == EXPECTED_NUM_OF_ARGS){
			// Argument parsing: expect [config edge_devices applications output iteration]
			configFile = args[0];
			edgeDevicesFile = args[1];
			applicationsFile = args[2];
			outputFolder = args[3];
			iterationStart = Integer.parseInt(args[4]);
			iterationEnd = iterationStart;
		}
		else{
			// Inform user about fallback to defaults
			SimLogger.printLine("Simulation setting file, output folder and iteration number are not provided! Using default ones...");
			configFile = "scripts/" + APPLICATION_FOLDER + "/config/default_config.properties";
			applicationsFile = "scripts/" + APPLICATION_FOLDER + "/config/applications.xml";
			edgeDevicesFile = "scripts/" + APPLICATION_FOLDER + "/config/edge_devices.xml";
			
			// !! IMPORTANT NOTICE !!
			// For those who are using IDE (eclipse etc) can modify
			// -> iteration value to run a specific iteration
			// -> iteration Start/End value to run multiple iterations at a time
			//    in this case start shall be less than or equal to end value
			int iteration = 1;
			iterationStart = iteration;
			iterationEnd = iteration;
		}

		// Load all simulation settings; abort if any file/config inconsistency occurs
		//load settings from configuration file
		SimSettings SS = SimSettings.getInstance();
		if(SS.initialize(configFile, edgeDevicesFile, applicationsFile) == false){
			SimLogger.printLine("cannot initialize simulation settings!");
			System.exit(0);
		}
		
		// Human-readable timestamp formatter for scenario start/end logging
		DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date SimulationStartDate = Calendar.getInstance().getTime();
		String now = df.format(SimulationStartDate);
		SimLogger.printLine("Simulation started at " + now);
		SimLogger.printLine("----------------------------------------------------------------------");
		
		for(int iterationNumber=iterationStart; iterationNumber<=iterationEnd; iterationNumber++) {
			// Auto-generate output folder when not supplied via CLI
			if (args.length != EXPECTED_NUM_OF_ARGS)
				outputFolder = "sim_results/" + APPLICATION_FOLDER + "/ite" + iterationNumber;
			
			if(SS.getFileLoggingEnabled()){
				// Ensure clean output directory for reproducibility (remove stale files)
				SimLogger.enableFileLog();
				File dir = new File(outputFolder);
				if(dir.exists() && dir.isDirectory())
				{
					SimLogger.printLine("Output folder is available; cleaning '" + outputFolder + "'");
					for (File f: dir.listFiles())
					{
						// Delete only regular files (ignore subdirs for forward compatibility)
						if (f.exists() && f.isFile())
						{
							if(!f.delete())
							{
								SimLogger.printLine("file cannot be deleted: " + f.getAbsolutePath());
								System.exit(1);
							}
						}
					}
				}
				else {
					// Create directory tree if absent
					SimLogger.printLine("Output folder is not available; deleting '" + outputFolder + "'");
					dir.mkdirs();
				}
			}
	
			// Sweep through configured mobile device counts
			for(int j=SS.getMinNumOfMobileDev(); j<=SS.getMaxNumOfMobileDev(); j+=SS.getMobileDevCounterSize())
			{
				// Iterate over scenario variants defined in config
				for(int k=0; k<SS.getSimulationScenarios().length; k++)
				{
					// Iterate over orchestrator (offloading/placement) policies
					for(int i=0; i<SS.getOrchestratorPolicies().length; i++)
					{
						// Extract current simulation factors and log scenario header
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
							// CloudSim initialization parameters:
							// num_user   : logical users (brokers)
							// calendar   : base time reference
							// trace_flag : detailed event tracing (disabled for performance)
							// last param : min time resolution (seconds)
							int num_user = 2;   // number of grid users
							Calendar calendar = Calendar.getInstance();
							boolean trace_flag = false;  // mean trace events
	
							// Initialize the CloudSim library
							CloudSim.init(num_user, calendar, trace_flag, 0.01);
	
							// Build scenario-specific factory (provides mobility, network, load, orchestrator, etc.)
							ScenarioFactory sampleFactory = new SampleScenarioFactory(j,SS.getSimulationTime(), orchestratorPolicy, simScenario);
	
							// SimManager wires entities and drives the discrete-event simulation
							SimManager manager = new SimManager(sampleFactory, j, simScenario, orchestratorPolicy);
	
							// Launch simulation (blocking until completion)
							manager.startSimulation();
						}
						catch (Exception e)
						{
							// Fail fast: prevent mixing partial/corrupted results with valid ones
							SimLogger.printLine("The simulation has been terminated due to an unexpected error");
							e.printStackTrace();
							System.exit(0);
						}
	
						// Log per-scenario elapsed real time (wall-clock difference)
						Date ScenarioEndDate = Calendar.getInstance().getTime();
						now = df.format(ScenarioEndDate);
						SimLogger.printLine("Scenario finished at " + now +  ". It took " + SimUtils.getTimeDifference(ScenarioStartDate,ScenarioEndDate));
						SimLogger.printLine("----------------------------------------------------------------------");
					}//End of orchestrators loop
				}//End of scenarios loop
			}//End of mobile devices loop
		}//End of iteration loop

		// Final summary for entire multi-iteration batch
		Date SimulationEndDate = Calendar.getInstance().getTime();
		now = df.format(SimulationEndDate);
		SimLogger.printLine("Simulation finished at " + now +  ". It took " + SimUtils.getTimeDifference(SimulationStartDate,SimulationEndDate));
	}
}
