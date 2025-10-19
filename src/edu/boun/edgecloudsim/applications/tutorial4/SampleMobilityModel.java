/*
 * Title:        EdgeCloudSim - Mobility model implementation
 * 
 * Description: 
 * VehicularMobilityModel implements basic vehicular mobility model
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.tutorial4;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.mobility.MobilityModel;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimUtils;

public class SampleMobilityModel extends MobilityModel {
	// SPEED_FOR_PLACES[index] = km/h associated with attractiveness level (0..2)
	private final double SPEED_FOR_PLACES[] = {20, 40, 60}; //km per hour

	private int lengthOfSegment;
	private double totalTimeForLoop; //seconds
	private int[] locationTypes;

	//prepare following arrays to decrease computation on getLocation() function
	//NOTE: if the number of clients is high, keeping following values in RAM
	//      may be expensive. In that case sacrifice computational resources!
	private int[] initialLocationIndexArray;
	private int[] initialPositionArray; //in meters unit
	private double[] timeToDriveLocationArray;//in seconds unit
	private double[] timeToReachNextLocationArray; //in seconds unit

	// Model summary:
	// - Linear circular road composed of contiguous segments (one per datacenter).
	// - Each segment length inferred from first location's x_pos (assumed uniform).
	// - Vehicle speed depends on location attractiveness (mapped via SPEED_FOR_PLACES).
	// - Precomputes initial position, segment index, and time to next boundary per device
	//   to keep getLocation() O(number_of_crossed_segments) instead of O(total_segments).

	public SampleMobilityModel(int _numberOfMobileDevices, double _simulationTime) {
		super(_numberOfMobileDevices, _simulationTime);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void initialize() {
		// Derive segment length from first datacenter's x_pos (road mirrored => *2)
		//Find total length of the road
		Document doc = SimSettings.getInstance().getEdgeDevicesDocument();
		NodeList datacenterList = doc.getElementsByTagName("datacenter");
		Element location = (Element)((Element)datacenterList.item(0)).getElementsByTagName("location").item(0);
		int x_pos = Integer.parseInt(location.getElementsByTagName("x_pos").item(0).getTextContent());
		lengthOfSegment = x_pos * 2; //assume that all segments have the same length
		int totalLengthOfRoad = lengthOfSegment * datacenterList.getLength();

		//prepare locationTypes array to store attractiveness level of the locations
		locationTypes = new int[datacenterList.getLength()];
		timeToDriveLocationArray = new double[datacenterList.getLength()];
		for(int i=0; i<datacenterList.getLength(); i++) {
			Node datacenterNode = datacenterList.item(i);
			Element datacenterElement = (Element) datacenterNode;
			Element locationElement = (Element)datacenterElement.getElementsByTagName("location").item(0);
			locationTypes[i] = Integer.parseInt(locationElement.getElementsByTagName("attractiveness").item(0).getTextContent());

			//(3600 * lengthOfSegment) / (SPEED_FOR_PLACES[x] * 1000);
			timeToDriveLocationArray[i] = ((double)3.6 * (double)lengthOfSegment) /
					(SPEED_FOR_PLACES[locationTypes[i]]);

			//find the time required to loop in the road
			totalTimeForLoop += timeToDriveLocationArray[i];
		}

		//assign a random x position as an initial position for each device
		initialPositionArray = new int[numberOfMobileDevices];
		initialLocationIndexArray =  new int[numberOfMobileDevices];
		timeToReachNextLocationArray =  new double[numberOfMobileDevices];
		for(int i=0; i<numberOfMobileDevices; i++) {
			initialPositionArray[i] = SimUtils.getRandomNumber(0, totalLengthOfRoad-1);
			initialLocationIndexArray[i] = initialPositionArray[i] / lengthOfSegment;
			timeToReachNextLocationArray[i] = ((double)3.6 *
					(double)(lengthOfSegment - (initialPositionArray[i] % lengthOfSegment))) /
					(SPEED_FOR_PLACES[locationTypes[initialLocationIndexArray[i]]]);
		}
		// Preallocation below trades memory for O(1) reuse during getLocation().
		// Remove arrays and recompute on demand if memory footprint becomes critical for very large device counts.
	}

	@Override
	public Location getLocation(int deviceId, double time) {
		// time: simulation seconds since start
		// Phase 1: still within first partial segment? Use initial offset.
		// Phase 2: advance whole segments using modulo of loop time, subtract segment times.
		// Linear interpolation inside current segment: distance = speed(km/h)*time / 3.6

		int ofset = 0;
		double remainingTime = 0;

		int locationIndex = initialLocationIndexArray[deviceId];
		double timeToReachNextLocation = timeToReachNextLocationArray[deviceId];

		if(time < timeToReachNextLocation){
			ofset = initialPositionArray[deviceId];
			remainingTime = time;
		}
		else{
			remainingTime = (time - timeToReachNextLocation) % totalTimeForLoop;
			locationIndex = (locationIndex+1) % locationTypes.length;

			// When time surpasses first boundary, modulo arithmetic ensures cyclic wrap (ring road).
			// Loop below may traverse multiple segments if remainingTime spans >1 segment (high speed or long time jump).
			while(remainingTime > timeToDriveLocationArray[locationIndex]) {
				// Decrement remainingTime until we locate current segment.
				remainingTime -= timeToDriveLocationArray[locationIndex];
				locationIndex =  (locationIndex+1) % locationTypes.length;
			}

			ofset = locationIndex * lengthOfSegment;
		}

		// x_pos computed as starting offset of segment plus distance progressed in current segment.
		// y dimension is fixed (0) in this linear scenario model.
		int x_pos = (int) (ofset + ( (SPEED_FOR_PLACES[locationTypes[locationIndex]] * remainingTime) / (double)3.6));

		return new Location(locationTypes[locationIndex], locationIndex, x_pos, 0);
	}
}
