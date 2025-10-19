/*
 * Title:        EdgeCloudSim - Sample Application
 * 
 * Description:  Sample application for Vehicular App
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app5;

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

public class VehicularMainApp {

	/**
	 * Main entry point for the vehicular edge computing simulation.
	 * Configures and runs simulation scenarios with different policies and device counts.
	 * 
	 * @param args command line arguments: [configFile, edgeDevicesFile, applicationsFile, outputFolder, iterationNumber]
	 */
	public static void main(String[] args) {
		// Disable CloudSim library console output to reduce noise
		Log.disable();

		// Enable console and file output for this application
		SimLogger.enablePrintLog();

		// Default simulation parameters
		int iterationNumber = 1;
		String configFile = "";
		String outputFolder = "";
		String edgeDevicesFile = "";
		String applicationsFile = "";
		
		// Parse command line arguments if provided
		if (args.length == 5){
			configFile = args[0];
			edgeDevicesFile = args[1];
			applicationsFile = args[2];
			outputFolder = args[3];
			iterationNumber = Integer.parseInt(args[4]);
		}
		else{
			// Use default configuration files if no arguments provided
			SimLogger.printLine("Simulation setting file, output folder and iteration number are not provided! Using default ones...");
			String configName = "default";
			configFile = "scripts/sample_app5/config/" + configName + "_config.properties";
			applicationsFile = "scripts/sample_app5/config/applications.xml";
			edgeDevicesFile = "scripts/sample_app5/config/edge_devices.xml";
			outputFolder = "sim_results/ite" + iterationNumber;
		}

		// Load simulation settings from configuration files
		SimSettings SS = SimSettings.getInstance();
		if(SS.initialize(configFile, edgeDevicesFile, applicationsFile) == false) {
			SimLogger.printLine("cannot initialize simulation settings!");
			System.exit(1);
		}

		// Setup output logging if enabled
		if(SS.getFileLoggingEnabled()){
			SimUtils.cleanOutputFolder(outputFolder);
			SimLogger.enableFileLog();
		}

		// Log simulation start time
		DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date SimulationStartDate = Calendar.getInstance().getTime();
		String now = df.format(SimulationStartDate);
		SimLogger.printLine("Simulation started at " + now);
		SimLogger.printLine("----------------------------------------------------------------------");

		// Initialize machine learning models for AI-based orchestration
		String wekaModelsFolder = configFile.substring(0, configFile.lastIndexOf('/')) + "/weka/";
		WekaWrapper.getInstance().initialize("MultilayerPerceptron", "LinearRegression", wekaModelsFolder);

		// Run simulation scenarios with different configurations
		// Iterate through: device counts -> scenarios -> orchestration policies
		for(int i=SS.getMinNumOfMobileDev(); i<=SS.getMaxNumOfMobileDev(); i+=SS.getMobileDevCounterSize())
			for(int s=0; s<SS.getSimulationScenarios().length; s++)
				for(int p=0; p<SS.getOrchestratorPolicies().length; p++)
					mainHelper(outputFolder, SS.getSimulationScenarios()[s], SS.getOrchestratorPolicies()[p], iterationNumber, i);

		Date SimulationEndDate = Calendar.getInstance().getTime();
		now = df.format(SimulationEndDate);
		SimLogger.printLine("Simulation finished at " + now +  ". It took " + SimUtils.getTimeDifference(SimulationStartDate,SimulationEndDate));
	}

	/**
	 * Helper method to run individual simulation scenarios
	 * @param outputFolder Directory for simulation results
	 * @param simulationScenario Network scenario (e.g., "ATTRACTIVE_SCENARIO")
	 * @param orchestratorPolicy Task orchestration policy (e.g., "GAME_THEORY")
	 * @param iterationNumber Current iteration for statistical averaging
	 * @param numOfMobileDevice Number of mobile devices in simulation
	 */
	public static void mainHelper(String outputFolder, String simulationScenario, String orchestratorPolicy, int iterationNumber, int numOfMobileDevice){
		DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date ScenarioStartDate = Calendar.getInstance().getTime();
		String now = df.format(ScenarioStartDate);
		SimSettings SS = SimSettings.getInstance();

		// Log scenario details
		SimLogger.printLine("Scenario started at " + now);
		SimLogger.printLine("Scenario: " + simulationScenario + " - Policy: " + orchestratorPolicy + " - #iteration: " + iterationNumber);
		SimLogger.printLine("Duration: " + SS.getSimulationTime()/60 + " min (warm up period: "+ SS.getWarmUpPeriod()/60 +" min) - #devices: " + numOfMobileDevice);
		SimLogger.getInstance().simStarted(outputFolder, "SIMRESULT_" + simulationScenario + "_"  + orchestratorPolicy + "_" + numOfMobileDevice + "DEVICES");

		try
		{
			// Initialize CloudSim simulation framework
			// This must be called before creating any CloudSim entities
			int num_user = 2;   // number of grid users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false;  // mean trace events

			// Initialize the CloudSim simulation library
			CloudSim.init(num_user, calendar, trace_flag, 0.01);

			// Create scenario factory for vehicular edge computing simulation
			ScenarioFactory sampleFactory = new VehicularScenarioFactory(numOfMobileDevice, SS.getSimulationTime(), orchestratorPolicy, simulationScenario);

			// Create simulation manager with vehicular-specific components
			SimManager manager = new SimManager(sampleFactory, numOfMobileDevice, simulationScenario, orchestratorPolicy);

			// Setup AI trainer mode if specified (for machine learning model training)
			if(orchestratorPolicy.equals("AI_TRAINER")){
				SimLogger.disableFileLog();
				((VehicularEdgeOrchestrator)manager.getEdgeOrchestrator()).openTrainerOutputFile();
			}

			// Execute the simulation
			manager.startSimulation();

			// Debug: Network delay statistics (commented out for production)
			//SimLogger.printLine("maxWanDelay: " + ((VehicularNetworkModel)manager.getNetworkModel()).maxWanDelay);
			//SimLogger.printLine("maxGsmDelay: " + ((VehicularNetworkModel)manager.getNetworkModel()).maxGsmDelay);
			//SimLogger.printLine("maxWlanDelay: " + ((VehicularNetworkModel)manager.getNetworkModel()).maxWlanDelay);

			// Close AI trainer output file if needed
			if(orchestratorPolicy.equals("AI_TRAINER"))
				((VehicularEdgeOrchestrator)manager.getEdgeOrchestrator()).closeTrainerOutputFile();
		}
		catch (Exception e)
		{
			SimLogger.printLine("The simulation has been terminated due to an unexpected error");
			e.printStackTrace();
			System.exit(1);
		}

		// Log scenario completion
		Date ScenarioEndDate = Calendar.getInstance().getTime();
		now = df.format(ScenarioEndDate);
		SimLogger.printLine("Scenario finished at " + now +  ". It took " + SimUtils.getTimeDifference(ScenarioStartDate,ScenarioEndDate));
		SimLogger.printLine("----------------------------------------------------------------------");

		// Suggest garbage collection to reduce memory usage
		System.gc();
	}//End of scenarios loop
}
