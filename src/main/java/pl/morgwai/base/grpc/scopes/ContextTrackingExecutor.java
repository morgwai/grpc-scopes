// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * A {@link pl.morgwai.base.guice.scopes.ContextTrackingExecutor} with additional
 * {@link #execute(StreamObserver, Runnable)} method that sends {@link Status#UNAVAILABLE} if this
 * executor rejects a task.
 */
public class ContextTrackingExecutor extends pl.morgwai.base.guice.scopes.ContextTrackingExecutor {



	/**
	 * Calls {@link #execute(Runnable) execute(task)} and if it's rejected sends
	 * {@link Status#UNAVAILABLE} to {@code responseObserver}.
	 */
	public void execute(StreamObserver<?> responseObserver, Runnable task) {
		try {
			execute(task);
		} catch (RejectedExecutionException e) {
			if ( ! backingExecutor.isShutdown()) log.warn("executor " + getName() + " overloaded");
			responseObserver.onError(Status.UNAVAILABLE.asException());
		}
	}



	/**
	 * Constructs an instance backed by a new fixed size {@link ThreadPoolExecutor} that uses a
	 * {@link NamedThreadFactory} and an unbound {@link LinkedBlockingQueue}.
	 * <p>
	 * To avoid {@link OutOfMemoryError}s, an external mechanism that limits maximum number of tasks
	 * (such as a load balancer or frontend) should be used.</p>
	 */
	public ContextTrackingExecutor(String name, int poolSize, ContextTracker<?>... trackers) {
		super(name, poolSize, trackers);
	}



	/**
	 * Constructs an instance backed by a new fixed size {@link ThreadPoolExecutor} that uses a
	 * {@link NamedThreadFactory}.
	 * <p>
	 * Throws a {@link RejectedExecutionException} if {@code workQueue} is full. It should usually
	 * be handled by sending status {@link Status#UNAVAILABLE} to the client.</p>
	 */
	public ContextTrackingExecutor(
			String name,
			int poolSize,
			BlockingQueue<Runnable> workQueue,
			ContextTracker<?>... trackers) {
		super(name, poolSize, workQueue, trackers);
	}



	/**
	 * Constructs an instance backed by a new fixed size {@link ThreadPoolExecutor}.
	 * <p>
	 * Throws a {@link RejectedExecutionException} if {@code workQueue} is full. It should usually
	 * be handled by sending status {@link Status#UNAVAILABLE} to the client.</p>
	 */
	public ContextTrackingExecutor(
			String name,
			int poolSize,
			BlockingQueue<Runnable> workQueue,
			ThreadFactory threadFactory,
			ContextTracker<?>... trackers) {
		super(name, poolSize, workQueue, threadFactory, trackers);
	}



	/**
	 * Constructs an instance backed by {@code backingExecutor}.
	 * <p>
	 * <b>NOTE:</b> {@code backingExecutor.execute(task)} must throw
	 * {@link RejectedExecutionException} in case of rejection for
	 * {@link #execute(StreamObserver, Runnable)} to work properly.</p>
	 * <p>
	 * {@code poolSize} is informative only, to be returned by {@link #getPoolSize()}.</p>
	 */
	public ContextTrackingExecutor(
			String name,
			ExecutorService backingExecutor,
			int poolSize,
			ContextTracker<?>... trackers) {
		super(name, backingExecutor, poolSize, trackers);
	}
}
