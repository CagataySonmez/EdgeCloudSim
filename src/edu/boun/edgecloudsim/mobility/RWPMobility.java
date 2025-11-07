/*
 * Title:        EdgeCloudSim - Random Waypoint (RWP) mobility model implementation
 * 
 * Description:  This class implements a random waypoint mobility model using a normal distribution for both velocity and pause time.
 * 
 * License:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2019, Tobias Baumann
 */

package edu.boun.edgecloudsim.mobility;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.commons.math3.distribution.AbstractRealDistribution;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

public class RWPMobility extends MobilityModel {
	private ArrayList<Location> datacenters;
	private List<TreeMap<Double, Location>> treeMapArray;
	protected int x_size, y_size;
	protected AbstractRealDistribution velocityDistr;
	protected AbstractRealDistribution pauseTimeDistr;

	/**
	 * Implementation of the random waypoint (RWP) mobility model using a normal distribution for both velocity and pause time.
	 * 
	 * @param _numberOfMobileDevices Number of mobile devices
	 * @param _simulationTime Currently unused, keeping it for compatibility. SimSettings.getInstance().getSimulationTime() is being used instead.
	 * @param _x_size Maximum x-size of the movable area
	 * @param _y_size Maximum y-size of the movable area
	 * @param _velocityDistr Distribution used for the velocity of movement
	 * @param _pauseTimeDistr Distribution used for the waiting time between movements
	 */
	public RWPMobility(int _numberOfMobileDevices, double _simulationTime, int _x_size, int _y_size, AbstractRealDistribution _velocityDistr, AbstractRealDistribution _pauseTimeDistr) {
		super(_numberOfMobileDevices, _simulationTime);
		x_size = _x_size; y_size = _y_size;
		velocityDistr = _velocityDistr; pauseTimeDistr = _pauseTimeDistr;

		if(SimSettings.getInstance().getSimulationTime() != simulationTime) {
			SimLogger.printLine("WARNING (RWPMobility.RWPMobility): Given _simulationTime (" + simulationTime + ") is different from the one in SimSettings (" + SimSettings.getInstance().getSimulationTime() + ").");
		}
	}

	@Override
	public void initialize() {
		readDatacenters();
		treeMapArray = new ArrayList<TreeMap<Double, Location>>();

		for (int i = 0; i < numberOfMobileDevices; i++) {
			// Initialize tree map of each mobile devices
			treeMapArray.add(i, new TreeMap<Double, Location>());

			// assign random initial position
			int x_pos = SimUtils.getRandomNumber(0, x_size);
			int y_pos = SimUtils.getRandomNumber(0, y_size);

			// start locating user shortly after the simulation started (e.g. 10 seconds)
			treeMapArray.get(i).put(SimSettings.CLIENT_ACTIVITY_START_TIME, makeLocation(x_pos, y_pos));
		}

		// calculate random waypoints for all devices for the whole simulation
		for(int i=0; i<numberOfMobileDevices; i++) {
			TreeMap<Double, Location> treeMap = treeMapArray.get(i);

			while(treeMap.lastKey() < SimSettings.getInstance().getSimulationTime()) {
				Entry<Double, Location> lastEntry = treeMap.lastEntry();
				int x_last = lastEntry.getValue().getXPos();
				int y_last = lastEntry.getValue().getYPos();
				int x_new = SimUtils.getRandomNumber(0, x_size-1);
				int y_new = SimUtils.getRandomNumber(0, y_size-1);
				Location newLoc = makeLocation(x_new, y_new);
				double distance = Math.sqrt(Math.pow(x_new - x_last, 2) + Math.pow(y_new - y_last, 2));
				double velocity = Math.abs(velocityDistr.sample());
				double moveTime = distance / velocity;
				//SimLogger.printLine("Time: " + treeMap.lastKey() +" Device " + i + " moves to (" + newLoc.getXPos() + "," + newLoc.getYPos() + ") which needs " + moveTime + " with velocity " + velocity);
				treeMap.put(treeMap.lastKey()+moveTime, newLoc);
				double pauseTime = Math.abs(pauseTimeDistr.sample());
				//SimLogger.printLine("Time: " + treeMap.lastKey() +" Device " + i + " waits for " + pauseTime + ", connected to AP " + newLoc.getServingWlanId() + ", placeTypeIndex=" + newLoc.getPlaceTypeIndex());
				treeMap.put(treeMap.lastKey()+pauseTime, newLoc);
			}
		}
	}

