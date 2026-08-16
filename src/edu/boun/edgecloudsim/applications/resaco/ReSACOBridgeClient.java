/*
 * Title:        EdgeCloudSim - ReSACO Bridge Client
 *
 * Description:
 * TCP client for the Python ReSACO inference/online-learning bridge
 * (ReSACO/bridge/inference_server.py), implementing the Java side of the
 * Deployment Phase (Algorithm 4 in the ReSACO paper): ReSACOEdgeOrchestrator
 * asks this class for an offloading decision (ACT), and
 * ReSACOMobileDeviceManager later reports the task's real outcome
 * (OUTCOME) so the served policy keeps adapting online (where applicable).
 *
 * The bridge serves six algorithms at once (RESACO, SAC_BASELINE,
 * DDPG_BASELINE, A2C_BASELINE, A3C_BASELINE -- Section V-C of the paper --
 * plus the MULTITASK_BASELINE joint-pretraining control) selected
 * per-request by the "algo" parameter -- callers pass the current
 * orchestrator policy string straight through, so listing them as
 * orchestrator_policies (default_config.properties lists all six) drives
 * each one through the real CloudSim simulation, not just ReSACO.
 *
 * If the bridge process isn't running, every call fails fast (after a short
 * reconnect backoff) and returns a sentinel so callers can fall back to a
 * static heuristic instead of crashing the simulation.
 *
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 */

package edu.boun.edgecloudsim.applications.resaco;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.utils.SimLogger;

public class ReSACOBridgeClient {
	public static final int NO_ACTION = -1;
	private static final long RECONNECT_BACKOFF_MS = 5000;
	// Without this, a hung bridge (not down, just stuck) would block
	// in.readLine() forever, freezing the whole simulation -- unlike a
	// refused/reset connection, which ensureConnected() already handles.
	// SocketTimeoutException extends IOException, so it flows through the
	// exact same catch blocks (closeQuietly() + NO_ACTION) as any other
	// connection failure below.
	private static final int READ_TIMEOUT_MS = 10000;

	private static ReSACOBridgeClient instance;

	private final String host;
	private final int port;
	private final Map<String, Double> submissionClock = new ConcurrentHashMap<String, Double>();

	private Socket socket;
	private BufferedReader in;
	private BufferedWriter out;
	private long lastFailureTimeMs = -1;
	private boolean warnedOnce = false;

	/**
	 * Measured ACT round-trip times (write -> readLine, i.e. TCP + bridge
	 * inference + any online-update work the bridge does inline), the real
	 * per-decision cost that ReSACOStateBuilder.RESACO_DECISION_LATENCY_SECONDS
	 * lets the simulation charge. ~8 bytes per decision; a 2,000-device run
	 * makes a few tens of thousands of decisions, so keeping them all for
	 * exact percentiles is cheap. Guarded by this client's own monitor
	 * (selectAction is synchronized; printAndResetLatencyStats synchronizes
	 * explicitly for the MainApp thread).
	 */
	private final java.util.ArrayList<Long> actLatencyNanos = new java.util.ArrayList<Long>();

	private ReSACOBridgeClient() {
		this(System.getProperty("resaco.host", "127.0.0.1"),
				Integer.parseInt(System.getProperty("resaco.port", "8765")));
	}

