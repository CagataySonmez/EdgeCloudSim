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
import java.util.List;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

public class NomadicMobility extends MobilityModel {
	private List<TreeMap<Double, Location>> treeMapArray;
	
	public NomadicMobility(int _numberOfMobileDevices, double _simulationTime) {
		super(_numberOfMobileDevices, _simulationTime);
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public void initialize() {
		treeMapArray = new ArrayList<TreeMap<Double, Location>>();
		
		ExponentialDistribution[] expRngList = new ExponentialDistribution[SimSettings.getInstance().getNumOfEdgeDatacenters()];

		//create random number generator for each place
		Document doc = SimSettings.getInstance().getEdgeDevicesDocument();
		NodeList datacenterList = doc.getElementsByTagName("datacenter");
		for (int i = 0; i < datacenterList.getLength(); i++) {
			Node datacenterNode = datacenterList.item(i);
			Element datacenterElement = (Element) datacenterNode;
			Element location = (Element)datacenterElement.getElementsByTagName("location").item(0);
			String attractiveness = location.getElementsByTagName("attractiveness").item(0).getTextContent();
			SimSettings.PLACE_TYPES placeType = SimUtils.stringToPlace(attractiveness);
			
			expRngList[i] = new ExponentialDistribution(SimSettings.getInstance().getMobilityLookUpTable()[placeType.ordinal()]);
		}
		
		//initialize tree maps and position of mobile devices
		for(int i=0; i<numberOfMobileDevices; i++) {
			treeMapArray.add(i, new TreeMap<Double, Location>());
			
			int randDatacenterId = SimUtils.getRandomNumber(0, SimSettings.getInstance().getNumOfEdgeDatacenters()-1);
			Node datacenterNode = datacenterList.item(randDatacenterId);
			Element datacenterElement = (Element) datacenterNode;
			Element location = (Element)datacenterElement.getElementsByTagName("location").item(0);
			String attractiveness = location.getElementsByTagName("attractiveness").item(0).getTextContent();
			SimSettings.PLACE_TYPES placeType = SimUtils.stringToPlace(attractiveness);
			int wlan_id = Integer.parseInt(location.getElementsByTagName("wlan_id").item(0).getTextContent());
			int x_pos = Integer.parseInt(location.getElementsByTagName("x_pos").item(0).getTextContent());
			int y_pos = Integer.parseInt(location.getElementsByTagName("y_pos").item(0).getTextContent());

			//start locating user from 10th seconds
			treeMapArray.get(i).put((double)10, new Location(placeType, wlan_id, x_pos, y_pos));
		}
		
		for(int i=0; i<numberOfMobileDevices; i++) {
			TreeMap<Double, Location> treeMap = treeMapArray.get(i);

			while(treeMap.lastKey() < SimSettings.getInstance().getSimulationTime()) {				
				boolean placeFound = false;
				int currentLocationId = treeMap.lastEntry().getValue().getServingWlanId();
				double waitingTime = expRngList[currentLocationId].sample();
				
				while(placeFound == false){
					int newDatacenterId = SimUtils.getRandomNumber(0,SimSettings.getInstance().getNumOfEdgeDatacenters()-1);
					if(newDatacenterId != currentLocationId){
						placeFound = true;
						Node datacenterNode = datacenterList.item(newDatacenterId);
						Element datacenterElement = (Element) datacenterNode;
						Element location = (Element)datacenterElement.getElementsByTagName("location").item(0);
						String attractiveness = location.getElementsByTagName("attractiveness").item(0).getTextContent();
						SimSettings.PLACE_TYPES placeType = SimUtils.stringToPlace(attractiveness);
						int wlan_id = Integer.parseInt(location.getElementsByTagName("wlan_id").item(0).getTextContent());
						int x_pos = Integer.parseInt(location.getElementsByTagName("x_pos").item(0).getTextContent());
						int y_pos = Integer.parseInt(location.getElementsByTagName("y_pos").item(0).getTextContent());
						
						treeMap.put(treeMap.lastKey()+waitingTime, new Location(placeType, wlan_id, x_pos, y_pos));
					}
				}
				if(!placeFound){
					SimLogger.printLine("impossible is occured! location cannot be assigned to the device!");
			    	System.exit(0);
				}
			}
		}

	}

	@Override
	public Location getLocation(int deviceId, double time) {
		TreeMap<Double, Location> treeMap = treeMapArray.get(deviceId);
		
		Entry<Double, Location> e = treeMap.floorEntry(time);
	    
	    if(e == null){
	    	SimLogger.printLine("impossible is occured! no location is found for the device!");
	    	System.exit(0);
	    }
	    
		return e.getValue();
	}

}
