/* SPDX-License-Identifier: GPL-3.0-or-later */
package edu.boun.edgecloudsim.applications.sample_app5;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

/**
 * Dependency-free regression tests for core deterministic and mathematical behavior.
 */
public final class CoreRegressionTest {
	private static final double EPSILON = 1.0e-12;

	private CoreRegressionTest() {
	}

	/**
	 * Runs the dependency-free regression checks.
	 *
	 * @param args ignored
	 * @throws Exception if reflective access required by a check fails
	 */
	public static void main(String[] args) throws Exception {
		testDeterministicRandomSequence();
		testInclusiveIntegerBounds();
		testCompletedStatusCompatibility();
		testLocationEqualityContract();
		testGameTheoryExclusion();
		testBanditIncrementalMean();
		testFailureRateScale();
		System.out.println("Core regression tests passed.");
	}

	private static void testCompletedStatusCompatibility() {
		assertTrue(SimLogger.TASK_STATUS.COMPLETED == SimLogger.TASK_STATUS.COMLETED,
				"The corrected completed-status spelling must remain an identity alias");
		assertEquals(10, SimLogger.TASK_STATUS.values().length,
				"The compatibility alias must not alter enum ordinals or serialized values");
	}

	private static void testDeterministicRandomSequence() {
		SimUtils.setSeed(123456789L);
		long first = SimUtils.getRandomLongNumber(Long.MIN_VALUE, Long.MAX_VALUE);
		int second = SimUtils.getRandomNumber(Integer.MIN_VALUE, Integer.MAX_VALUE);
		SimUtils.setSeed(123456789L);
		assertEquals(first, SimUtils.getRandomLongNumber(Long.MIN_VALUE, Long.MAX_VALUE),
				"Long sequence must be reproducible");
		assertEquals(second, SimUtils.getRandomNumber(Integer.MIN_VALUE, Integer.MAX_VALUE),
				"Integer sequence must be reproducible");
	}

	private static void testInclusiveIntegerBounds() {
		SimUtils.setSeed(7L);
		Set<Integer> observed = new HashSet<Integer>();
		for (int i = 0; i < 1_000; i++) {
			int value = SimUtils.getRandomNumber(-1, 1);
			assertTrue(value >= -1 && value <= 1, "Random integer escaped its requested bounds");
			observed.add(value);
		}
		assertEquals(3, observed.size(), "Inclusive random range must reach both endpoints");
	}

	private static void testLocationEqualityContract() {
		Location first = new Location(0, 1, 10, 20);
		Location second = new Location(2, 9, 10, 20);
		assertTrue(first.equals(second), "Locations with equal coordinates must be equal");
		assertEquals(first.hashCode(), second.hashCode(), "Equal locations must have equal hash codes");
	}

	private static void testGameTheoryExclusion() throws Exception {
		GameTheoryHelper helper = new GameTheoryHelper(1, 2, 3);
		setField(helper, "pVector", new double[] {0.1, 0.2, 0.3});
		setField(helper, "arrivalRateVector", new double[] {0.5, 0.5, 0.5});
		Method multiplyArray = GameTheoryHelper.class.getDeclaredMethod("multiplyArray", int.class);
		multiplyArray.setAccessible(true);
		double result = ((Double) multiplyArray.invoke(helper, 1)).doubleValue();
		assertClose(0.95 * 0.85, result, "Excluding one vehicle must not truncate the remaining product");
	}

	private static void testBanditIncrementalMean() throws Exception {
		MultiArmedBanditHelper helper = new MultiArmedBanditHelper(100, 1_000);
		helper.initialize(new double[] {10, 20, 30}, 100);
		helper.updateUtility(0, 0.3);
		Field utilityField = MultiArmedBanditHelper.class.getDeclaredField("U_tn");
		utilityField.setAccessible(true);
		double[] utilities = (double[]) utilityField.get(helper);
		assertClose(0.2, utilities[0], "Incremental mean must divide by the updated observation count");
	}

	private static void testFailureRateScale() {
		OrchestratorStatisticLogger logger = new OrchestratorStatisticLogger();
		OrchestratorStatisticLogger.StatItem stat = logger.new StatItem();
		stat.numOfCompletedTasks = 3;
		stat.numOfFailedTasks = 1;
		assertClose(25.0, stat.getFailureRate(), "Failure rate public contract must remain a percentage");
	}

	private static void setField(Object target, String name, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static void assertClose(double expected, double actual, String message) {
		if (Math.abs(expected - actual) > EPSILON) {
			throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
		}
	}

	private static void assertEquals(long expected, long actual, String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
		}
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