	/** Package-private: lets tests build an isolated instance (own host/port,
	 * bypassing the getInstance() singleton) without touching global system
	 * properties or the shared singleton other tests might also be using. */
	ReSACOBridgeClient(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public static synchronized ReSACOBridgeClient getInstance() {
		if (instance == null) {
			instance = new ReSACOBridgeClient();
		}
		return instance;
	}

	private synchronized boolean ensureConnected() {
		if (socket != null && socket.isConnected() && !socket.isClosed()) {
			return true;
		}
		long now = System.currentTimeMillis();
		if (lastFailureTimeMs > 0 && (now - lastFailureTimeMs) < RECONNECT_BACKOFF_MS) {
			return false;
		}
		try {
			socket = new Socket(host, port);
			socket.setTcpNoDelay(true);
			socket.setSoTimeout(READ_TIMEOUT_MS);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			lastFailureTimeMs = -1;
			return true;
		} catch (IOException e) {
			if (!warnedOnce) {
				SimLogger.printLine("ReSACO bridge unavailable at " + host + ":" + port
						+ " (" + e.getMessage() + "). Falling back to a static heuristic policy.");
				warnedOnce = true;
			}
			socket = null;
			lastFailureTimeMs = now;
			return false;
		}
	}

	/**
	 * Requests an offloading decision for the given task/state from the
	 * named algorithm's served policy (RESACO, SAC_BASELINE, DDPG_BASELINE,
	 * A2C_BASELINE, A3C_BASELINE or MULTITASK_BASELINE). Also records the
	 * current simulation clock so {@link #elapsedSince(String)} can later
	 * compute the task's service time for the reward signal (Eq. 9).
	 *
	 * @return action in {0..N+1} (0=device, 1..N=edge, N+1=cloud), or
	 *         {@link #NO_ACTION} if the bridge or that algorithm is unavailable.
	 */
	public synchronized int selectAction(String algo, String requestId, double[] state) {
		submissionClock.put(requestId, CloudSim.clock());
		if (!ensureConnected()) {
			return NO_ACTION;
		}
		try {
			StringBuilder sb = new StringBuilder("ACT ").append(algo).append(' ').append(requestId);
			for (double v : state) {
				sb.append(' ').append(v);
			}
			long t0 = System.nanoTime();
			out.write(sb.toString());
			out.newLine();
			out.flush();
			String response = in.readLine();
			if (response == null) {
				throw new IOException("connection closed by bridge");
			}
			response = response.trim();
			if (response.startsWith("ERROR")) {
				SimLogger.printLine("ReSACO bridge error: " + response);
				return NO_ACTION;
			}
			// Recorded only for replies that carry a real decision: an ERROR
			// reply involves no policy inference (and its task is routed by
			// the fallback heuristic), so counting its fast round-trip would
			// bias the DECISION_LATENCY_SECONDS calibration low.
			actLatencyNanos.add(System.nanoTime() - t0);
			return Integer.parseInt(response);
		} catch (Exception e) {
			closeQuietly();
			return NO_ACTION;
		}
	}

	/** Elapsed simulation time since {@link #selectAction} was called for this request, or -1 if unknown. */
	public double elapsedSince(String requestId) {
		Double t0 = submissionClock.remove(requestId);
		if (t0 == null) {
			return -1;
		}
		return CloudSim.clock() - t0;
	}

	public synchronized void reportOutcome(String algo, String requestId, double reward, boolean done, double[] nextState) {
		if (!ensureConnected()) {
			return;
		}
		try {
			StringBuilder sb = new StringBuilder("OUTCOME ").append(algo).append(' ').append(requestId)
					.append(' ').append(reward).append(' ').append(done ? 1 : 0);
			for (double v : nextState) {
				sb.append(' ').append(v);
			}
			out.write(sb.toString());
			out.newLine();
			out.flush();
			in.readLine(); // consume ack, content not needed
		} catch (Exception e) {
			closeQuietly();
		}
	}

	/**
	 * Asks the bridge to re-run Algorithm 4 line 1 for this algorithm: copy
	 * the originally loaded theta* back into theta_adapt and drop the replay
	 * buffer/in-flight state, so each simulation run (each new S_new in the
	 * device-count x scenario x policy sweep) adapts from the meta-trained
	 * parameter instead of inheriting the previous run's drift. Best-effort:
	 * a missing bridge just means the run continues with whatever the bridge
	 * ends up serving (or the static fallback), same as every other call.
	 */
	public synchronized void reset(String algo) {
		if (!ensureConnected()) {
			return;
		}
		try {
			out.write("RESET " + algo);
			out.newLine();
			out.flush();
			String response = in.readLine();
			if (response != null && response.trim().startsWith("ERROR")) {
				SimLogger.printLine("ReSACO bridge RESET " + algo + ": " + response.trim());
			}
		} catch (Exception e) {
			closeQuietly();
		}
	}

	/**
	 * Prints mean/percentile stats of the measured ACT round-trip times and
	 * clears them, so each scenario's print covers only its own decisions.
	 * No-op when no decision went through the bridge (e.g. pure fallback
	 * runs). This measured distribution is exactly what
	 * RESACO_DECISION_LATENCY_SECONDS should be set from to stop treating
	 * the policy as a zero-cost oracle.
	 */
	public synchronized void printAndResetLatencyStats() {
		if (actLatencyNanos.isEmpty()) {
			return;
		}
		long[] sorted = new long[actLatencyNanos.size()];
		for (int i = 0; i < sorted.length; i++) {
			sorted[i] = actLatencyNanos.get(i);
		}
		actLatencyNanos.clear();
		java.util.Arrays.sort(sorted);
		double sum = 0;
		for (long v : sorted) {
			sum += v;
		}
		SimLogger.printLine(String.format(
				"ReSACO bridge ACT round-trip (%d decisions): mean=%.2fms p50=%.2fms "
				+ "p95=%.2fms p99=%.2fms max=%.2fms -- consider setting "
				+ "DECISION_LATENCY_SECONDS (+ its Java mirror) from these",
				sorted.length, sum / sorted.length / 1e6, percentileMs(sorted, 50),
				percentileMs(sorted, 95), percentileMs(sorted, 99),
				sorted[sorted.length - 1] / 1e6));
	}

	// package-private so ReSACOBridgeClientTest can pin the nearest-rank math
	static double percentileMs(long[] sorted, int p) {
		int idx = (int) Math.ceil(sorted.length * (p / 100.0)) - 1;
		return sorted[Math.min(sorted.length - 1, Math.max(0, idx))] / 1e6;
	}

	private void closeQuietly() {
		try {
			if (socket != null) {
				socket.close();
			}
		} catch (IOException ignored) {
			// nothing to do
		}
		socket = null;
		lastFailureTimeMs = System.currentTimeMillis();
	}
}
