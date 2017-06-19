/*
 * Title:        EdgeCloudSim - EdgeTask
 * 
 * Description: 
 * A custom class used in Load Generator Model to store tasks information
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.utils;

import org.apache.commons.math3.distribution.ExponentialDistribution;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.core.SimSettings.APP_TYPES;

public class EdgeTask {
    public APP_TYPES taskType;
    public double startTime;
    public long length, inputFileSize, outputFileSize;
    public int pesNumber;
    public int mobileDeviceId;
    
    public EdgeTask(int _mobileDeviceId, APP_TYPES _taskType, double _startTime, ExponentialDistribution[][] expRngList) {
    	mobileDeviceId=_mobileDeviceId;
    	startTime=_startTime;
    	taskType=_taskType;
    	
    	inputFileSize = (long)expRngList[_taskType.ordinal()][0].sample();
    	outputFileSize =(long)expRngList[_taskType.ordinal()][1].sample();
    	length = (long)expRngList[_taskType.ordinal()][2].sample();
    	
    	pesNumber = (int)SimSettings.getInstance().getTaskLookUpTable()[_taskType.ordinal()][8];
	}
}
