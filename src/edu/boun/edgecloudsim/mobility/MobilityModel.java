/*
 * Title:        EdgeCloudSim - Mobility Model
 * 
 * Description: 
 * MobilityModel is an abstract class which is used for calculating the
 * location of each mobile devices with respect to the time. For those who
 * wants to add a custom Mobility Model to EdgeCloudSim should extend
 * this class and provide a concreate instance via ScenarioFactory
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.mobility;

import edu.boun.edgecloudsim.utils.Location;

public abstract class MobilityModel {
	protected int numberOfMobileDevices;
	protected double simulationTime;
	
	public MobilityModel(int _numberOfMobileDevices, double _simulationTime){
		numberOfMobileDevices=_numberOfMobileDevices;
		simulationTime=_simulationTime;
	};
	
	/*
	 * calculate location of the devices according to related mobility model
	 */
	public abstract void initialize();
	
	/*
	 * returns location of a device at a certain time
	 */
	public abstract Location getLocation(int deviceId, double time);
}
