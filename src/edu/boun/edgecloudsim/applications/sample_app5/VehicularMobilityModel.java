/*
 * Title:        EdgeCloudSim - Mobility model implementation
 * 
 * Description: 
 * VehicularMobilityModel implements basic vehicular mobility model
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app5;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.mobility.MobilityModel;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimUtils;

public class VehicularMobilityModel extends MobilityModel {
	// Speed limits for different location types (km/h): [low, medium, high attractiveness]
	private final double SPEED_FOR_PLACES[] = {20, 40, 60};

	private int lengthOfSegment;          // Length of each road segment in meters
	private double totalTimeForLoop;      // Total time to complete one full road loop in seconds
	private int[] locationTypes;          // Attractiveness level for each location (0=low, 1=medium, 2=high)

	// Pre-computed arrays to optimize getLocation() performance
	// NOTE: For large number of clients, consider computing on-demand to save memory
	private int[] initialLocationIndexArray;        // Starting location index for each vehicle
	private int[] initialPositionArray;             // Starting position in meters for each vehicle
	private double[] timeToDriveLocationArray;      // Time required to traverse each location segment
	private double[] timeToReachNextLocationArray;  // Time for each vehicle to reach next location from start

	/**
	 * Constructor for vehicular mobility model.
	 * 
	 * @param _numberOfMobileDevices number of mobile devices (vehicles)
	 * @param _simulationTime total simulation duration in seconds
	 */
	public VehicularMobilityModel(int _numberOfMobileDevices, double _simulationTime) {
		super(_numberOfMobileDevices, _simulationTime);
	}

	/**
	 * Initializes the mobility model by reading road topology and computing movement parameters.
	 * Sets up road segments, speeds, and initial positions for all vehicles.
	 */
	@Override
	public void initialize() {
		// Calculate total road length from edge device configuration
		Document doc = SimSettings.getInstance().getEdgeDevicesDocument();
		NodeList datacenterList = doc.getElementsByTagName("datacenter");
		Element location = (Element)((Element)datacenterList.item(0)).getElementsByTagName("location").item(0);
		int x_pos = Integer.parseInt(location.getElementsByTagName("x_pos").item(0).getTextContent());
		lengthOfSegment = x_pos * 2; // Assume uniform segment lengths
		int totalLengthOfRoad = lengthOfSegment * datacenterList.getLength();

		// Extract location attractiveness levels and compute travel times
		locationTypes = new int[datacenterList.getLength()];
		timeToDriveLocationArray = new double[datacenterList.getLength()];
		for(int i=0; i<datacenterList.getLength(); i++) {
			Node datacenterNode = datacenterList.item(i);
			Element datacenterElement = (Element) datacenterNode;
			Element locationElement = (Element)datacenterElement.getElementsByTagName("location").item(0);
			locationTypes[i] = Integer.parseInt(locationElement.getElementsByTagName("attractiveness").item(0).getTextContent());

			// Calculate time to traverse this segment: time = distance / speed
			// Convert km/h to m/s: (km/h) * (1000m/km) / (3600s/h) = (km/h) / 3.6
			timeToDriveLocationArray[i] = ((double)3.6 * (double)lengthOfSegment) /
					(SPEED_FOR_PLACES[locationTypes[i]]);

			// Accumulate total loop time
			totalTimeForLoop += timeToDriveLocationArray[i];
		}

		// Assign random initial positions for each vehicle on the road
		initialPositionArray = new int[numberOfMobileDevices];
		initialLocationIndexArray =  new int[numberOfMobileDevices];
		timeToReachNextLocationArray =  new double[numberOfMobileDevices];
		for(int i=0; i<numberOfMobileDevices; i++) {
			// Random position anywhere on the road
			initialPositionArray[i] = SimUtils.getRandomNumber(0, totalLengthOfRoad-1);
			
			// Determine which road segment the vehicle starts in
			initialLocationIndexArray[i] = initialPositionArray[i] / lengthOfSegment;
			
			// Calculate time needed to reach the next location from current position
			int remainingDistance = lengthOfSegment - (initialPositionArray[i] % lengthOfSegment);
			timeToReachNextLocationArray[i] = ((double)3.6 * (double)remainingDistance) /
					(SPEED_FOR_PLACES[locationTypes[initialLocationIndexArray[i]]]);
		}
	}

	/**
	 * Calculates the current location of a vehicle at a given simulation time.
	 * Vehicles move in a loop along the road with different speeds in different segments.
	 * 
	 * @param deviceId identifier of the mobile device (vehicle)
	 * @param time current simulation time in seconds
	 * @return Location object containing attractiveness, segment index, and position
	 */
	@Override
	public Location getLocation(int deviceId, double time) {
		int offset = 0;
		double remainingTime = 0;

		int locationIndex = initialLocationIndexArray[deviceId];
		double timeToReachNextLocation = timeToReachNextLocationArray[deviceId];

		if(time < timeToReachNextLocation){
			// Vehicle hasn't reached the next segment yet - still in initial segment
			offset = initialPositionArray[deviceId];
			remainingTime = time;
		}
		else{
			// Vehicle has completed at least one segment, calculate current position in loop
			remainingTime = (time - timeToReachNextLocation) % totalTimeForLoop;
			locationIndex = (locationIndex+1) % locationTypes.length;

			// Find which segment the vehicle is currently in
			while(remainingTime > timeToDriveLocationArray[locationIndex]) {
				remainingTime -= timeToDriveLocationArray[locationIndex];
				locationIndex =  (locationIndex+1) % locationTypes.length;
			}

			offset = locationIndex * lengthOfSegment;
		}

		// Calculate exact position within the current segment
		// Distance = speed * time, convert speed from km/h to m/s
		int x_pos = (int) (offset + ((SPEED_FOR_PLACES[locationTypes[locationIndex]] * remainingTime) / 3.6));

		return new Location(locationTypes[locationIndex], locationIndex, x_pos, 0);
	}

}
