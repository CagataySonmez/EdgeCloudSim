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

	@Override
	public void initialize() {
		readDatacenters();
		deviceLocations = new Location[numberOfMobileDevices];
		datacenterDeviceCount = new int[SimSettings.getInstance().getNumOfEdgeDatacenters()];
		for (int i=0; i<datacenterDeviceCount.length;i++){
			datacenterDeviceCount[i] = 0;
		}

		expRngList = new ExponentialDistribution[SimSettings.getInstance().getNumOfEdgeDatacenters()];

		//create random number generator for each place
//		Document doc = SimSettings.getInstance().getEdgeDevicesDocument();
//		NodeList datacenterList = doc.getElementsByTagName("datacenter");

		for (int i = 0; i < datacenters.length; i++) {
//			Node datacenterNode = datacenterList.item(i);
//			Element datacenterElement = (Element) datacenterNode;
//			Element location = (Element)datacenterElement.getElementsByTagName("location").item(0);
//			String attractiveness = location.getElementsByTagName("attractiveness").item(0).getTextContent();
//			int placeTypeIndex = Integer.parseInt(attractiveness);

			expRngList[i] = new ExponentialDistribution(SimSettings.getInstance().getMobilityLookUpTable()[datacenters[i].getPlaceTypeIndex()]);
		}

		//initialize locations of each device and start scheduling of movement events
		for(int i=0; i<numberOfMobileDevices; i++) {
			int randDatacenterId = SimUtils.getRandomNumber(0, SimSettings.getInstance().getNumOfEdgeDatacenters()-1);
//			Node datacenterNode = datacenterList.item(randDatacenterId);
//			Element datacenterElement = (Element) datacenterNode;
//			Element location = (Element)datacenterElement.getElementsByTagName("location").item(0);
//			String attractiveness = location.getElementsByTagName("attractiveness").item(0).getTextContent();
//			int placeTypeIndex = Integer.parseInt(attractiveness);
//			int wlan_id = Integer.parseInt(location.getElementsByTagName("wlan_id").item(0).getTextContent());
//			int x_pos = Integer.parseInt(location.getElementsByTagName("x_pos").item(0).getTextContent());
//			int y_pos = Integer.parseInt(location.getElementsByTagName("y_pos").item(0).getTextContent());

			++datacenterDeviceCount[randDatacenterId];
			deviceLocations[i] = datacenters[randDatacenterId];
			//double waitingTime = expRngList[deviceLocations[i].getServingWlanId()].sample();
			SimManager x = SimManager.getInstance();
			x.schedule(x.getId(),SimSettings.CLIENT_ACTIVITY_START_TIME,SimManager.getMoveDevice(), i);

		}



	}

	@Override
	public void move(int deviceId){
		boolean placeFound = false;
		int currentLocationId = deviceLocations[deviceId].getServingWlanId();
//		Document doc = SimSettings.getInstance().getEdgeDevicesDocument();
//		NodeList datacenterList = doc.getElementsByTagName("datacenter");

		while(placeFound == false){
			int newDatacenterId = SimUtils.getRandomNumber(0,SimSettings.getInstance().getNumOfEdgeDatacenters()-1);
			if(newDatacenterId != currentLocationId){
				placeFound = true;
//				Node datacenterNode = datacenterList.item(newDatacenterId);
//				Element datacenterElement = (Element) datacenterNode;
//				Element location = (Element)datacenterElement.getElementsByTagName("location").item(0);
//				String attractiveness = location.getElementsByTagName("attractiveness").item(0).getTextContent();
//				int placeTypeIndex = Integer.parseInt(attractiveness);
//				int wlan_id = Integer.parseInt(location.getElementsByTagName("wlan_id").item(0).getTextContent());
//				int x_pos = Integer.parseInt(location.getElementsByTagName("x_pos").item(0).getTextContent());
//				int y_pos = Integer.parseInt(location.getElementsByTagName("y_pos").item(0).getTextContent());


				--datacenterDeviceCount[currentLocationId];
				++datacenterDeviceCount[newDatacenterId];
				deviceLocations[deviceId] = datacenters[newDatacenterId];
				double waitingTime = expRngList[newDatacenterId].sample();
				SimManager x = SimManager.getInstance();
				x.schedule(x.getId(),waitingTime,SimManager.getMoveDevice(), deviceId);
			}
		}
	}

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

	@Override
	public Location getLocation(int deviceId, double time) {
		return deviceLocations[deviceId];
	}

	@Override
	public int getDeviceCount(int datacenterId) {
		return datacenterDeviceCount[datacenterId];
	}
}