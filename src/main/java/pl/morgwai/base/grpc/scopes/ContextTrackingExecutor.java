// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

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
	 * {@link Status#UNAVAILABLE} to {@code responseObserver}.
	 */
	public void execute(StreamObserver<?> responseObserver, Runnable task) {
		try {
			execute(task);
		} catch (RejectedExecutionException e) {
			responseObserver.onError(Status.UNAVAILABLE.asException());
		}
	}



	ContextTrackingExecutor(String name, int poolSize, ContextTracker<?>... trackers) {
		super(name, poolSize, trackers);
	}



	ContextTrackingExecutor(
			String name,
			int poolSize,
			BlockingQueue<Runnable> workQueue,
			ContextTracker<?>... trackers) {
		super(name, poolSize, workQueue, trackers);
	}



	ContextTrackingExecutor(
			String name,
			int poolSize,
			BlockingQueue<Runnable> workQueue,
			ThreadFactory threadFactory,
			ContextTracker<?>... trackers) {
		super(name, poolSize, workQueue, threadFactory, trackers);
	}



	ContextTrackingExecutor(
			String name,
			ExecutorService backingExecutor,
			int poolSize,
			ContextTracker<?>... trackers) {
		super(name, backingExecutor, poolSize, trackers);
	}
}
