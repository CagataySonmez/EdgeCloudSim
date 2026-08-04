/*
 * Title:        EdgeCloudSim - Main Application
 * 
 * Description:  Main application for this scenario
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.scenario3;

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
	public static final String SCENARIO_NAME = "scenario3";

	/**
	 * Creates main() to run this example
	 */
	public static void main(String[] args) {
		//disable console output of CloudSim library
		Log.disable();

		//enable console output
		SimLogger.enablePrintLog();

		int iterationStart;
		int iterationEnd;
		String configFile = null;
		String outputFolder = null;
		String edgeDevicesFile = null;
		String applicationsFile = null;

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
			SimLogger.printLine("Simulation setting file, output folder and iteration number are not provided! Using default ones...");
			configFile = "scripts/" + SCENARIO_NAME + "/config/default_config.properties";
			applicationsFile = "scripts/" + SCENARIO_NAME + "/config/applications.xml";
			edgeDevicesFile = "scripts/" + SCENARIO_NAME + "/config/edge_devices.xml";
			
			// !! IMPORTANT NOTICE !!
			// For those who are using IDE (eclipse etc) can modify
			// -> iteration value to run a specific iteration
			// -> iteration Start/End value to run multiple iterations at a time
			//    in this case start shall be less than or equal to end value
			int iteration = 1;
			iterationStart = iteration;
			iterationEnd = iteration;
		}

		//load settings from configuration file
		SimSettings SS = SimSettings.getInstance();
		if(SS.initialize(configFile, edgeDevicesFile, applicationsFile) == false){
			SimLogger.printLine("cannot initialize simulation settings!");
			System.exit(0);
		}
		
		DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date SimulationStartDate = Calendar.getInstance().getTime();
		String now = df.format(SimulationStartDate);
		SimLogger.printLine("Simulation started at " + now);
		SimLogger.printLine("----------------------------------------------------------------------");
		
		for(int iterationNumber=iterationStart; iterationNumber<=iterationEnd; iterationNumber++) {
			
			if (args.length != EXPECTED_NUM_OF_ARGS)
				outputFolder = "sim_results/" + SCENARIO_NAME + "/ite" + iterationNumber;
			
			if(SS.getFileLoggingEnabled()){
				SimLogger.enableFileLog();
				File dir = new File(outputFolder);
				if(dir.exists() && dir.isDirectory())
				{
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
					SimLogger.printLine("Output folder is not available; deleting '" + outputFolder + "'");
					dir.mkdirs();
				}
			}
	
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
							// First step: Initialize the CloudSim package. It should be called
							// before creating any entities.
							int num_user = 2;   // number of grid users
							Calendar calendar = Calendar.getInstance();
							boolean trace_flag = false;  // mean trace events
	
							// Initialize the CloudSim library
							CloudSim.init(num_user, calendar, trace_flag, 0.01);
	
							// Generate EdgeCloudsim Scenario Factory
							ScenarioFactory sampleFactory = new SampleScenarioFactory(j,SS.getSimulationTime(), orchestratorPolicy, simScenario);
	
							// Generate EdgeCloudSim Simulation Manager
							SimManager manager = new SimManager(sampleFactory, j, simScenario, orchestratorPolicy);
	
							// Start simulation
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
		}//End of iteration loop

		Date SimulationEndDate = Calendar.getInstance().getTime();
		now = df.format(SimulationEndDate);
		SimLogger.printLine("Simulation finished at " + now +  ". It took " + SimUtils.getTimeDifference(SimulationStartDate,SimulationEndDate));
	}
}
