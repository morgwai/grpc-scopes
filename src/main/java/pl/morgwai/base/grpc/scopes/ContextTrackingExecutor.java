// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.List;
import java.util.concurrent.*;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import pl.morgwai.base.concurrent.Awaitable;
import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * A {@link pl.morgwai.base.guice.scopes.ContextTrackingExecutor} with additional
 * {@link #execute(StreamObserver, Runnable) execute(responseObserver, task)} method that sends
 * {@link Status#UNAVAILABLE} if the task is rejected.
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
			BlockingQueue<Runnable> workQueue,
			List<ContextTracker<?>> trackers) {
		super(name, poolSize, workQueue, trackers);
	}



	ContextTrackingExecutor(
			String name,
			int poolSize,
			BlockingQueue<Runnable> workQueue,
			ThreadFactory threadFactory,
			List<ContextTracker<?>> trackers) {
		super(name, poolSize, workQueue, threadFactory, trackers);
	}



	ContextTrackingExecutor(
			String name,
			ExecutorService backingExecutor,
			int poolSize,
			List<ContextTracker<?>> trackers) {
		super(name, backingExecutor, poolSize, trackers);
	}
}
