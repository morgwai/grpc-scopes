// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes.tests.server.grpc;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import pl.morgwai.base.grpc.scopes.tests.server.grpc.RecordStorageGrpc.RecordStorageBlockingStub;
import pl.morgwai.base.grpc.scopes.tests.server.grpc.RecordStorageGrpc.RecordStorageStub;
import pl.morgwai.base.grpc.utils.BlockingResponseObserver;



public class RecordStorageClient {



	final String target;
	final ManagedChannel channel;
	final RecordStorageStub connector;
	final RecordStorageBlockingStub blockingConnector;
	final long deadlineMillis;



	public RecordStorageClient(String target, long deadlineMillis) {
		this.target = target;
		this.deadlineMillis = deadlineMillis;
		channel = ManagedChannelBuilder
				.forTarget(target)
				.usePlaintext()
				.build();
		connector = RecordStorageGrpc.newStub(channel);
		blockingConnector = RecordStorageGrpc.newBlockingStub(channel)
				.withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS);
	}



	public void shutdown() {
		channel.shutdown();
	}



	public boolean enforceTermination(long timeoutMillis) throws InterruptedException {
		if (channel.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
			log.info("gRPC client shutdown completed");
			return true;
		} else {
			log.warning("gRPC client has NOT shutdown cleanly");
			channel.shutdownNow();
			return false;
		}
	}



	public boolean awaitTermination(long timeoutMillis) throws InterruptedException {
		return channel.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS);
	}



	public void doStuff() throws Exception {
		log.fine("store single");
		final NewRecordId recordIdResponse = blockingConnector.store(
				Record.newBuilder().setContent("first").build());
		if (log.isLoggable(Level.FINER)) log.finer("id: " + recordIdResponse.getId());



		log.fine("store multiple");
		final var storeRecordResponseObserver = new BlockingResponseObserver<StoreRecordResponse>(
			response -> {
				if (log.isLoggable(Level.FINER)) {
					log.finer(response.getRequestId() + " -> " + response.getRecordId());
				}
			}
		);
		final var requestObserver = connector.storeMultiple(storeRecordResponseObserver);
		for (int i = 2; i <= 5; i++) {
			requestObserver.onNext(
					StoreRecordRequest.newBuilder()
							.setRequestId(i)
							.setContent("record-" + i)
							.build());
		}
		requestObserver.onCompleted();
		storeRecordResponseObserver.awaitCompletion(5000l);



		log.fine("get all");
		final var recordIterator = blockingConnector.getAll(Empty.newBuilder().build());
		while (recordIterator.hasNext()) {
			final Record record = recordIterator.next();
			log.finer("* " + record.getId() + " -> " + record.getContent());
		}
	}



	static final Logger log = Logger.getLogger(RecordStorageClient.class.getName());
}
