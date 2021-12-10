// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import pl.morgwai.base.concurrent.Awaitable;
import pl.morgwai.base.grpc.scopes.tests.ScopedObjectHashClient;
import pl.morgwai.base.grpc.scopes.tests.ScopedObjectHashServer;
import pl.morgwai.base.grpc.scopes.tests.grpc.Empty;
import pl.morgwai.base.grpc.scopes.tests.grpc.ScopedObjectsHashes;
import pl.morgwai.base.grpc.utils.BlockingResponseObserver;

import static org.junit.Assert.*;



public class IntegrationTest {



	ScopedObjectHashServer server;
	ScopedObjectHashClient client;

	volatile String error;



	@Before
	public void setup() throws Exception {
		error = null;
		server = new ScopedObjectHashServer(0, (errorMessage) -> { error = errorMessage; });
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



	@Test
	public void test() throws Exception {
		final var hashObserver =
				new BlockingResponseObserver<Empty, ScopedObjectsHashes>(this::logHashes);
		client.streaming(5, hashObserver);
		hashObserver.awaitCompletion(500l);
		if (error != null) fail(error);
		logHashes(client.unary());
		if (error != null) fail(error);
		final var hashObserver2 =
				new BlockingResponseObserver<Empty, ScopedObjectsHashes>(this::logHashes);
		client.streaming(5, hashObserver2);
		hashObserver2.awaitCompletion(500l);
		if (error != null) fail(error);
		logHashes(client.unary());
		if (error != null) fail(error);
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
