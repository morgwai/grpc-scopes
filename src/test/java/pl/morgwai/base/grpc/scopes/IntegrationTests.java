// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.grpc.stub.StreamObserver;
import org.junit.*;
import pl.morgwai.base.grpc.scopes.tests.*;
import pl.morgwai.base.grpc.scopes.tests.grpc.ScopedObjectsHashes;
import pl.morgwai.base.utils.concurrent.Awaitable;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;



public class IntegrationTests {

	// TODO: test multi-layer nesting



	public static final long TIMEOUT_MILLIS = 1000L;

	ScopedObjectHashServer server;
	ScopedObjectHashService service;
	ScopedObjectHashClient client;
	GrpcModule clientGrpcModule;
	BackendServer backendServer;

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
		backendServer = new BackendServer(0);
		server = new ScopedObjectHashServer(0, "localhost:" + backendServer.getPort());
		service = server.getService();
		serverErrors = new ArrayList<>(20);
		service.setFinalizationListener((callId, callErrors) -> {
			synchronized (serverErrors) {
				serverErrors.addAll(callErrors);
			}
			callBiFinalized[callId].countDown();
		});
		clientGrpcModule = new GrpcModule();
		client = new ScopedObjectHashClient(
				"localhost:" + server.getPort(), TIMEOUT_MILLIS, clientGrpcModule);
	}



	@After
	public void shutdown() throws InterruptedException {
		assertTrue(
			"servers and client should shutdown cleanly",
			Awaitable.awaitMultiple(
				TIMEOUT_MILLIS,
				client.toAwaitableOfEnforcedTermination(),
				server::shutdownAndEnforceTermination,
				backendServer.toAwaitableOfEnforcedTermination()
			)
		);
	}



	static class ResponseObserver implements StreamObserver<ScopedObjectsHashes> {

		final int callId;
		final CountDownLatch finalizationLatch;
		final GrpcModule grpcModule;
		final List<Throwable> clientScopingErrors = new ArrayList<>(10);
		Throwable error = null;
		ContextVerifier ctxVerifier;



		public ResponseObserver(int callId, CountDownLatch finalizationLatch, GrpcModule grpcModule)
		{
			this.callId = callId;
			this.finalizationLatch = finalizationLatch;
			this.grpcModule = grpcModule;
		}



		@Override public void onNext(ScopedObjectsHashes response) {
			logHashes(callId, response);
			if (ctxVerifier == null) {
				final var eventCtx = grpcModule.listenerEventContextTracker.getCurrentContext();
				ctxVerifier = new ContextVerifier(grpcModule, eventCtx.getRpcContext());
				ctxVerifier.add(eventCtx);
			} else {
				try {
					ctxVerifier.verifyCtxs();
				} catch (Throwable scopingError) {
					clientScopingErrors.add(scopingError);
				}
			}
		}



		@Override public void onError(Throwable t) {
			error = t;
			try {
				ctxVerifier.verifyCtxs();
			} catch (Throwable scopingError) {
				clientScopingErrors.add(scopingError);
			}
			finalizationLatch.countDown();
		}



		@Override public void onCompleted() {
			try {
				ctxVerifier.verifyCtxs();
			} catch (Throwable scopingError) {
				clientScopingErrors.add(scopingError);
			}
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
			var responseObserver = new ResponseObserver(
					callId, callBiFinalized[callId], clientGrpcModule);
			if (callId % 2 == 0) {
				client.streaming(callId, 3, responseObserver);
			} else {
				client.unary(callId, responseObserver);
			}
			if ( !callBiFinalized[callId].await(TIMEOUT_MILLIS, MILLISECONDS)) {
				throw new TimeoutException();
			}
			if ( !serverErrors.isEmpty()) fail(formatError(serverErrors));
			if (responseObserver.error != null) throw responseObserver.error;
			if ( !responseObserver.clientScopingErrors.isEmpty()) {
				throw responseObserver.clientScopingErrors.get(0);
			}
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
		var warmupResponseObserver = new ResponseObserver(
				warmupId, callBiFinalized[warmupId], clientGrpcModule);
		client.unary(warmupId, warmupResponseObserver);
		if ( !callBiFinalized[warmupId].await(TIMEOUT_MILLIS, MILLISECONDS)) {
			throw new TimeoutException();
		}
		if ( !serverErrors.isEmpty()) fail(formatError(serverErrors));
		if (warmupResponseObserver.error != null) throw warmupResponseObserver.error;
		if ( !warmupResponseObserver.clientScopingErrors.isEmpty()) {
			throw warmupResponseObserver.clientScopingErrors.get(0);
		}

		// cancelled call
		callBiFinalized[cancelledId] = new CountDownLatch(2);
		var cancelResponseObserver =
				new ResponseObserver(cancelledId, callBiFinalized[cancelledId], clientGrpcModule);
		service.setCancelExpected(true);
		client.streamingAndCancel(cancelledId, 3, cancelResponseObserver);
		if ( !callBiFinalized[cancelledId].await(TIMEOUT_MILLIS, MILLISECONDS)) {
			throw new TimeoutException();
		}
		if ( !serverErrors.isEmpty()) fail(formatError(serverErrors));
		final var error = cancelResponseObserver.error;
		if (error == null) fail("cancellation expected");
		if ( !ScopedObjectHashService.isCancellation(error)) throw error;
		if ( !cancelResponseObserver.clientScopingErrors.isEmpty()) {
			throw cancelResponseObserver.clientScopingErrors.get(0);
		}
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

	static final Logger log = Logger.getLogger(IntegrationTests.class.getName());

	@BeforeClass
	public static void setupLogging() {
		try {
			LOG_LEVEL = Level.parse(System.getProperty(
					IntegrationTests.class.getPackageName() + ".level"));
		} catch (Exception ignored) {}
		log.setLevel(LOG_LEVEL);
		ScopedObjectHashService.setLogLevel(LOG_LEVEL);
		for (final var handler: Logger.getLogger("").getHandlers()) handler.setLevel(LOG_LEVEL);
	}
}
