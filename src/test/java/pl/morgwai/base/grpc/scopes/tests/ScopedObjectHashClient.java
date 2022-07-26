// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes.tests;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.grpc.*;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.StreamObserver;

import pl.morgwai.base.grpc.scopes.GrpcModule;
import pl.morgwai.base.grpc.scopes.tests.grpc.Request;
import pl.morgwai.base.grpc.scopes.tests.grpc.ScopedObjectHashGrpc;
import pl.morgwai.base.grpc.scopes.tests.grpc.ScopedObjectHashGrpc.ScopedObjectHashStub;
import pl.morgwai.base.grpc.scopes.tests.grpc.ScopedObjectsHashes;



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
	) {
		final var messagesReceivedLatch = new CountDownLatch(requestCount + 2);
			// 1 additional for startCall and 1 for onReady
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
		if ( ! messagesReceived) throw new RuntimeException("deadline exceeded");
	}

	public void streaming(
			int callId, int requestCount, StreamObserver<ScopedObjectsHashes> responseObserver) {
		streaming(callId, requestCount, responseObserver, false);
	}

	public void streamingAndCancel(
			int callId, int requestCount, StreamObserver<ScopedObjectsHashes> responseObserver) {
		streaming(callId, requestCount, responseObserver, true);
	}



	public void shutdown() {
		channel.shutdown();
	}



	public boolean enforceTermination(long timeoutMillis) throws InterruptedException {
		if (channel.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
			return true;
		} else {
			channel.shutdownNow();
			return false;
		}
	}
}
