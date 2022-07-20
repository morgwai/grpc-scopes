// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.grpc.stub.StreamObserver;
import org.junit.*;

import pl.morgwai.base.concurrent.Awaitable;
import pl.morgwai.base.grpc.scopes.tests.*;
import pl.morgwai.base.grpc.scopes.tests.grpc.ScopedObjectsHashes;

import static org.junit.Assert.*;



public class IntegrationTest {



	public static final long TIMEOUT_MILLIS = 500l;

	ScopedObjectHashServer server;
	ScopedObjectHashService service;
	ScopedObjectHashClient client;

	/**
	 * Whether given call is finalized both from client's and server's perspective.
	 * Decreased by the server in
	 * {@link ScopedObjectHashService#setFinalizationListener(BiConsumer)} callback set in
	 * {@link #setup()}. Decreased by the client in {@link ResponseObserver#onCompleted()} /
	 * {@link ResponseObserver#onError(Throwable)}.
	 */
	CountDownLatch[] callBiFinalized;

	/**
	 * Errors reported by the service via
	 * {@link ScopedObjectHashService#setFinalizationListener(BiConsumer)} callback.
	 */
	List<String> serverErrors;



	@Before
	public void setup() throws Exception {
		server = new ScopedObjectHashServer(0);
		service = server.getService();
		serverErrors = new ArrayList<>(20);
		service.setFinalizationListener((callId, callErrors) -> {
			synchronized (serverErrors) {
				serverErrors.addAll(callErrors);
			}
			callBiFinalized[callId].countDown();
		});
		client = new ScopedObjectHashClient("localhost:" + server.getPort(), TIMEOUT_MILLIS);
	}



	@After
	public void shutdown() throws InterruptedException {
		client.shutdown();
		assertTrue(
			"server and client should shutdown cleanly",
			Awaitable.awaitMultiple(
				TIMEOUT_MILLIS,
				(timeout) -> client.enforceTermination(timeout),
				(timeout) -> server.shutdownAndEnforceTermination(timeout)
			)
		);
	}



	static class ResponseObserver implements StreamObserver<ScopedObjectsHashes> {

		final int callId;
		final CountDownLatch finalizationLatch;
		Throwable error = null;

		public ResponseObserver(int callId, CountDownLatch finalizationLatch) {
			this.callId = callId;
			this.finalizationLatch = finalizationLatch;
		}

		@Override public void onNext(ScopedObjectsHashes response) {
			logHashes(callId, response);
		}

		@Override public void onError(Throwable t) {
			error = t;
			finalizationLatch.countDown();
		}

		@Override public void onCompleted() {
			finalizationLatch.countDown();
		}
	}



	/**
	 * Four calls: streaming, unary, streaming, unary. 3 request messages per streaming call.
	 */
	@Test
	public void testPositiveCase() throws Throwable {
		final int numberOfCalls = 4;
		callBiFinalized = new CountDownLatch[numberOfCalls];
		for (int callId = 0; callId < numberOfCalls; callId++) {
			callBiFinalized[callId] = new CountDownLatch(2);
			var responseObserver = new ResponseObserver(callId, callBiFinalized[callId]);
			if (callId % 2 == 0) {
				client.streaming(callId, 3, responseObserver);
			} else {
				client.unary(callId, responseObserver);
			}
			if ( ! callBiFinalized[callId].await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
				throw new TimeoutException();
			}
			if ( ! serverErrors.isEmpty()) fail(formatError(serverErrors));
			if (responseObserver.error != null) throw responseObserver.error;
		}
	}


	/**
	 * First issues a "warmup" unary call to populate scoped object logs, then issues a streaming
	 * call that is cancelled after 3 request messages.
	 */
	@Test
	public void testCancel() throws Throwable {
		final int warmupId = 0;
		final int cancelledId = 1;
		callBiFinalized = new CountDownLatch[2];

		// warmup call
		callBiFinalized[warmupId] = new CountDownLatch(2);
		var warmupResponseObserver = new ResponseObserver(warmupId, callBiFinalized[warmupId]);
		client.unary(warmupId, warmupResponseObserver);
		if ( ! callBiFinalized[warmupId].await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
			throw new TimeoutException();
		}
		if ( ! serverErrors.isEmpty()) fail(formatError(serverErrors));
		if (warmupResponseObserver.error != null) throw warmupResponseObserver.error;

		// cancelled call
		callBiFinalized[cancelledId] = new CountDownLatch(2);
		var cancelResponseObserver =
				new ResponseObserver(cancelledId, callBiFinalized[cancelledId]);
		service.setCancelExpected(true);
		client.streamingAndCancel(cancelledId, 3, cancelResponseObserver);
		if ( ! callBiFinalized[cancelledId].await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
			throw new TimeoutException();
		}
		if ( ! serverErrors.isEmpty()) fail(formatError(serverErrors));
		final var error = cancelResponseObserver.error;
		if (error == null) fail("cancellation expected");
		if ( ! ScopedObjectHashService.isCancellation(error)) throw error;
	}



	static String formatError(List<String> errors) {
		StringBuilder combined = new StringBuilder();
		for (var error: errors) combined.append(error).append('\n');
		return combined.toString();
	}



	static void logHashes(int callId, ScopedObjectsHashes hashes) {
		if (log.isLoggable(Level.FINER)) {
			log.finer(
					"call: " + callId
					+ ", event: " + hashes.getEventName()
					+ ", rpc-scoped hash: " + hashes.getRpcScopedHash()
					+ ", event-scoped hash: " + hashes.getEventScopedHash());
		}
	}



	static Level LOG_LEVEL = Level.WARNING;

	static final Logger log = Logger.getLogger(IntegrationTest.class.getName());

	@BeforeClass
	public static void setupLogging() {
		try {
			LOG_LEVEL = Level.parse(System.getProperty(
					IntegrationTest.class.getPackageName() + ".level"));
		} catch (Exception ignored) {}
		log.setLevel(LOG_LEVEL);
		for (final var handler: Logger.getLogger("").getHandlers()) handler.setLevel(LOG_LEVEL);
	}
}
