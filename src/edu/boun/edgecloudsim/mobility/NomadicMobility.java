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
 * modified 2021, Raphael Freymann
 */

package edu.boun.edgecloudsim.mobility;


import edu.boun.edgecloudsim.core.SimManager;
import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimUtils;

public class NomadicMobility extends MobilityModel {
	private Location[] deviceLocations;
	private int[] datacenterDeviceCount;
	ExponentialDistribution[] expRngList;
	private Location[] datacenters;

	public NomadicMobility(int _numberOfMobileDevices, double _simulationTime) {
		super(_numberOfMobileDevices, _simulationTime);
		// TODO Auto-generated constructor stub
	}

	/**
	 * initializes the model by setting necessary attributes and starting the movement for each device
	 */

	@Override
	public void initialize() {
		readDatacenters();
		deviceLocations = new Location[numberOfMobileDevices];
		datacenterDeviceCount = new int[SimSettings.getInstance().getNumOfEdgeDatacenters()];
		for (int i=0; i<datacenterDeviceCount.length;i++){
			datacenterDeviceCount[i] = 0;
		}
		expRngList = new ExponentialDistribution[SimSettings.getInstance().getNumOfEdgeDatacenters()];
		for (int i = 0; i < datacenters.length; i++) {
			expRngList[i] = new ExponentialDistribution(SimSettings.getInstance().getMobilityLookUpTable()[datacenters[i].getPlaceTypeIndex()]);
		}

		for(int i=0; i<numberOfMobileDevices; i++) {
			int randDatacenterId = SimUtils.getRandomNumber(0, SimSettings.getInstance().getNumOfEdgeDatacenters()-1);

			++datacenterDeviceCount[randDatacenterId];
			deviceLocations[i] = datacenters[randDatacenterId];
			SimManager x = SimManager.getInstance();
			x.schedule(x.getId(),SimSettings.CLIENT_ACTIVITY_START_TIME,SimManager.getMoveDevice(), i);
		}
	}

	/**
	 * move calculates the new location of the given device, alters the device count array of the data centers
	 * accordingly and schedules the next movement of the device
	 * @param deviceId the id of the device to be moved
	 */

	@Override
	public void move(int deviceId){
		boolean placeFound = false;
		int currentLocationId = deviceLocations[deviceId].getServingWlanId();

		while(placeFound == false){
			int newDatacenterId = SimUtils.getRandomNumber(0,SimSettings.getInstance().getNumOfEdgeDatacenters()-1);
			if(newDatacenterId != currentLocationId){
				placeFound = true;
				--datacenterDeviceCount[currentLocationId];
				++datacenterDeviceCount[newDatacenterId];
				deviceLocations[deviceId] = datacenters[newDatacenterId];
				double waitingTime = expRngList[newDatacenterId].sample();
				SimManager x = SimManager.getInstance();
				x.schedule(x.getId(),waitingTime,SimManager.getMoveDevice(), deviceId);
			}
		}
	}

	/**
	 * reads location data for scenario's data centers and stores them as attribute for faster access
	 */
	public void readDatacenters(){
		Document doc = SimSettings.getInstance().getEdgeDevicesDocument();
		NodeList datacenterList = doc.getElementsByTagName("datacenter");
		int count = SimSettings.getInstance().getNumOfEdgeDatacenters();
		datacenters = new Location[count];
		for (int i = 0; i < count; i++){
			Node datacenterNode = datacenterList.item(i);
			Element datacenterElement = (Element) datacenterNode;
			Element location = (Element)datacenterElement.getElementsByTagName("location").item(0);
			String attractiveness = location.getElementsByTagName("attractiveness").item(0).getTextContent();
			int placeTypeIndex = Integer.parseInt(attractiveness);
			int wlan_id = Integer.parseInt(location.getElementsByTagName("wlan_id").item(0).getTextContent());
			int x_pos = Integer.parseInt(location.getElementsByTagName("x_pos").item(0).getTextContent());
			int y_pos = Integer.parseInt(location.getElementsByTagName("y_pos").item(0).getTextContent());
			datacenters[i] = new Location(placeTypeIndex, wlan_id, x_pos, y_pos);
		}
	}

	/**
	 * returns the current location of given device
	 * @param deviceId id of device
	 * @param time left over for compatibility, no purpose
	 * @return location of given device
	 */
	@Override
	public Location getLocation(int deviceId, double time) {
		return deviceLocations[deviceId];
	}

	/**
	 * returns the count of devices, located at the given data center
	 * @param datacenterId id of a data center
	 * @return count of devices located in given data center
	 */
	@Override
	public int getDeviceCount(int datacenterId) {
		return datacenterDeviceCount[datacenterId];
	}
}