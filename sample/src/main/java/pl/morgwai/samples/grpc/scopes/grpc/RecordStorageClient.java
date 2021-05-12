// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.grpc.scopes.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import pl.morgwai.base.grpc.utils.BlockingResponseObserver;
import pl.morgwai.samples.grpc.scopes.grpc.RecordStorageGrpc.RecordStorageStub;



public class RecordStorageClient {



	public static void main(String args[]) throws Exception {
		ManagedChannel channel = ManagedChannelBuilder
				.forTarget("localhost:" + RecordStorageServer.PORT)
				.defaultLoadBalancingPolicy("grpclb")
				.usePlaintext()
				.build();
		RecordStorageStub connector = RecordStorageGrpc.newStub(channel);



		System.out.println("store single");
		var idResponseObserver = new BlockingResponseObserver<NewRecordId>(
			response -> System.out.println("id: " + response.getId())
		);
		Record request = Record.newBuilder().setContent("first").build();
		connector.store(request, idResponseObserver);
		idResponseObserver.awaitCompletion();



		System.out.println();
		System.out.println("store multiple");
		var storeRecordResponseObserver = new BlockingResponseObserver<StoreRecordResponse>(
			response -> System.out.println(response.getRequestId()+ " -> " + response.getRecordId())
		);
		StreamObserver<StoreRecordRequest> requestObserver =
				connector.storeMultiple(storeRecordResponseObserver);
		for (int i = 2; i <= 5; i++) {
			requestObserver.onNext(
					StoreRecordRequest.newBuilder()
							.setRequestId(i)
							.setContent("record-" + i)
							.build());
		}
		requestObserver.onCompleted();
		storeRecordResponseObserver.awaitCompletion();



		System.out.println();
		System.out.println("get all");
		var recordResponseObserver = new BlockingResponseObserver<Record>(
			record -> System.out.println("* " + record.getId() + " -> " + record.getContent())
		);
		connector.getAll(Empty.newBuilder().build(), recordResponseObserver);
		recordResponseObserver.awaitCompletion();
	}
}
