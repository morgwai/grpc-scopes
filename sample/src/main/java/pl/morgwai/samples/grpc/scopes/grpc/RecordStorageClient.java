// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.grpc.scopes.grpc;

import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannelBuilder;

import pl.morgwai.base.grpc.utils.BlockingResponseObserver;
import pl.morgwai.base.grpc.utils.BlockingResponseObserver.ErrorReportedException;



public class RecordStorageClient {



	public static void main(String[] args) throws ErrorReportedException, InterruptedException {
		String target = "localhost:" + RecordStorageServer.DEFAULT_PORT;
		if (args.length > 0) target = args[0];
		final var channel = ManagedChannelBuilder
				.forTarget(target)
				.usePlaintext()
				.build();
		final var connector = RecordStorageGrpc.newStub(channel);
		final var blockingConnector = RecordStorageGrpc.newBlockingStub(channel)
				.withDeadlineAfter(10, TimeUnit.SECONDS);



		System.out.println("store single");
		final NewRecordId recordIdResponse = blockingConnector.store(
				Record.newBuilder().setContent("first").build());
		System.out.println("id: " + recordIdResponse.getId());



		System.out.println();
		System.out.println("store multiple");
		var storeRecordResponseObserver = new BlockingResponseObserver<StoreRecordResponse>(
			response -> System.out.println(response.getRequestId()+ " -> " + response.getRecordId())
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



		System.out.println();
		System.out.println("get all");
		final var recordIterator = blockingConnector.getAll(Empty.newBuilder().build());
		while (recordIterator.hasNext()) {
			final Record record = recordIterator.next();
			System.out.println("* " + record.getId() + " -> " + record.getContent());
		}



		channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		if ( ! channel.isTerminated()) System.out.println("channel has NOT shutdown cleanly");
	}
}
