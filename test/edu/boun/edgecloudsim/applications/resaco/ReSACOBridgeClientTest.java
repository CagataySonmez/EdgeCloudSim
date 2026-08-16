/*
 * Title:        EdgeCloudSim - ReSACO Bridge Client Tests
 *
 * Description:
 * Exercises ReSACOBridgeClient's TCP protocol handling against a real
 * local ServerSocket standing in for the Python bridge -- no Python, no
 * trained checkpoints, no CloudSim.init() needed (CloudSim.clock() safely
 * returns 0.0 when uninitialized, verified separately). Uses the
 * package-private (host, port) constructor added specifically so tests can
 * build an isolated instance instead of fighting the getInstance()
 * singleton / global system properties.
 *
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 */

package edu.boun.edgecloudsim.applications.resaco;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReSACOBridgeClientTest {

	private ServerSocket serverSocket;
	private ExecutorService exec;

	@AfterEach
	void tearDown() throws IOException {
		if (serverSocket != null && !serverSocket.isClosed()) {
			serverSocket.close();
		}
		if (exec != null) {
			exec.shutdownNow();
		}
	}

	private ReSACOBridgeClient clientFor(ServerSocket server) {
		return new ReSACOBridgeClient("127.0.0.1", server.getLocalPort());
	}

	@Test
	void selectAction_bridgeUnreachable_returnsNoAction() throws IOException {
		// bind then immediately release a port so nothing is listening there
		ServerSocket temp = new ServerSocket(0);
		int deadPort = temp.getLocalPort();
		temp.close();

		ReSACOBridgeClient client = new ReSACOBridgeClient("127.0.0.1", deadPort);
		int action = client.selectAction("RESACO", "req-1", new double[] { 1.0, 2.0, 3.0 });

		assertEquals(ReSACOBridgeClient.NO_ACTION, action);
	}

	@Test
	void selectAction_validResponse_returnsParsedActionAndCorrectWireFormat() throws Exception {
		serverSocket = new ServerSocket(0);
		ReSACOBridgeClient client = clientFor(serverSocket);
		exec = Executors.newSingleThreadExecutor();

		Future<String> received = exec.submit(() -> {
			try (Socket s = serverSocket.accept();
					BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
					BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()))) {
				String line = in.readLine();
				out.write("5");
				out.newLine();
				out.flush();
				return line;
			}
		});

		int action = client.selectAction("RESACO", "req-1", new double[] { 1.0, 2.5, 3.0 });
		String requestLine = received.get(5, TimeUnit.SECONDS);

		assertEquals(5, action);
		assertTrue(requestLine.startsWith("ACT RESACO req-1 "), "unexpected request line: " + requestLine);
		assertTrue(requestLine.contains("1.0"));
		assertTrue(requestLine.contains("2.5"));
		assertTrue(requestLine.contains("3.0"));
	}

	@Test
	void selectAction_errorResponse_returnsNoAction() throws Exception {
		serverSocket = new ServerSocket(0);
		ReSACOBridgeClient client = clientFor(serverSocket);
		exec = Executors.newSingleThreadExecutor();

		exec.submit(() -> {
			try (Socket s = serverSocket.accept();
					BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
					BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()))) {
				in.readLine();
				out.write("ERROR unknown algo BOGUS");
				out.newLine();
				out.flush();
			} catch (IOException ignored) {
				// nothing to do
			}
		});

		int action = client.selectAction("BOGUS", "req-1", new double[] { 1.0 });

		assertEquals(ReSACOBridgeClient.NO_ACTION, action);
	}

	@Test
	void reportOutcome_sendsCorrectProtocolFormat() throws Exception {
		serverSocket = new ServerSocket(0);
		ReSACOBridgeClient client = clientFor(serverSocket);
		exec = Executors.newSingleThreadExecutor();

		Future<String> received = exec.submit(() -> {
			try (Socket s = serverSocket.accept();
					BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
					BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()))) {
				String line = in.readLine();
				out.write("OK");
				out.newLine();
				out.flush();
				return line;
			}
		});

		client.reportOutcome("RESACO", "req-1", -1.25, true, new double[] { 0.1, 0.2 });
		String line = received.get(5, TimeUnit.SECONDS);

		assertEquals("OUTCOME RESACO req-1 -1.25 1 0.1 0.2", line);
	}

	@Test
	void elapsedSince_unknownRequestId_returnsMinusOne() {
		ReSACOBridgeClient client = new ReSACOBridgeClient("127.0.0.1", 1);
		assertEquals(-1.0, client.elapsedSince("never-seen"), 1e-9);
	}

	@Test
	void elapsedSince_afterSelectAction_isConsumedOnlyOnce() throws Exception {
		serverSocket = new ServerSocket(0);
		ReSACOBridgeClient client = clientFor(serverSocket);
		exec = Executors.newSingleThreadExecutor();

		exec.submit(() -> {
			try (Socket s = serverSocket.accept();
					BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
					BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()))) {
				in.readLine();
				out.write("0");
				out.newLine();
				out.flush();
			} catch (IOException ignored) {
				// nothing to do
			}
		});
		client.selectAction("RESACO", "req-1", new double[] { 1.0 });

		// CloudSim.clock() is 0.0 with no CloudSim.init() in this test, so the
		// exact value isn't meaningful here -- what matters is that the first
		// call finds the recorded submission and the second one (the entry
		// having been consumed already) reports "unknown" the same way an
		// entirely-never-seen request id does.
		double first = client.elapsedSince("req-1");
		double second = client.elapsedSince("req-1");

		assertTrue(first >= 0.0, "expected a known elapsed time on first call, got " + first);
		assertEquals(-1.0, second, 1e-9);
	}

	@Test
	void latencyStats_countOnlyRealDecisions_andClearOnPrint() throws Exception {
		serverSocket = new ServerSocket(0);
		ReSACOBridgeClient client = clientFor(serverSocket);
		exec = Executors.newSingleThreadExecutor();

		// one connection serving four ACTs: three real decisions + one ERROR
		exec.submit(() -> {
			try (Socket s = serverSocket.accept();
					BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
					BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()))) {
				String[] responses = { "3", "0", "ERROR unknown algo BOGUS", "7" };
				for (String response : responses) {
					in.readLine();
					out.write(response);
					out.newLine();
					out.flush();
				}
			} catch (IOException ignored) {
				// nothing to do
			}
		});

		client.selectAction("RESACO", "req-1", new double[] { 1.0 });
		client.selectAction("RESACO", "req-2", new double[] { 1.0 });
		client.selectAction("BOGUS", "req-3", new double[] { 1.0 });
		client.selectAction("RESACO", "req-4", new double[] { 1.0 });

		java.io.PrintStream originalOut = System.out;
		java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
		edu.boun.edgecloudsim.utils.SimLogger.enablePrintLog();
		System.setOut(new java.io.PrintStream(captured, true));
		try {
			client.printAndResetLatencyStats();
			String firstPrint = captured.toString();
			// ERROR replies carry no policy inference, so only the three real
			// decisions may be counted -- a fast error round-trip in the stats
			// would bias the DECISION_LATENCY_SECONDS calibration low.
			assertTrue(firstPrint.contains("(3 decisions)"),
					"expected 3 counted decisions, got: " + firstPrint);

			captured.reset();
			client.printAndResetLatencyStats(); // stats were cleared -> silent no-op
			assertEquals("", captured.toString(),
					"second print must be empty: the first one clears the samples");
		} finally {
			System.setOut(originalOut);
		}
	}

	@Test
	void percentileMs_nearestRankMath() {
		long[] one = { 10_000_000L }; // 10 ms
		assertEquals(10.0, ReSACOBridgeClient.percentileMs(one, 50), 1e-9);
		assertEquals(10.0, ReSACOBridgeClient.percentileMs(one, 99), 1e-9);

		long[] four = { 1_000_000L, 2_000_000L, 3_000_000L, 4_000_000L }; // 1..4 ms
		assertEquals(2.0, ReSACOBridgeClient.percentileMs(four, 50), 1e-9); // ceil(4*.50)=2nd
		assertEquals(4.0, ReSACOBridgeClient.percentileMs(four, 95), 1e-9); // ceil(4*.95)=4th
		assertEquals(4.0, ReSACOBridgeClient.percentileMs(four, 99), 1e-9);

		long[] hundred = new long[100]; // 1..100 ms
		for (int i = 0; i < 100; i++) {
			hundred[i] = (i + 1) * 1_000_000L;
		}
		assertEquals(50.0, ReSACOBridgeClient.percentileMs(hundred, 50), 1e-9);
		assertEquals(95.0, ReSACOBridgeClient.percentileMs(hundred, 95), 1e-9);
		assertEquals(99.0, ReSACOBridgeClient.percentileMs(hundred, 99), 1e-9);
	}

	@Test
	@Timeout(15)
	void selectAction_hungBridge_timesOutInsteadOfBlockingForever() throws Exception {
		serverSocket = new ServerSocket(0);
		ReSACOBridgeClient client = clientFor(serverSocket);
		exec = Executors.newSingleThreadExecutor();

		exec.submit(() -> {
			try {
				serverSocket.accept(); // accept, then never respond -- exercises the read timeout
			} catch (IOException ignored) {
				// nothing to do
			}
		});

		long start = System.currentTimeMillis();
		int action = client.selectAction("RESACO", "req-1", new double[] { 1.0 });
		long elapsedMs = System.currentTimeMillis() - start;

		assertEquals(ReSACOBridgeClient.NO_ACTION, action);
		assertTrue(elapsedMs >= 9000,
				"expected the ~10s read timeout to have fired before returning, took " + elapsedMs + "ms");
	}
}
