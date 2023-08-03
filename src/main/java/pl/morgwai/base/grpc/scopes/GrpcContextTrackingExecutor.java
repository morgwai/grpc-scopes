// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.List;
import java.util.concurrent.*;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pl.morgwai.base.guice.scopes.ContextBoundTask;
import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.util.concurrent.NamingThreadFactory;
import pl.morgwai.base.util.concurrent.TaskTrackingThreadPoolExecutor;



/**
 * A {@link TaskTrackingThreadPoolExecutor} that wraps tasks with {@link ContextBoundTask} decorator
 * to automatically transfer contexts.
 * <p>
 * Instances should usually be created using
 * {@link GrpcModule#newContextTrackingExecutor(String, int)
 * GrpcModule.newContextTrackingExecutor(...)} helper methods family.</p>
 */
public class GrpcContextTrackingExecutor extends TaskTrackingThreadPoolExecutor {



	public String getName() { return name; }
	final String name;

	final List<ContextTracker<?>> trackers;



	/** See {@link GrpcModule#newContextTrackingExecutor(String, int)}. */
	public GrpcContextTrackingExecutor(String name, List<ContextTracker<?>> trackers, int poolSize)
	{
		this(name, trackers, poolSize, new LinkedBlockingQueue<>());
	}



	@Override
	public void execute(Runnable task) {
		super.execute(new ContextBoundTask(task, ContextTracker.getActiveContexts(trackers)));
	}



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



	@Override
	public String toString() {
		return "GrpcContextTrackingExecutor { name=\"" + name + "\" }";
	}



	/** See {@link GrpcModule#newContextTrackingExecutor(String, int, int)}. */
	public GrpcContextTrackingExecutor(
		String name,
		List<ContextTracker<?>> trackers,
		int poolSize,
		int queueSize
	) {
		this(name, trackers, poolSize, new LinkedBlockingQueue<>(queueSize));
	}



	public GrpcContextTrackingExecutor(
		String name,
		List<ContextTracker<?>> trackers,
		int poolSize,
		BlockingQueue<Runnable> workQueue
	) {
		super(poolSize, poolSize, 0L, TimeUnit.DAYS, workQueue, new NamingThreadFactory(name));
		this.name = name;
		this.trackers = trackers;
	}

	/**
	 * See {@link GrpcModule#newContextTrackingExecutor(String, int, BlockingQueue,
	 * RejectedExecutionHandler)}.
	 */
	public GrpcContextTrackingExecutor(
		String name,
		List<ContextTracker<?>> trackers,
		int poolSize,
		BlockingQueue<Runnable> workQueue,
		RejectedExecutionHandler rejectionHandler
	) {
		this(
			name,
			trackers,
			poolSize,
			poolSize,
			0L,
			TimeUnit.SECONDS,
			workQueue,
			new NamingThreadFactory(name),
			rejectionHandler
		);
	}

	/**
	 * See {@link GrpcModule#newContextTrackingExecutor(String, int, int, long, TimeUnit,
	 * BlockingQueue, ThreadFactory, RejectedExecutionHandler)}.
	 */
	public GrpcContextTrackingExecutor(
		String name,
		List<ContextTracker<?>> trackers,
		int corePoolSize,
		int maxPoolSize,
		long keepAliveTime,
		TimeUnit unit,
		BlockingQueue<Runnable> workQueue,
		ThreadFactory threadFactory,
		RejectedExecutionHandler handler
	) {
		super(corePoolSize, maxPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
		this.name = name;
		this.trackers = trackers;
	}
}
