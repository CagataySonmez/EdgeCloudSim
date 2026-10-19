/* SPDX-License-Identifier: GPL-3.0-or-later */
/*
 * Title:        EdgeCloudSim - Nomadic Mobility model implementation
 *
 * Description:
 * MobilityModel implements basic nomadic mobility model where the
 * place of the devices are changed from time to time instead of a
 * continuous location update.
 *
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.mobility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.random.Well19937c;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimUtils;

/**
 * Generates discrete device-location timelines. Residence time at a location is
 * exponentially distributed with the mean configured for that location's
 * attractiveness class; the next location is selected uniformly from the other
 * configured locations.
 */
public class NomadicMobility extends MobilityModel {
	private List<TreeMap<Double, Location>> treeMapArray;    // Timeline of location changes for each device

	/**
	 * Constructs a NomadicMobility model with specified parameters.
	 *
	 * @param _numberOfMobileDevices Total number of mobile devices to model
	 * @param _simulationTime Duration of the simulation in seconds
	 */
	public NomadicMobility(int _numberOfMobileDevices, double _simulationTime) {
		super(_numberOfMobileDevices, _simulationTime);
	}

	/**
	 * Initializes the nomadic mobility model by generating movement timelines.
	 * Creates exponential distributions for each location based on attractiveness,
	 * assigns initial positions to devices, and generates complete movement
	 * trajectories for the entire simulation period.
	 */
	@Override
	public void initialize() {
		treeMapArray = new ArrayList<TreeMap<Double, Location>>();

		int datacenterCount = SimSettings.getInstance().getNumOfEdgeDatacenters();
		if (datacenterCount <= 0) {
			throw new IllegalStateException("Nomadic mobility requires at least one edge datacenter");
		}
		// Create exponential distribution generators for residence time at each location
		ExponentialDistribution[] expRngList = new ExponentialDistribution[datacenterCount];
		Location[] datacenterLocations = new Location[datacenterCount];
		Map<Integer, Integer> datacenterIndexByWlanId = new HashMap<Integer, Integer>();

		// Create exponential RNG for each datacenter location based on attractiveness
		Document doc = SimSettings.getInstance().getEdgeDevicesDocument();
		NodeList datacenterList = doc.getElementsByTagName("datacenter");
		if (datacenterList.getLength() != datacenterCount) {
			throw new IllegalStateException("Parsed datacenter count does not match the mobility configuration");
		}
		for (int i = 0; i < datacenterList.getLength(); i++) {
			Node datacenterNode = datacenterList.item(i);
			Element datacenterElement = (Element) datacenterNode;
			Element location = (Element)datacenterElement.getElementsByTagName("location").item(0);
			String attractiveness = location.getElementsByTagName("attractiveness").item(0).getTextContent();
			int placeTypeIndex = Integer.parseInt(attractiveness);
			if (placeTypeIndex < 0
					|| placeTypeIndex >= SimSettings.getInstance().getMobilityLookUpTable().length) {
				throw new IllegalArgumentException("Invalid attractiveness index: " + placeTypeIndex);
			}
			int wlanId = Integer.parseInt(location.getElementsByTagName("wlan_id").item(0).getTextContent());
			int xPos = Integer.parseInt(location.getElementsByTagName("x_pos").item(0).getTextContent());
			int yPos = Integer.parseInt(location.getElementsByTagName("y_pos").item(0).getTextContent());
			if (datacenterIndexByWlanId.put(wlanId, i) != null) {
				throw new IllegalArgumentException("Duplicate WLAN ID in edge-device configuration: " + wlanId);
			}
			datacenterLocations[i] = new Location(placeTypeIndex, wlanId, xPos, yPos);

			// Create exponential distribution based on location attractiveness (mean residence time)
			expRngList[i] = new ExponentialDistribution(
					new Well19937c(SimUtils.nextSeed()),
					SimSettings.getInstance().getMobilityLookUpTable()[placeTypeIndex]);
		}

		// Initialize timeline maps and assign initial positions to mobile devices
		for(int i=0; i<numberOfMobileDevices; i++) {
			treeMapArray.add(i, new TreeMap<Double, Location>());

			// Randomly assign initial location to each device
			int randDatacenterId = SimUtils.getRandomNumber(0, SimSettings.getInstance().getNumOfEdgeDatacenters()-1);
			// Set initial location at simulation start time (allows for initialization period)
			treeMapArray.get(i).put(SimSettings.CLIENT_ACTIVITY_START_TIME,
					datacenterLocations[randDatacenterId]);
		}

		// Generate complete movement trajectory for each device throughout simulation
		for(int i=0; i<numberOfMobileDevices; i++) {
			TreeMap<Double, Location> treeMap = treeMapArray.get(i);

			// Continue generating location changes until simulation end time
			while(treeMap.lastKey() < SimSettings.getInstance().getSimulationTime()) {
				boolean placeFound = false;
				Integer currentDatacenterIndex = datacenterIndexByWlanId.get(
						treeMap.lastEntry().getValue().getServingWlanId());
				if (currentDatacenterIndex == null) {
					throw new IllegalStateException("Current WLAN ID is not mapped to a datacenter");
				}
				// Sample residence time from exponential distribution for current location
				double waitingTime = expRngList[currentDatacenterIndex].sample();

				// Select a new location different from current location
				while(placeFound == false){
					int newDatacenterId = SimUtils.getRandomNumber(0,SimSettings.getInstance().getNumOfEdgeDatacenters()-1);
					// Ensure device moves to a different location (unless only one location exists)
					if(datacenterCount == 1 || newDatacenterId != currentDatacenterIndex){
						placeFound = true;
						// Add new location to timeline at calculated transition time
						treeMap.put(treeMap.lastKey()+waitingTime, datacenterLocations[newDatacenterId]);
					}
				}
			}
		}

	}

	/**
	 * Returns the current location of a mobile device at the specified time.
	 * Uses the pre-generated timeline to find the appropriate location entry
	 * that was active at the requested time.
	 *
	 * @param deviceId Unique identifier of the mobile device
	 * @param time Simulation time when location is requested (in seconds)
	 * @return Location object containing the device's position and serving edge server
	 */
	@Override
	public Location getLocation(int deviceId, double time) {
		TreeMap<Double, Location> treeMap = treeMapArray.get(deviceId);

		// Find the latest location entry before or at the requested time
		Entry<Double, Location> e = treeMap.floorEntry(time);

	    if(e == null){
			throw new IllegalStateException(
					"No location is available for device " + deviceId + " at simulation time " + time);
	    }

		return e.getValue();
	}

}
