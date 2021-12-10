// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes.tests;

import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.StreamObserver;

import pl.morgwai.base.grpc.scopes.tests.grpc.Empty;
import pl.morgwai.base.grpc.scopes.tests.grpc.ScopedObjectHashGrpc;
import pl.morgwai.base.grpc.scopes.tests.grpc.ScopedObjectHashGrpc.ScopedObjectHashBlockingStub;
import pl.morgwai.base.grpc.scopes.tests.grpc.ScopedObjectHashGrpc.ScopedObjectHashStub;
import pl.morgwai.base.grpc.scopes.tests.grpc.ScopedObjectsHashes;



public class ScopedObjectHashClient {



	final String target;
	final ManagedChannel channel;
	final ScopedObjectHashStub connector;
	final ScopedObjectHashBlockingStub blockingConnector;
	final long deadlineMillis;



	public ScopedObjectHashClient(String target, long deadlineMillis) {
		this.target = target;
		this.deadlineMillis = deadlineMillis;
		channel = ManagedChannelBuilder
				.forTarget(target)
				.usePlaintext()
				.build();
		connector = ScopedObjectHashGrpc.newStub(channel);
		blockingConnector = ScopedObjectHashGrpc.newBlockingStub(channel)
				.withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS);
	}



	public ScopedObjectsHashes unary() {
		return blockingConnector.unary(Empty.newBuilder().build());
	}



	public void streaming(
			int requestCount, StreamObserver<ScopedObjectsHashes> hashObserver, boolean cancel) {
		final var requestObserver = connector.streaming(hashObserver);
		for (int i = 0; i < requestCount; i++) requestObserver.onNext(Empty.newBuilder().build());
		if (cancel) {
			((ClientCallStreamObserver<Empty>) requestObserver).cancel("requested by caller", null);
		} else {
			requestObserver.onCompleted();
		}
	}

	public void streaming(int requestCount, StreamObserver<ScopedObjectsHashes> hashObserver) {
		streaming(requestCount, hashObserver, false);
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
