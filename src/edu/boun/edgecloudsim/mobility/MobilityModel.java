/* SPDX-License-Identifier: GPL-3.0-or-later */
/*
 * Title:        EdgeCloudSim - Mobility Model
 *
 * Description:
 * Defines time-dependent mobile-device locations. Custom mobility models
 * extend this class and are supplied by ScenarioFactory.
 *
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.mobility;

import edu.boun.edgecloudsim.utils.Location;

/**
 * Abstract base class for modeling mobile device movement in EdgeCloudSim.
 *
 * This class defines the interface for calculating and tracking the geographic
 * locations of mobile devices throughout the simulation. Mobility models are
 * essential for edge computing scenarios where device proximity to edge servers
 * affects network latency, handoff decisions, and resource allocation strategies.
 *
 * Key responsibilities:
 * - Device location calculation based on time and mobility patterns
 * - Geographic trajectory management for mobile devices
 * - Integration with edge server selection and handoff mechanisms
 * - Support for various mobility patterns (random walk, nomadic, etc.)
 *
 * Custom mobility models can be implemented by extending this class and
 * providing concrete location calculation algorithms via the ScenarioFactory pattern.
 *
 * The mobility model works in conjunction with EdgeCloudSim's location-aware
 * orchestration and network-delay calculations.
 */
public abstract class MobilityModel {
	/** Number of mobile devices represented by the model. */
	protected int numberOfMobileDevices;
	/** Simulation duration in seconds. */
	protected double simulationTime;

	/**
	 * Constructs a MobilityModel with specified device count and simulation duration.
	 *
	 * @param _numberOfMobileDevices Total number of mobile devices to model
	 * @param _simulationTime Duration of the simulation in seconds
	 */
	public MobilityModel(int _numberOfMobileDevices, double _simulationTime){
		numberOfMobileDevices=_numberOfMobileDevices;
		simulationTime=_simulationTime;
	};

	/**
	 * Default constructor creating an empty MobilityModel.
	 * Device count and simulation time should be set through other means
	 * when using this constructor.
	 */
	public MobilityModel() {
	}

	/**
	 * Initializes the mobility model with device-specific movement patterns.
	 * This method should set up initial positions, movement trajectories,
	 * and any required data structures for location calculations throughout
	 * the simulation period.
	 */
	public abstract void initialize();

	/**
	 * Returns the geographic location of a mobile device at a specific time.
	 * This method implements the core mobility logic, calculating device
	 * position based on the mobility model's movement patterns and timing.
	 *
	 * @param deviceId Unique identifier of the mobile device
	 * @param time Simulation time when location is requested (in seconds)
	 * @return Location object containing coordinates and associated edge server information
	 */
	public abstract Location getLocation(int deviceId, double time);
}
