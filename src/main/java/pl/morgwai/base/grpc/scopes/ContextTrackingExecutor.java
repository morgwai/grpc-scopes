// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import pl.morgwai.base.util.concurrent.Awaitable;
import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * A {@link pl.morgwai.base.guice.scopes.ContextTrackingExecutor} with additional
 * {@link #execute(StreamObserver, Runnable) execute(responseObserver, task)} methods that send
 * {@link Status#UNAVAILABLE} if {@code task} is rejected.
 * This can happen due to an overload or a shutdown.
 * <p>
 * Instances can be created using {@link GrpcModule#newContextTrackingExecutor(String, int)
 * GrpcModule.newContextTrackingExecutor(...)} helper methods family.</p>
 */
public class ContextTrackingExecutor extends pl.morgwai.base.guice.scopes.ContextTrackingExecutor {



	/**
	 * Calls {@link #execute(Runnable) execute(task)} and if it's rejected, sends
	 * {@link Status#UNAVAILABLE} to {@code outboundObserver}.
	 */
	public void execute(StreamObserver<?> outboundObserver, Runnable task) {
		try {
			execute(task);
		} catch (RejectedExecutionException e) {
			outboundObserver.onError(Status.UNAVAILABLE.withCause(e).asException());
		}
	}



	/**
	 * Calls {@link #execute(Callable) execute(task)} and if it's rejected, sends
	 * {@link Status#UNAVAILABLE} to {@code outboundObserver} and returns
	 * {@link CompletableFuture#failedFuture(Throwable)} with the {@link RejectedExecutionException}
	 * as the argument.
	 */
	public <T> CompletableFuture<T> execute(StreamObserver<?> outboundObserver, Callable<T> task) {
		try {
			return execute(task);
		} catch (RejectedExecutionException e) {
			outboundObserver.onError(Status.UNAVAILABLE.withCause(e).asException());
			return CompletableFuture.failedFuture(e);
		}
	}



	public Awaitable.WithUnit toAwaitableOfEnforcedTermination() {
		shutdown();
		return (timeout, unit) -> enforceTermination(timeout, unit).isEmpty();
	}



	public Awaitable.WithUnit toAwaitableOfTermination() {
		shutdown();
		return this::awaitTermination;
	}



	ContextTrackingExecutor(String name, int poolSize, List<ContextTracker<?>> trackers) {
		super(name, poolSize, trackers);
	}



	ContextTrackingExecutor(
		String name,
		int poolSize,
		List<ContextTracker<?>> trackers,
		BlockingQueue<Runnable> workQueue
	) {
		super(name, poolSize, trackers, workQueue);
	}



	ContextTrackingExecutor(
		String name,
		int poolSize,
		List<ContextTracker<?>> trackers,
		BlockingQueue<Runnable> workQueue,
		BiConsumer<Object, ? super ContextTrackingExecutor> rejectionHandler
	) {
		super(
			name,
			poolSize,
			trackers,
			workQueue,
			(task, executor) -> rejectionHandler.accept(task, (ContextTrackingExecutor) executor)
		);
	}



	ContextTrackingExecutor(
		String name,
		int poolSize,
		List<ContextTracker<?>> trackers,
		BlockingQueue<Runnable> workQueue,
		BiConsumer<Object, ? super ContextTrackingExecutor> rejectionHandler,
		ThreadFactory threadFactory
	) {
		super(
			name,
			poolSize,
			trackers,
			workQueue,
			(task, executor) -> rejectionHandler.accept(task, (ContextTrackingExecutor) executor),
			threadFactory
		);
	}



	ContextTrackingExecutor(
		String name,
		int poolSize,
		List<ContextTracker<?>> trackers,
		ExecutorService backingExecutor
	) {
		super(name, poolSize, trackers, backingExecutor);
	}
}
