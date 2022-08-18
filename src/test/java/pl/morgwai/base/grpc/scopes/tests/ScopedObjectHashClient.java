// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes.tests;

import java.util.concurrent.*;

import io.grpc.*;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.StreamObserver;

import pl.morgwai.base.concurrent.Awaitable;
import pl.morgwai.base.grpc.scopes.GrpcModule;
import pl.morgwai.base.grpc.scopes.tests.grpc.Request;
import pl.morgwai.base.grpc.scopes.tests.grpc.ScopedObjectHashGrpc;
import pl.morgwai.base.grpc.scopes.tests.grpc.ScopedObjectHashGrpc.ScopedObjectHashStub;
import pl.morgwai.base.grpc.scopes.tests.grpc.ScopedObjectsHashes;
import pl.morgwai.base.grpc.utils.GrpcAwaitable;



public class ScopedObjectHashClient {



	final ManagedChannel channel;
	final ScopedObjectHashStub connector;
	final long deadlineMillis;



	public ScopedObjectHashClient(String target, long deadlineMillis, GrpcModule grpcModule) {
		this.deadlineMillis = deadlineMillis;
		channel = ManagedChannelBuilder
			.forTarget(target)
			.usePlaintext()
			.build();
		connector = ScopedObjectHashGrpc.newStub(
				ClientInterceptors.intercept(channel, grpcModule.clientInterceptor));
	}



	public void unary(int callId, StreamObserver<ScopedObjectsHashes> hashObserver) {
		connector.unary(Request.newBuilder().setCallId(callId).build(), hashObserver);
	}



	void streaming(
		int callId,
		int requestCount,
		StreamObserver<ScopedObjectsHashes> responseObserver,
		boolean cancel
	) throws TimeoutException {
		final var messagesReceivedLatch = new CountDownLatch(requestCount + 4);
			// +4: startCall, onReady, backend.onNext, backend.onCompleted
		final var decoratedResponseObserver = new StreamObserver<ScopedObjectsHashes>() {

			@Override public void onNext(ScopedObjectsHashes value) {
				messagesReceivedLatch.countDown();
				responseObserver.onNext(value);
			}

			@Override public void onError(Throwable t) { responseObserver.onError(t); }

			@Override public void onCompleted() { responseObserver.onCompleted(); }
		};
		final var requestObserver = connector.streaming(decoratedResponseObserver);
		for (int i = 0; i < requestCount; i++) {
			requestObserver.onNext(Request.newBuilder().setCallId(callId).build());
		}
		boolean messagesReceived = false;
		try {
			messagesReceived = messagesReceivedLatch.await(deadlineMillis, TimeUnit.MILLISECONDS);
		} catch (InterruptedException ignored) {}
		if (cancel) {
			((ClientCallStreamObserver<?>) requestObserver).cancel("requested by caller", null);
		} else {
			requestObserver.onCompleted();
		}
		if ( ! messagesReceived) throw new TimeoutException();
	}

	public void streaming(
			int callId, int requestCount, StreamObserver<ScopedObjectsHashes> responseObserver)
			throws TimeoutException {
		streaming(callId, requestCount, responseObserver, false);
	}

	public void streamingAndCancel(
			int callId, int requestCount, StreamObserver<ScopedObjectsHashes> responseObserver)
			throws TimeoutException {
		streaming(callId, requestCount, responseObserver, true);
	}



	public Awaitable.WithUnit toAwaitableOfEnforcedTermination() {
		return GrpcAwaitable.ofEnforcedTermination(channel);
	}



	public static void main(String[] args) throws InterruptedException {
		ScopedObjectHashClient client = null;
		try {
			int numberOfCalls = 2;
			if (args.length > 1) numberOfCalls = Integer.parseInt(args[1]);
			int numberOfMessages = 5;
			if (args.length > 2) numberOfMessages = Integer.parseInt(args[2]);
			long timeoutMillis = 500L;
			if (args.length > 3) timeoutMillis = Long.parseLong(args[3]);
			client = new ScopedObjectHashClient(
					"localhost:" + Integer.parseInt(args[0]), timeoutMillis, new GrpcModule());
			for (int callNumber = 0; callNumber < numberOfCalls; callNumber++) {
				final int callId = callNumber;
				final var latch = new CountDownLatch(1);
				try {
					client.streaming(callNumber, numberOfMessages, new StreamObserver<>() {

						@Override public void onNext(ScopedObjectsHashes hashes) {
							System.out.println(
									"call: " + callId
									+ ", event: " + hashes.getEventName()
									+ ", rpc-scoped hash: " + hashes.getRpcScopedHash()
									+ ", event-scoped hash: " + hashes.getEventScopedHash());
						}

						@Override public void onError(Throwable t) { t.printStackTrace(); }

						@Override public void onCompleted() {
							System.out.println("completed " + callId + '\n');
							latch.countDown();
						}
					});
				} catch (TimeoutException e) {
					System.err.println("timeout waiting for messages, will wait 100ms more");
				}
				if ( ! latch.await(100L, TimeUnit.MILLISECONDS)) throw new TimeoutException();
			}
		} catch (Throwable t) {
			t.printStackTrace();
		} finally {
			if (client != null) {
				client.toAwaitableOfEnforcedTermination().await(200L);
			}
		}
	}
}
