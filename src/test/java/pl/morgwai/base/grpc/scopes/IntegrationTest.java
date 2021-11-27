// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import pl.morgwai.base.concurrent.Awaitable;
import pl.morgwai.base.grpc.scopes.tests.server.grpc.RecordStorageClient;
import pl.morgwai.base.grpc.scopes.tests.server.grpc.RecordStorageServer;

import static org.junit.Assert.assertTrue;
import static pl.morgwai.base.grpc.scopes.tests.server.grpc.RecordStorageServer.*;



public class IntegrationTest {



	RecordStorageServer server;
	RecordStorageClient client;



	@Before
	public void setup() throws Exception {
		server = new RecordStorageServer(
				0,
				DEFAULT_MAX_CONNECTION_IDLE,
				DEFAULT_MAX_CONNECTION_AGE,
				DEFAULT_MAX_CONNECTION_AGE_GRACE);
		client = new RecordStorageClient("localhost:" + server.getPort(), 1000l);
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
		client.doStuff();
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
