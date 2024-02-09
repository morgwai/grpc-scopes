// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.grpc.scopes.grpc;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.inject.*;
import com.google.inject.Module;
import com.google.inject.name.Names;
import io.grpc.*;

import pl.morgwai.base.grpc.scopes.GrpcModule;
import pl.morgwai.base.grpc.utils.BlockingResponseObserver;
import pl.morgwai.base.utils.concurrent.Awaitable;
import pl.morgwai.samples.grpc.scopes.grpc.RecordStorageGrpc.RecordStorageBlockingStub;
import pl.morgwai.samples.grpc.scopes.grpc.RecordStorageGrpc.RecordStorageStub;



public class RecordStorageClient {



	/**
	 * Establishes a gRPC channel to a server and calls {@link #runAllExamples(ManagedChannel)}.
	 * @param args if any args are present, the first will be considered a target specification
	 *     instead of the default.
	 */
	public static void main(String[] args) throws Throwable {
		final var target = args.length > 0
				? args[0]
				: "localhost:" + RecordStorageServer.DEFAULT_PORT;
		final var managedChannel = ManagedChannelBuilder
			.forTarget(target)
			.usePlaintext()
			.build();
		try {
			runAllExamples(managedChannel);
		} finally {
			managedChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
			if ( !managedChannel.isTerminated()) {
				System.out.println("channel has NOT shutdown cleanly");
				managedChannel.shutdownNow();
			}
		}
	}

	static void runAllExamples(ManagedChannel managedChannel) throws Throwable {

		// setup Guice
		final var grpcModule = new GrpcModule();
		final Module recordStorageClientModule = (binder) -> {
			binder.bind(Integer.class)
				.annotatedWith(Names.named(ClientResponseProcessor.RPC_COUNTER))
				.toProvider(new Counter())
				.in(grpcModule.rpcScope);
			binder.bind(Integer.class)
				.annotatedWith(Names.named(ClientResponseProcessor.RESPONSE_COUNTER))
				.toProvider(new Counter())
				.in(grpcModule.listenerEventScope);
			binder.bind(ClientResponseProcessor.class)
				.in(Scopes.SINGLETON);
		};
		final var injector = Guice.createInjector(grpcModule, recordStorageClientModule);

		// intercept gRPC client channel and create connectors
		final var interceptedChannel = ClientInterceptors.intercept(
				managedChannel, grpcModule.clientInterceptor);
		final var asyncConnector = RecordStorageGrpc.newStub(interceptedChannel);
		final var blockingConnector = RecordStorageGrpc.newBlockingStub(interceptedChannel)
				.withDeadlineAfter(10, TimeUnit.SECONDS);

		// run the actual examples
		storeSingle(blockingConnector, "first single");
		System.out.println();
		storeMultiple(asyncConnector, injector);
		System.out.println();
		storeSingle(blockingConnector, "last single");
		System.out.println();
		getAll(blockingConnector);
	}

	static class Counter implements Provider<Integer> {

		final AtomicInteger counter = new AtomicInteger(0);

		@Override public Integer get() {
			return counter.incrementAndGet();
		}
	}



	/**
	 * Calls {@code storeMultiple} 2 times concurrently and processes results with a
	 * {@link ClientResponseProcessor}.
	 * @param injector used to obtain an instance of {@link ClientResponseProcessor}.
	 */
	static void storeMultiple(RecordStorageStub asyncConnector, Injector injector)
			throws Throwable {
		System.out.println("2 concurrent store multiple");
		final var responseProcessor = injector.getInstance(ClientResponseProcessor.class);
		final var storeRecordResponseObserver1 = new BlockingResponseObserver<>(responseProcessor);
		final var storeRecordResponseObserver2 = new BlockingResponseObserver<>(responseProcessor);
		final var requestObserver1 = asyncConnector.storeMultiple(storeRecordResponseObserver1);
		final var requestObserver2 = asyncConnector.storeMultiple(storeRecordResponseObserver2);
		for (int i = 1; i <= 9; i++) {
			requestObserver1.onNext(
					StoreRecordRequest.newBuilder()
						.setRequestId(i)
						.setContent(String.format("{ RPC: 1, requestId: %4d }", i))
						.build());
			requestObserver2.onNext(
					StoreRecordRequest.newBuilder()
						.setRequestId(i)
						.setContent(String.format("{ RPC: 2, requestId: %4d }", i))
						.build());
		}
		requestObserver1.onCompleted();
		requestObserver2.onCompleted();
		Awaitable.awaitMultiple(
			5000L,
			storeRecordResponseObserver1.toAwaitable(),
			storeRecordResponseObserver2.toAwaitable()
		);
		if (storeRecordResponseObserver1.getError().isPresent()) {
			throw storeRecordResponseObserver1.getError().get();
		}
		if (storeRecordResponseObserver2.getError().isPresent()) {
			throw storeRecordResponseObserver2.getError().get();
		}
	}



	/**
	 * Calls {@code store}.
	 * @param content content of the record that will be stored.
	 */
	static void storeSingle(RecordStorageBlockingStub blockingConnector, String content) {
		System.out.println("store single");
		final NewRecordId recordIdResponse = blockingConnector.store(
				Record.newBuilder().setContent(content).build());
		System.out.println("recordId: " + recordIdResponse.getId());
	}



	/**
	 * Calls {@code getAll} and prints the results.
	 */
	static void getAll(RecordStorageBlockingStub blockingConnector) {
		System.out.println("get all");
		final var recordIterator = blockingConnector.getAll(Empty.newBuilder().build());
		while (recordIterator.hasNext()) {
			final Record record = recordIterator.next();
			System.out.printf("recordId: %4d -> %s%n", record.getId(), record.getContent());
		}
	}
}
