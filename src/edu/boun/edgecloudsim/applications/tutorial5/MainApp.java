/*
 * Title:        EdgeCloudSim - Sample Application
 * 
 * Description:  Sample application for this scenario
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.tutorial5;

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
	public static final String APPLICATION_FOLDER = "tutorial5";

	/**
	 * Creates main() to run this example
	 */
	public static void main(String[] args) {
		// Suppress CloudSim internal console noise (EdgeCloudSim uses SimLogger)
		Log.disable();

		// Enable EdgeCloudSim console logging (file logging optionally added later)
		SimLogger.enablePrintLog();

		int iterationStart;
		int iterationEnd;
		boolean clearOutputFolder = true;
		String configFile = null;
		String outputFolder = null;
		String edgeDevicesFile = null;
		String applicationsFile = null;

		// iterationStart/End allow batching multiple repetitions (statistical runs)
		// clearOutputFolder toggles whether prior results are deleted
		// Argument parsing: expect [config edge_devices applications output iteration]
		// Defaults selected for quick IDE run (note different *1.properties / xml)
		// Command line arguments will be properly provided by our simulation runner scripts.
		// IDE users mostly do not provide simulation configuration files and iteration value!
		if (args.length == EXPECTED_NUM_OF_ARGS){
			configFile = args[0];
			edgeDevicesFile = args[1];
			applicationsFile = args[2];
			outputFolder = args[3];
			iterationStart = Integer.parseInt(args[4]);
			iterationEnd = iterationStart;
		}
		else{
			// Inform about fallback + disable automatic folder cleaning for safety
			SimLogger.printLine("Simulation setting file, output folder and iteration number are not provided! Using default ones...");
			configFile = "scripts/" + APPLICATION_FOLDER + "/config/default_config1.properties";
			applicationsFile = "scripts/" + APPLICATION_FOLDER + "/config/applications1.xml";
			edgeDevicesFile = "scripts/" + APPLICATION_FOLDER + "/config/edge_devices1.xml";
			
			// Do not remove files in the output folder while using an IDE (eclipse etc)
			// That means you need to clear iteration folders manually!
			clearOutputFolder = false;
			
			// !! IMPORTANT NOTICE !!
			// For those who are using an IDE can modify
			// -> iteration value to run a specific iteration
			// -> iteration Start/End value to run multiple iterations at a time
			//    in this case start shall be less than or equal to end value
			int iteration = 1;
			iterationStart = iteration;
			iterationEnd = iteration;
		}

		// Initialize global simulation settings; abort on failure (invalid paths / parse errors)
		// load settings from configuration file
		SimSettings SS = SimSettings.getInstance();
		if(SS.initialize(configFile, edgeDevicesFile, applicationsFile) == false){
			// ...existing code...
			SimLogger.printLine("cannot initialize simulation settings!");
			System.exit(0);
		}
		
		// Human-readable timestamp formatter for start/end logging
		DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date SimulationStartDate = Calendar.getInstance().getTime();
		String now = df.format(SimulationStartDate);
		SimLogger.printLine("Simulation started at " + now);
		SimLogger.printLine("----------------------------------------------------------------------");
		
		// Execution roadmap:
		// 1) Parse CLI args (or use default script configs for IDE runs)
		// 2) Initialize SimSettings (config, edge devices, applications)
		// 3) For each iteration:
		//      - Determine output folder and optionally clean it
		//      - Loop over mobile device population sizes
		//          - Loop over configured scenario names
		//              - Loop over orchestrator policies
		//                  * Initialize CloudSim kernel
		//                  * Build ScenarioFactory
		//                  * Create SimManager
		//                  * Run simulation
		//                  * Log per-scenario timing
		// 4) Log total wall-clock simulation duration
		// Fail-fast: terminate on any configuration or runtime error to avoid corrupt results.
		
		for(int iterationNumber=iterationStart; iterationNumber<=iterationEnd; iterationNumber++) {
			// Auto-generate per-iteration result folder when not provided
			if (args.length != EXPECTED_NUM_OF_ARGS)
				outputFolder = "sim_results/" + APPLICATION_FOLDER + "/ite" + iterationNumber;
			
			if(SS.getFileLoggingEnabled()){
				// Prepare output directory (clean only if flag true)
				SimLogger.enableFileLog();
				File dir = new File(outputFolder);
				if(clearOutputFolder && dir.exists() && dir.isDirectory())
				{
					// Remove existing files to avoid mixing different run outputs
					SimLogger.printLine("Output folder is available; cleaning '" + outputFolder + "'");
					for (File f: dir.listFiles())
					{
						// Delete only regular files (skip potential subdirectories)
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
					// Create directory structure if absent
					SimLogger.printLine("Output folder is not available; deleting '" + outputFolder + "'");
					dir.mkdirs();
				}
			}
	
			// Sweep over mobile device counts (scalability experiments)
			for(int j=SS.getMinNumOfMobileDev(); j<=SS.getMaxNumOfMobileDev(); j+=SS.getMobileDevCounterSize())
			{
				// Iterate scenario variants (functional / workload differences)
				for(int k=0; k<SS.getSimulationScenarios().length; k++)
				{
					// Iterate orchestrator policies (placement / offloading strategies)
					for(int i=0; i<SS.getOrchestratorPolicies().length; i++)
					{
						String simScenario = SS.getSimulationScenarios()[k];
						String orchestratorPolicy = SS.getOrchestratorPolicies()[i];
						Date ScenarioStartDate = Calendar.getInstance().getTime();
						now = df.format(ScenarioStartDate);
	
						// Log scenario header summary (factors + iteration)
						SimLogger.printLine("Scenario started at " + now);
						SimLogger.printLine("Scenario: " + simScenario + " - Policy: " + orchestratorPolicy + " - #iteration: " + iterationNumber);
						SimLogger.printLine("Duration: " + SS.getSimulationTime()/60 + " min (warm up period: "+ SS.getWarmUpPeriod()/60 +" min) - #devices: " + j);
						SimLogger.getInstance().simStarted(outputFolder,"SIMRESULT_" + simScenario + "_"  + orchestratorPolicy + "_" + j + "DEVICES");
	
						try
						{
							// CloudSim kernel initialization:
							// num_user   : logical user entities (e.g., brokers)
							// calendar   : base reference time
							// trace_flag : enable detailed event tracing (disabled for speed)
							// last param : minimal event time granularity (seconds)
							int num_user = 2;   // number of grid users
							Calendar calendar = Calendar.getInstance();
							boolean trace_flag = false;  // mean trace events
	
							// Initialize the CloudSim library
							CloudSim.init(num_user, calendar, trace_flag, 0.01);
	
							// Build factory providing mobility, network, load generator, orchestrator, managers
							ScenarioFactory sampleFactory = new SampleScenarioFactory(j,SS.getSimulationTime(), orchestratorPolicy, simScenario);
	
							// SimManager wires all components and starts discrete-event processing
							SimManager manager = new SimManager(sampleFactory, j, simScenario, orchestratorPolicy);
	
							// Launch simulation (blocking until completion)
							manager.startSimulation();
						}
						catch (Exception e)
						{
							// Fail fast to prevent partial / inconsistent datasets
							SimLogger.printLine("The simulation has been terminated due to an unexpected error");
							e.printStackTrace();
							System.exit(0);
						}
	
						// Per-scenario duration log (human-readable difference)
						Date ScenarioEndDate = Calendar.getInstance().getTime();
						now = df.format(ScenarioEndDate);
						SimLogger.printLine("Scenario finished at " + now +  ". It took " + SimUtils.getTimeDifference(ScenarioStartDate,ScenarioEndDate));
						SimLogger.printLine("----------------------------------------------------------------------");
					}//End of orchestrators loop
				}//End of scenarios loop
			}//End of mobile devices loop
		}//End of iteration loop

		// Final overall duration summary for the entire batch
		Date SimulationEndDate = Calendar.getInstance().getTime();
		now = df.format(SimulationEndDate);
		SimLogger.printLine("Simulation finished at " + now +  ". It took " + SimUtils.getTimeDifference(SimulationStartDate,SimulationEndDate));
	}
}
