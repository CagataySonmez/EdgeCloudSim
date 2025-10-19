/*
 * Title:        EdgeCloudSim - Location
 * 
 * Description:  Location class used in EdgeCloudSim
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.utils;

/**
 * Represents a geographical location within the EdgeCloudSim simulation environment.
 * 
 * <p>The Location class encapsulates spatial and network connectivity information for
 * mobile devices and infrastructure components. It provides a discrete coordinate system
 * with network service area mapping that enables realistic mobility and communication
 * modeling in edge computing scenarios.</p>
 * 
 * <p><b>Core Components:</b>
 * <ul>
 *   <li><b>Spatial Coordinates:</b> X,Y position in the simulation grid</li>
 *   <li><b>Network Service:</b> WLAN access point assignment for connectivity</li>
 *   <li><b>Place Classification:</b> Location type for behavior modeling</li>
 * </ul></p>
 * 
 * <p><b>Usage Scenarios:</b>
 * <ul>
 *   <li>Mobile device position tracking during simulation</li>
 *   <li>Network service area determination for delay calculations</li>
 *   <li>Location-based task generation and processing decisions</li>
 *   <li>Mobility pattern validation and visualization</li>
 * </ul></p>
 * 
 * <p>Location equality is determined by coordinate matching only, allowing devices
 * at the same position to share network resources and experience similar conditions.</p>
 * 
 * @see edu.boun.edgecloudsim.mobility.MobilityModel
 * @see edu.boun.edgecloudsim.network.NetworkModel
 */
public class Location {
	/** X-coordinate position in the simulation grid */
	private int xPos;
	
	/** Y-coordinate position in the simulation grid */
	private int yPos;
	
	/** Identifier of the WLAN access point serving this location */
	private int servingWlanId;
	
	/** Place type index defining the location category (residential, commercial, etc.) */
	private int placeTypeIndex;
	
	/**
	 * Constructs a new Location with the specified spatial and network parameters.
	 * 
	 * <p>Creates a complete location specification including both spatial coordinates
	 * and network service information. The place type index enables location-based
	 * behavior modeling, while the serving WLAN ID determines network connectivity
	 * and communication characteristics.</p>
	 * 
	 * @param _placeTypeIndex the type/category of this location (e.g., residential, commercial)
	 * @param _servingWlanId the ID of the WLAN access point serving this location
	 * @param _xPos the X-coordinate in the simulation grid
	 * @param _yPos the Y-coordinate in the simulation grid
	 */
	public Location(int _placeTypeIndex, int _servingWlanId, int _xPos, int _yPos){
		servingWlanId = _servingWlanId;
		placeTypeIndex = _placeTypeIndex;
		xPos = _xPos;
		yPos = _yPos;
	}
	
	/**
	 * Default constructor that creates an uninitialized Location.
	 * 
	 * <p>Creates a Location object with default values. This constructor is typically
	 * used when location parameters will be set later through other mechanisms or
	 * when a placeholder location object is needed.</p>
	 * 
	 * <p><b>Warning:</b> Location created with this constructor will have undefined
	 * spatial and network properties until properly initialized.</p>
	 */
	public Location() {
	}
	
	/**
	 * Determines if two Location objects represent the same spatial position.
	 * 
	 * <p>Location equality is based solely on spatial coordinates (X,Y position).
	 * Network service parameters (WLAN ID, place type) are not considered in equality
	 * comparison, allowing devices at the same coordinates to be treated as co-located
	 * regardless of their specific network or behavioral characteristics.</p>
	 * 
	 * <p>This design enables:
	 * <ul>
	 *   <li>Grouping devices by physical location for congestion modeling</li>
	 *   <li>Network resource sharing among co-located devices</li>
	 *   <li>Location-based service optimization</li>
	 * </ul></p>
	 * 
	 * @param other the object to compare with this Location
	 * @return true if both locations have identical X,Y coordinates, false otherwise
	 */
	@Override
	public boolean equals(Object other){
		boolean result = false;
		// Null safety check
	    if (other == null) return false;
	    // Type safety check
	    if (!(other instanceof Location)) return false;
	    // Reference equality optimization
	    if (other == this) return true;
	    
	    // Compare spatial coordinates only (network parameters ignored for equality)
	    Location otherLocation = (Location)other;
	    if(this.xPos == otherLocation.xPos && this.yPos == otherLocation.yPos)
	    	result = true;

	    return result;
	}

	/**
	 * Returns the WLAN access point ID serving this location.
	 * 
	 * <p>The serving WLAN ID determines which wireless access point provides
	 * network connectivity for devices at this location. This information is
	 * crucial for network delay calculations, bandwidth allocation, and
	 * connectivity management in the simulation.</p>
	 * 
	 * @return the identifier of the WLAN access point serving this location
	 */
	public int getServingWlanId(){
		return servingWlanId;
	}
	
	/**
	 * Returns the place type index for location categorization.
	 * 
	 * <p>The place type index classifies locations into categories such as
	 * residential, commercial, industrial, or recreational areas. This
	 * classification enables location-specific behavior modeling, task
	 * generation patterns, and mobility characteristics.</p>
	 * 
	 * @return the place type index defining this location's category
	 */
	public int getPlaceTypeIndex(){
		return placeTypeIndex;
	}
	
	/**
	 * Returns the X-coordinate of this location in the simulation grid.
	 * 
	 * <p>The X-coordinate represents the horizontal position within the
	 * simulation's spatial coordinate system. Together with the Y-coordinate,
	 * it uniquely identifies the spatial position for mobility tracking
	 * and distance calculations.</p>
	 * 
	 * @return the X-coordinate position
	 */
	public int getXPos(){
		return xPos;
	}
	
	/**
	 * Returns the Y-coordinate of this location in the simulation grid.
	 * 
	 * <p>The Y-coordinate represents the vertical position within the
	 * simulation's spatial coordinate system. Combined with the X-coordinate,
	 * it enables precise location tracking and spatial relationship
	 * calculations between devices and infrastructure.</p>
	 * 
	 * @return the Y-coordinate position
	 */
	public int getYPos(){
		return yPos;
	}
}
