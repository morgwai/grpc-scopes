/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.samples.grpc.scopes.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import pl.morgwai.samples.grpc.scopes.grpc.RecordStorageGrpc.RecordStorageStub;



public class RecordStorageClient {



	public static void main(String args[]) throws InterruptedException {
		ManagedChannel channel = ManagedChannelBuilder.forTarget(
			"localhost:" + RecordStorageServer.PORT
		).usePlaintext().build();
		RecordStorageStub connector = RecordStorageGrpc.newStub(channel);

		System.out.println("store single");
		BlockingResponseObserver<NewRecordId> idResponseObserver =
				new BlockingResponseObserver<>() {

			@Override
			public void onNext(NewRecordId response) {
				System.out.println("id: " + response.getId());
			}
		};
		Record request = Record.newBuilder().setContent("first").build();
		connector.store(request, idResponseObserver);
		idResponseObserver.awaitCompletion();

		System.out.println("");
		System.out.println("store multiple");
		StreamObserver<Record> requestObserver = connector.storeMultiple(idResponseObserver);
		for (int i = 2; i <= 5; i++) {
			requestObserver.onNext(Record.newBuilder().setContent("record-" + i).build());
		}
		requestObserver.onCompleted();
		idResponseObserver.awaitCompletion();

		System.out.println("");
		System.out.println("get all");
		BlockingResponseObserver<Record> recordResponseObserver = new BlockingResponseObserver<>() {

			@Override
			public void onNext(Record record) {
				System.out.println("* " + record.getId() + " -> " + record.getContent());
			}
		};
		connector.getAll(Empty.newBuilder().build(), recordResponseObserver);
		recordResponseObserver.awaitCompletion();
	}
}



abstract class BlockingResponseObserver<T> implements StreamObserver<T> {



	boolean completed = false;



	public synchronized void awaitCompletion() throws InterruptedException {
		while ( ! completed) wait();
		completed = false;
	}



	@Override
	public void onError(Throwable t) {
		System.err.println("error: " + t);
		onCompleted();
	}



	@Override
	public synchronized void onCompleted() {
		completed = true;
		notifyAll();
	}
}
