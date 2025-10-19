/*
 * Title:        EdgeCloudSim - Main Application
 * 
 * Description:  Main application for this scenario
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.tutorial2;

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
	public static final String APPLICATION_FOLDER = "tutorial2";

	/**
	 * Creates main() to run this example
	 */
	public static void main(String[] args) {
		// Disable verbose CloudSim library console output (EdgeCloudSim uses its own logger)
		Log.disable();

		// Enable EdgeCloudSim console logging (can be combined with file logging later)
		SimLogger.enablePrintLog();

		// Iteration bounds (support single or multiple iteration runs)
		int iterationStart;
		int iterationEnd;
		String configFile = null;
		String outputFolder = null;
		String edgeDevicesFile = null;
		String applicationsFile = null;

		// Argument parsing: expecting config paths + output dir + iteration index
		// Fallback to default packaged scripts when not supplied (common in IDE usage)
		if (args.length == EXPECTED_NUM_OF_ARGS){
			configFile = args[0];
			edgeDevicesFile = args[1];
			applicationsFile = args[2];
			outputFolder = args[3];
			iterationStart = Integer.parseInt(args[4]);
			iterationEnd = iterationStart;
		}
		else{
			// Inform user about defaulting; allows quick experiments
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

		// Load all simulation settings; abort fast on failure to avoid partial / misleading results
		SimSettings SS = SimSettings.getInstance();
		if(SS.initialize(configFile, edgeDevicesFile, applicationsFile) == false){
			SimLogger.printLine("cannot initialize simulation settings!");
			System.exit(0);
		}
		
		// Date formatter for human-readable scenario timestamps
		DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date SimulationStartDate = Calendar.getInstance().getTime();
		String now = df.format(SimulationStartDate);
		SimLogger.printLine("Simulation started at " + now);
		SimLogger.printLine("----------------------------------------------------------------------");
		
		// Execution roadmap:
		// 1) Parse command line (or apply defaults for IDE runs)
		// 2) Initialize global SimSettings (config, devices, applications)
		// 3) For each iteration:
		//      a) (Optional) prepare / clean output folder
		//      b) Loop over mobile device population sizes
		//         i) Loop over simulation scenario names
		//            ii) Loop over orchestrator policies
		//                - Initialize CloudSim kernel
		//                - Build ScenarioFactory (inject parameters)
		//                - Create SimManager (wires components)
		//                - Run simulation
		//                - Log per-scenario duration
		// 4) Log total simulation wall-clock duration (real time difference)

		// Sweep over configured mobile device population sizes
		for(int iterationNumber=iterationStart; iterationNumber<=iterationEnd; iterationNumber++) {
			// Auto-generate output folder if not explicitly provided
			if (args.length != EXPECTED_NUM_OF_ARGS)
				outputFolder = "sim_results/" + APPLICATION_FOLDER + "/ite" + iterationNumber;
			
			if(SS.getFileLoggingEnabled()){
				// Prepare a clean result directory for reproducibility
				SimLogger.enableFileLog();
				File dir = new File(outputFolder);
				if(dir.exists() && dir.isDirectory())
				{
					// Remove stale result files from previous runs
					SimLogger.printLine("Output folder is available; cleaning '" + outputFolder + "'");
					for (File f: dir.listFiles())
					{
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
	
			// Sweep over configured mobile device population sizes
			for(int j=SS.getMinNumOfMobileDev(); j<=SS.getMaxNumOfMobileDev(); j+=SS.getMobileDevCounterSize())
			{
				// Iterate through scenario variants defined in settings
				for(int k=0; k<SS.getSimulationScenarios().length; k++)
				{
					// Iterate through orchestrator (offloading/placement) policies
					for(int i=0; i<SS.getOrchestratorPolicies().length; i++)
					{
						// Extract current experimental factors for logging
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
							// CloudSim core init:
							// num_user: logical users (brokers etc.)
							// calendar: base reference time
							// trace_flag: enable detailed event tracing (disabled => performance)
							// last param: minimal time between events (granularity)
							int num_user = 2;   // number of grid users
							Calendar calendar = Calendar.getInstance();
							boolean trace_flag = false;  // mean trace events
	
							// Initialize the CloudSim library
							CloudSim.init(num_user, calendar, trace_flag, 0.01);
	
							// Build scenario-specific factory (produces mobility, network, orchestrator, etc.)
							ScenarioFactory sampleFactory = new SampleScenarioFactory(j,SS.getSimulationTime(), orchestratorPolicy, simScenario);
	
							// Create simulation manager which registers entities and drives event scheduling
							SimManager manager = new SimManager(sampleFactory, j, simScenario, orchestratorPolicy);
	
							// Launch discrete-event simulation (blocks until completion)
							manager.startSimulation();
						}
						catch (Exception e)
						{
							// Fail fast on any unexpected error to avoid corrupt output data
							SimLogger.printLine("The simulation has been terminated due to an unexpected error");
							e.printStackTrace();
							System.exit(0);
						}
	
						// Log per-scenario simulated duration (virtual time difference)
						Date ScenarioEndDate = Calendar.getInstance().getTime();
						now = df.format(ScenarioEndDate);
						SimLogger.printLine("Scenario finished at " + now +  ". It took " + SimUtils.getTimeDifference(ScenarioStartDate,ScenarioEndDate));
						SimLogger.printLine("----------------------------------------------------------------------");
					}//End of orchestrators loop
				}//End of scenarios loop
			}//End of mobile devices loop
		}//End of iteration loop

		// Final overall runtime summary (wall clock difference from start to end)
		Date SimulationEndDate = Calendar.getInstance().getTime();
		now = df.format(SimulationEndDate);
		SimLogger.printLine("Simulation finished at " + now +  ". It took " + SimUtils.getTimeDifference(SimulationStartDate,SimulationEndDate));
	}
}
