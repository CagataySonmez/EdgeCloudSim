/* SPDX-License-Identifier: GPL-3.0-or-later */
package edu.boun.edgecloudsim.core;

import java.nio.file.Path;
import java.nio.file.Paths;

import edu.boun.edgecloudsim.applications.sample_app5.WekaWrapper;

/** Validates every bundled configuration and the sample_app5 WEKA model set. */
public final class ConfigurationSmokeTest {
	private ConfigurationSmokeTest() {
	}

	/**
	 * Validates configurations rooted at the supplied project directory.
	 *
	 * @param args one argument containing the project directory
	 */
	public static void main(String[] args) {
		if (args.length != 1) {
			throw new IllegalArgumentException("Expected the project directory as the only argument");
		}
		Path projectDirectory = Paths.get(args[0]).toAbsolutePath().normalize();
		String[] applications = {
				"sample_app1", "sample_app2", "sample_app3", "sample_app4", "sample_app5",
				"tutorial1", "tutorial2", "tutorial3", "tutorial4"
		};
		for (String application : applications) {
			validateConfiguration(projectDirectory.resolve("scripts").resolve(application).resolve("config"),
					"default_config.properties", "edge_devices.xml", "applications.xml");
		}
		Path tutorial5 = projectDirectory.resolve("scripts/tutorial5/config");
		for (int scenario = 1; scenario <= 3; scenario++) {
			validateConfiguration(tutorial5,
					"default_config" + scenario + ".properties",
					"edge_devices" + scenario + ".xml",
					"applications" + scenario + ".xml");
		}
		WekaWrapper.getInstance().initialize(
				"MultilayerPerceptron",
				"LinearRegression",
				projectDirectory.resolve("scripts/sample_app5/config/weka").toString());
		System.out.println("Configuration and WEKA model smoke tests passed.");
	}

	private static void validateConfiguration(Path directory, String properties,
			String edgeDevices, String applications) {
		boolean initialized = SimSettings.getInstance().initialize(
				directory.resolve(properties).toString(),
				directory.resolve(edgeDevices).toString(),
				directory.resolve(applications).toString());
		if (!initialized) {
			throw new AssertionError("Configuration failed validation: " + directory.resolve(properties));
		}
	}
}
