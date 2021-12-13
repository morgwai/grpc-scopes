// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes.tests;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.StreamObserver;

import pl.morgwai.base.grpc.scopes.tests.grpc.Request;
import pl.morgwai.base.grpc.scopes.tests.grpc.ScopedObjectHashGrpc;
import pl.morgwai.base.grpc.scopes.tests.grpc.ScopedObjectHashGrpc.ScopedObjectHashStub;
import pl.morgwai.base.grpc.scopes.tests.grpc.ScopedObjectsHashes;



public class ScopedObjectHashClient {



	final ManagedChannel channel;
	final ScopedObjectHashStub connector;



	public ScopedObjectHashClient(String target, long deadlineMillis) {
		channel = ManagedChannelBuilder
				.forTarget(target)
				.usePlaintext()
				.build();
		connector = ScopedObjectHashGrpc.newStub(channel);
	}



	public void unary(int id, StreamObserver<ScopedObjectsHashes> hashObserver) {
		connector.unary(Request.newBuilder().setId(id).build(), hashObserver);
	}



	void streaming(
		int id, int requestCount, StreamObserver<ScopedObjectsHashes> hashObserver, boolean cancel
	) {
		final var messagesReceived = new CountDownLatch(requestCount);
		final var decoratedHashObserver = new StreamObserver<ScopedObjectsHashes>() {

			@Override public void onNext(ScopedObjectsHashes value) {
				messagesReceived.countDown();
				hashObserver.onNext(value);
			}

			@Override public void onError(Throwable t) { hashObserver.onError(t); }

			@Override public void onCompleted() { hashObserver.onCompleted(); }
		};
		final var requestObserver = connector.streaming(decoratedHashObserver);
		for (int i = 0; i < requestCount; i++) {
			requestObserver.onNext(Request.newBuilder().setId(id).build());
		}
		if (cancel) {
			try {
				messagesReceived.await(500l, TimeUnit.MILLISECONDS);
			} catch (InterruptedException ignored) {}
			((ClientCallStreamObserver<?>) requestObserver).cancel("requested by caller", null);
		} else {
			requestObserver.onCompleted();
		}
	}

	public void streaming(
			int id, int requestCount, StreamObserver<ScopedObjectsHashes> hashObserver) {
		streaming(id, requestCount, hashObserver, false);
	}

	public void streamingAndCancel(
			int id, int requestCount, StreamObserver<ScopedObjectsHashes> hashObserver) {
		streaming(id, requestCount, hashObserver, true);
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
