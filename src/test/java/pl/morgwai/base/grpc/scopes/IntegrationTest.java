// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.grpc.stub.StreamObserver;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import pl.morgwai.base.concurrent.Awaitable;
import pl.morgwai.base.grpc.scopes.tests.ScopedObjectHashClient;
import pl.morgwai.base.grpc.scopes.tests.ScopedObjectHashServer;
import pl.morgwai.base.grpc.scopes.tests.ScopedObjectHashService;
import pl.morgwai.base.grpc.scopes.tests.grpc.ScopedObjectsHashes;

import static org.junit.Assert.*;



public class IntegrationTest {



	ScopedObjectHashServer server;
	ScopedObjectHashService service;
	ScopedObjectHashClient client;

	HashObserver[] hashObservers;
	CountDownLatch[] callLatches;
	List<String> errors;



	@Before
	public void setup() throws Exception {
		server = new ScopedObjectHashServer(0);
		service = server.getService();
		errors = new ArrayList<>(20);
		service.setFinalizationListener((id, callErrors) -> {
			errors.addAll(callErrors);
			callLatches[id].countDown();
		});
		client = new ScopedObjectHashClient("localhost:" + server.getPort(), 500l);
	}



	@After
	public void shutdown() throws InterruptedException {
		client.shutdown();
		assertTrue(
			"server and client should shutdown cleanly",
			Awaitable.awaitMultiple(
				500l,
				(timeout) -> client.enforceTermination(timeout),
				(timeout) -> server.shutdownAndforceTermination(timeout)
			)
		);
	}



	String formatError() {
		StringBuilder combined = new StringBuilder();
		for (var error: errors) combined.append(error).append('\n');
		return combined.toString();
	}



	class HashObserver implements StreamObserver<ScopedObjectsHashes> {

		final int id;
		Throwable error = null;

		public HashObserver(int id) {
			this.id = id;
		}

		@Override public void onNext(ScopedObjectsHashes value) {
			logHashes(value);
		}

		@Override public void onError(Throwable t) {
			error = t;
			callLatches[id].countDown();
		}

		@Override public void onCompleted() {
			callLatches[id].countDown();
		}
	}



	void unary(int i) {
		client.unary(i, hashObservers[i]);
	}



	void streaming(int i) {
		client.streaming(i, 5, hashObservers[i]);
	}



	void streamingAndCancel(int i) {
		client.streamingAndCancel(i, 5, hashObservers[i]);
	}



	@Test
	public void testPositiveCase() throws Throwable {
		callLatches = new CountDownLatch[4];
		hashObservers = new HashObserver[4];
		for (int i = 0; i < 4; i++) {
			callLatches[i] = new CountDownLatch(2);
			hashObservers[i] = new HashObserver(i);
		}
		int callCount = 0;

		streaming(callCount);
		callLatches[callCount].await(500l, TimeUnit.MILLISECONDS);
		if ( ! errors.isEmpty()) fail(formatError());
		if (hashObservers[callCount].error != null) throw hashObservers[callCount].error;
		callCount++;

		unary(callCount);
		callLatches[callCount].await(500l, TimeUnit.MILLISECONDS);
		if ( ! errors.isEmpty()) fail(formatError());
		if (hashObservers[callCount].error != null) throw hashObservers[callCount].error;
		callCount++;

		streaming(callCount);
		callLatches[callCount].await(500l, TimeUnit.MILLISECONDS);
		if ( ! errors.isEmpty()) fail(formatError());
		if (hashObservers[callCount].error != null) throw hashObservers[callCount].error;
		callCount++;

		unary(callCount);
		callLatches[callCount].await(500l, TimeUnit.MILLISECONDS);
		if ( ! errors.isEmpty()) fail(formatError());
		if (hashObservers[callCount].error != null) throw hashObservers[callCount].error;
		callCount++;
	}



	@Test
	public void testCancel() throws Throwable {
		callLatches = new CountDownLatch[2];
		hashObservers = new HashObserver[2];
		for (int i = 0; i < 2; i++) {
			callLatches[i] = new CountDownLatch(2);
			hashObservers[i] = new HashObserver(i);
		}
		int callCount = 0;

		unary(callCount);
		callLatches[callCount].await(500l, TimeUnit.MILLISECONDS);
		if ( ! errors.isEmpty()) fail(formatError());
		if (hashObservers[callCount].error != null) throw hashObservers[callCount].error;
		callCount++;

		service.setCancelExpected(true);
		streamingAndCancel(callCount);
		callLatches[callCount].await(500l, TimeUnit.MILLISECONDS);
		if ( ! errors.isEmpty()) fail(formatError());
		final var error = hashObservers[callCount].error;
		if (error == null) fail("cancellation expected");
		if ( ! ScopedObjectHashService.isCancellation(error)) throw error;
		callCount++;
	}



	void logHashes(ScopedObjectsHashes hashes) {
		if (log.isLoggable(Level.FINER)) {
			log.finer(
					"rpc: " + hashes.getRpcScopedHash()
					+ ", event: " + hashes.getEventScopedHash());
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
