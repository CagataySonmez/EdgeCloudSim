/*
 * Title:        EdgeCloudSim - Main Application
 * 
 * Description:  Main application for this scenario
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.tutorial4;

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
	public static final String APPLICATION_FOLDER = "tutorial4";

	/**
	 * Creates main() to run this example
	 */
	public static void main(String[] args) {
		// Suppress default CloudSim console output; EdgeCloudSim uses SimLogger
		Log.disable();

		// Enable EdgeCloudSim console logging (file logging may also be enabled)
		SimLogger.enablePrintLog();

		int iterationStart;
		int iterationEnd;
		String configFile = null;
		String outputFolder = null;
		String edgeDevicesFile = null;
		String applicationsFile = null;

		// Argument parsing: expecting [config edge_devices applications output iteration]
		// Defaults chosen for convenience in IDE context
		//Command line arguments will be properly provided by our simulation runner scripts.
		//IDE users mostly do not provide simulation configuration files and iteration value!
		if (args.length == EXPECTED_NUM_OF_ARGS){
			configFile = args[0];
			edgeDevicesFile = args[1];
			applicationsFile = args[2];
			outputFolder = args[3];
			iterationStart = Integer.parseInt(args[4]);
			iterationEnd = iterationStart;
		}
		else{
			// Inform user about fallback to packaged defaults
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

		// Initialize global simulation settings and abort on failure
		//load settings from configuration file
		SimSettings SS = SimSettings.getInstance();
		if(SS.initialize(configFile, edgeDevicesFile, applicationsFile) == false){
			SimLogger.printLine("cannot initialize simulation settings!");
			System.exit(0);
		}
		
		// Timestamp formatter for human-readable scenario start/end logs
		DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date SimulationStartDate = Calendar.getInstance().getTime();
		String now = df.format(SimulationStartDate);
		SimLogger.printLine("Simulation started at " + now);
		SimLogger.printLine("----------------------------------------------------------------------");
		
		for(int iterationNumber=iterationStart; iterationNumber<=iterationEnd; iterationNumber++) {
			// Auto-generate iteration-specific output folder when running with defaults
			if (args.length != EXPECTED_NUM_OF_ARGS)
				outputFolder = "sim_results/" + APPLICATION_FOLDER + "/ite" + iterationNumber;
			
			if(SS.getFileLoggingEnabled()){
				// Prepare result directory: clean existing files or create fresh folder
				SimLogger.enableFileLog();
				File dir = new File(outputFolder);
				if(dir.exists() && dir.isDirectory())
				{
					// Remove stale files from previous runs to ensure reproducibility
					SimLogger.printLine("Output folder is available; cleaning '" + outputFolder + "'");
					for (File f: dir.listFiles())
					{
						// Delete only regular files (ignore directories)
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
	
			// Loop over mobile device counts (scaling experiments)
			for(int j=SS.getMinNumOfMobileDev(); j<=SS.getMaxNumOfMobileDev(); j+=SS.getMobileDevCounterSize())
			{
				// Loop over configured scenario variants
				for(int k=0; k<SS.getSimulationScenarios().length; k++)
				{
					// Loop over orchestrator policies (offloading / placement strategies)
					for(int i=0; i<SS.getOrchestratorPolicies().length; i++)
					{
						// Log scenario header (factors + iteration)
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
							// num_user   : logical user entities (e.g., brokers)
							// calendar   : base reference time
							// trace_flag : enable detailed event tracing (false for performance)
							// last param : min event time granularity (seconds)
							int num_user = 2;   // number of grid users
							Calendar calendar = Calendar.getInstance();
							boolean trace_flag = false;  // mean trace events
	
							// Initialize the CloudSim library
							CloudSim.init(num_user, calendar, trace_flag, 0.01);
	
							// Build scenario-specific factory (mobility, network, orchestrator, etc.)
							ScenarioFactory sampleFactory = new SampleScenarioFactory(j,SS.getSimulationTime(), orchestratorPolicy, simScenario);
	
							// Create simulation manager to wire entities and drive event scheduling
							SimManager manager = new SimManager(sampleFactory, j, simScenario, orchestratorPolicy);
	
							// Start discrete-event simulation (blocking until completion)
							manager.startSimulation();
						}
						catch (Exception e)
						{
							// Fail fast on unexpected errors to avoid corrupt output aggregation
							SimLogger.printLine("The simulation has been terminated due to an unexpected error");
							e.printStackTrace();
							System.exit(0);
						}
	
						// Log per-scenario elapsed wall-clock duration
						Date ScenarioEndDate = Calendar.getInstance().getTime();
						now = df.format(ScenarioEndDate);
						SimLogger.printLine("Scenario finished at " + now +  ". It took " + SimUtils.getTimeDifference(ScenarioStartDate,ScenarioEndDate));
						SimLogger.printLine("----------------------------------------------------------------------");
					}//End of orchestrators loop
				}//End of scenarios loop
			}//End of mobile devices loop
		}//End of iteration loop

		// Final summary: total wall-clock time across all iterations
		Date SimulationEndDate = Calendar.getInstance().getTime();
		now = df.format(SimulationEndDate);
		SimLogger.printLine("Simulation finished at " + now +  ". It took " + SimUtils.getTimeDifference(SimulationStartDate,SimulationEndDate));
	}
}
