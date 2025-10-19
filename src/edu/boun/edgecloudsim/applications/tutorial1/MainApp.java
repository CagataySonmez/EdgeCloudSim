/*
 * Title:        EdgeCloudSim - Main Application
 * 
 * Description:  Main application for this scenario
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

// High-level execution phases:
// 1) Parse / default command line args
// 2) Load simulation settings (config, devices, applications)
// 3) For each iteration
//    3.1) For each mobile device population size
//          3.1.1) For each simulation scenario
//                  3.1.1.1) For each orchestrator policy
//                             - Initialize CloudSim
//                             - Build scenario factory
//                             - Create SimManager
//                             - Run simulation
//                             - Log results and duration
// 4) Print total elapsed time

package edu.boun.edgecloudsim.applications.tutorial1;

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
	public static final String APPLICATION_FOLDER = "tutorial1";

	/**
	 * Creates main() to run this example
	 */
	public static void main(String[] args) {
		//disable console output of CloudSim library
		Log.disable();

		//enable console output for EdgeCloudSim (centralized logging utility)
		SimLogger.enablePrintLog();

		// Declare iteration boundaries (can become a range if user wants batch runs)
		int iterationStart;
		int iterationEnd;
		// Paths provided externally or defaulted for IDE usage
		String configFile = null;
		String outputFolder = null;
		String edgeDevicesFile = null;
		String applicationsFile = null;

		// Parse command line arguments:
		// Expected: 0:config 1:edge_devices 2:applications 3:output_folder 4:iteration
		// If not provided, fall back to defaults for quick local tests.
		if (args.length == EXPECTED_NUM_OF_ARGS){
			configFile = args[0];
			edgeDevicesFile = args[1];
			applicationsFile = args[2];
			outputFolder = args[3];
			iterationStart = Integer.parseInt(args[4]);
			iterationEnd = iterationStart;
		}
		else{
			// Inform user that defaults are used (common in IDE debugging)
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

		// Load simulation settings (returns false if any config inconsistency occurs)
		// Abort early to avoid partial / misleading runs.
		SimSettings SS = SimSettings.getInstance();
		if(SS.initialize(configFile, edgeDevicesFile, applicationsFile) == false){
			SimLogger.printLine("cannot initialize simulation settings!");
			System.exit(0);
		}
		
		// Prepare date formatter for human-readable logging timestamps
		DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date SimulationStartDate = Calendar.getInstance().getTime();
		String now = df.format(SimulationStartDate);
		SimLogger.printLine("Simulation started at " + now);
		SimLogger.printLine("----------------------------------------------------------------------");
		
		// For each iteration specified by the user or default range
		for(int iterationNumber=iterationStart; iterationNumber<=iterationEnd; iterationNumber++) {
			// Derive output folder automatically when not explicitly provided
			if (args.length != EXPECTED_NUM_OF_ARGS)
				outputFolder = "sim_results/" + APPLICATION_FOLDER + "/ite" + iterationNumber;
			
			if(SS.getFileLoggingEnabled()){
				// File logging enabled -> ensure clean slate for this iteration
				// (avoids mixing results from different runs)
				SimLogger.enableFileLog();
				File dir = new File(outputFolder);
				if(dir.exists() && dir.isDirectory())
				{
					SimLogger.printLine("Output folder is available; cleaning '" + outputFolder + "'");
					for (File f: dir.listFiles())
					{
						// Only delete plain files (keep potential sub-structure future-proof)
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
					// If folder missing, create it (mkdirs covers nested structure)
					SimLogger.printLine("Output folder is not available; deleting '" + outputFolder + "'");
					dir.mkdirs();
				}
			}
	
			// Device population sweep (scalability analysis)
			for(int j=SS.getMinNumOfMobileDev(); j<=SS.getMaxNumOfMobileDev(); j+=SS.getMobileDevCounterSize())
			{
				// Iterate through each configured simulation scenario variant
				for(int k=0; k<SS.getSimulationScenarios().length; k++)
				{
					// Evaluate each orchestrator policy under the active scenario
					for(int i=0; i<SS.getOrchestratorPolicies().length; i++)
					{
						// Extract current test dimensions
						String simScenario = SS.getSimulationScenarios()[k];
						String orchestratorPolicy = SS.getOrchestratorPolicies()[i];
						Date ScenarioStartDate = Calendar.getInstance().getTime();
						now = df.format(ScenarioStartDate);
	
						// Log scenario header summarizing experimental factors
						SimLogger.printLine("Scenario started at " + now);
						SimLogger.printLine("Scenario: " + simScenario + " - Policy: " + orchestratorPolicy + " - #iteration: " + iterationNumber);
						SimLogger.printLine("Duration: " + SS.getSimulationTime()/60 + " min (warm up period: "+ SS.getWarmUpPeriod()/60 +" min) - #devices: " + j);
						// Warm-up period: metrics during first interval often excluded from statistical analysis (transient phase).
						// Consider filtering in post-processing if comparing steady-state KPIs.
						SimLogger.getInstance().simStarted(outputFolder,"SIMRESULT_" + simScenario + "_"  + orchestratorPolicy + "_" + j + "DEVICES");
	
						try
						{
							// For multi-seed experimentation, wrap this block and vary RNG seeds between iterations.
							// Minimal event granularity (0.01) chosen to avoid zero-time collisions
							// CloudSim core init:
							// num_user: logical users generating events (broker etc.)
							// calendar: base time reference
							// trace_flag: enable event tracing (disabled for performance)
							// last param: minimal time between events (precision)
							int num_user = 2;   // number of grid users
							Calendar calendar = Calendar.getInstance();
							boolean trace_flag = false;  // mean trace events
	
							// Initialize the CloudSim library
							CloudSim.init(num_user, calendar, trace_flag, 0.01);
	
							// ScenarioFactory encapsulates workload, mobility, network, placement etc.
							ScenarioFactory sampleFactory = new SampleScenarioFactory(j,SS.getSimulationTime(), orchestratorPolicy, simScenario);
	
							// SimManager wires all components, schedules events, and aggregates stats
							SimManager manager = new SimManager(sampleFactory, j, simScenario, orchestratorPolicy);
	
							// Kick off discrete-event simulation (blocking until completion)
							manager.startSimulation();
						}
						catch (Exception e)
						{
							// Crash-fast strategy prevents partial mixed-result datasets
							// Any uncaught exception here invalidates experimental run
							// Fail fast to avoid corrupt aggregated datasets
							SimLogger.printLine("The simulation has been terminated due to an unexpected error");
							e.printStackTrace();
							System.exit(0);
						}
	
						// Log per-scenario duration (excludes previous scenarios)
						Date ScenarioEndDate = Calendar.getInstance().getTime();
						now = df.format(ScenarioEndDate);
						SimLogger.printLine("Scenario finished at " + now +  ". It took " + SimUtils.getTimeDifference(ScenarioStartDate,ScenarioEndDate));
						SimLogger.printLine("----------------------------------------------------------------------");
					}//End of orchestrators loop
				}//End of scenarios loop
			}//End of mobile devices loop
		}//End of iteration loop

		// Final summary for the entire multi-iteration batch
		Date SimulationEndDate = Calendar.getInstance().getTime();
		now = df.format(SimulationEndDate);
		SimLogger.printLine("Simulation finished at " + now +  ". It took " + SimUtils.getTimeDifference(SimulationStartDate,SimulationEndDate));
	}
}