	@Override
	public Location getLocation(int deviceId, double time) {
		TreeMap<Double, Location> treeMap = treeMapArray.get(deviceId);

		Entry<Double, Location> floorEntry = treeMap.floorEntry(time);
		Entry<Double, Location> ceilingEntry = treeMap.ceilingEntry(time);

		if(floorEntry == null){
			SimLogger.printLine("ERROR: No location found for the device '" + deviceId + "' at time '" + time + "'.");
			System.exit(0);
		}

		if(ceilingEntry == null){
			SimLogger.printLine("WARNING (RWPMobility.getLocation): No ceilingEntry for device '" + deviceId + "' at time '" + time + "'.");
			return floorEntry.getValue();
		}
		int x_floor = floorEntry.getValue().getXPos();
		int y_floor = floorEntry.getValue().getYPos();
		int x_ceiling = ceilingEntry.getValue().getXPos();
		int y_ceiling = ceilingEntry.getValue().getYPos();
		
		if(x_floor == x_ceiling && y_floor == y_ceiling) {
			// Both entries have the same position, device is waiting. Return one of the entries.
			return floorEntry.getValue();
		}

		// Device is moving between these two entries. Calculate actual position at requested time:
		int x_delta = x_ceiling - x_floor;
		int y_delta = y_ceiling - y_floor;
		double totalMovingTime = ceilingEntry.getKey() - floorEntry.getKey();
		double requestetMovingTime = time - floorEntry.getKey();
		double timeRatio = requestetMovingTime / totalMovingTime;
		int x_loc = x_floor + (int) Math.round(x_delta*timeRatio);
		int y_loc = y_floor + (int) Math.round(y_delta*timeRatio);
		//SimLogger.printLine("Device " + deviceId + " requested Location at time " + time + ":");
		//SimLogger.printLine("Device is moving from (" + x_floor + "," + y_floor + ") [" + floorEntry.getKey() + "] to (" + x_ceiling + "," + y_ceiling + ") [" + ceilingEntry.getKey() + "].");
		//SimLogger.printLine("timeRatio=" + timeRatio + ", calculated Location: (" + x_loc + "," + y_loc + ")");
		return makeLocation(x_loc, y_loc);
	}

	/**
	 * Read datacenters into own data structure for performance reasons.
	 */
	private void readDatacenters() {
		Document doc = SimSettings.getInstance().getEdgeDevicesDocument();
		NodeList datacenterList = doc.getElementsByTagName("datacenter");
		datacenters = new ArrayList<Location>(datacenterList.getLength());

		for (int i = 0; i < datacenterList.getLength(); i++) {
			Node datacenterNode = datacenterList.item(i);
			Element datacenterElement = (Element) datacenterNode;
			Element location = (Element)datacenterElement.getElementsByTagName("location").item(0);
			int attractiveness = Integer.parseInt(location.getElementsByTagName("attractiveness").item(0).getTextContent());
			int wlan_id = Integer.parseInt(location.getElementsByTagName("wlan_id").item(0).getTextContent());
			int x_pos = Integer.parseInt(location.getElementsByTagName("x_pos").item(0).getTextContent());
			int y_pos = Integer.parseInt(location.getElementsByTagName("y_pos").item(0).getTextContent());
			
			Location dc = new Location(attractiveness, wlan_id, x_pos, y_pos);
			datacenters.add(dc);
		}
	}

	/**
	 * Returns a Location object filled with the appropriate 'servingWlanId' and 'placeTypeIndex' of nearest wireless access point (AP) for a given position in the 2D grid.
	 *
	 * @param x_client X-Coordinate within the 2D grid
	 * @param y_client Y-Coordinate within the 2D grid
	 * @return Finished Location object that contains appropriate 'servingWlanId' and 'placeTypeIndex' of nearest AP (also called 'datacenter' or 'edge device' in EdgeCloudSim).
	 */
	private Location makeLocation(int x_client, int y_client) {
		int nearest_wlan_id = -1;
		int placeTypeIndex = -1;
		double min_distance = Double.MAX_VALUE;

		for (int i = 0; i < datacenters.size(); i++) {
			int x_pos = datacenters.get(i).getXPos();
			int y_pos = datacenters.get(i).getYPos();

			double distance = Math.sqrt(Math.pow(x_pos - x_client, 2) + Math.pow(y_pos - y_client, 2));
			if(distance < min_distance) {
				min_distance = distance;
				nearest_wlan_id = datacenters.get(i).getServingWlanId();
				placeTypeIndex = datacenters.get(i).getPlaceTypeIndex();
			}
		}

		if (placeTypeIndex == -1 || nearest_wlan_id == -1) {
			SimLogger.printLine("ERROR: RWPMobility.makeLocation failed.");
			System.exit(0);
		}

		return new Location(placeTypeIndex, nearest_wlan_id, x_client, y_client);
	}
}
