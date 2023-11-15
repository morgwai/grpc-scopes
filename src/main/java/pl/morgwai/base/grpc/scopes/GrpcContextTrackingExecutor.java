// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.List;
import java.util.concurrent.*;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pl.morgwai.base.guice.scopes.*;
import pl.morgwai.base.utils.concurrent.NamingThreadFactory;
import pl.morgwai.base.utils.concurrent.TaskTrackingThreadPoolExecutor;



/**
 * A {@link TaskTrackingThreadPoolExecutor} that wraps tasks with {@link ContextBoundRunnable}
 * decorator to automatically transfer contexts.
 * <p>
 * Instances should usually be created using
 * {@link GrpcModule#newContextTrackingExecutor(String, int)
 * GrpcModule.newContextTrackingExecutor(...)} helper methods family.</p>
 */
public class GrpcContextTrackingExecutor extends TaskTrackingThreadPoolExecutor {



	public String getName() { return name; }
	final String name;

	final ContextBinder ctxBinder;



	/** See {@link GrpcModule#newContextTrackingExecutor(String, int)}. */
	public GrpcContextTrackingExecutor(String name, ContextBinder ctxBinder, int poolSize) {
		this(name, ctxBinder, poolSize, new LinkedBlockingQueue<>());
	}



	@Override
	public void execute(Runnable task) {
		super.execute(ctxBinder.bindToContext(task));
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



	/**
	 * See {@link GrpcModule#newContextTrackingExecutor(String, int)}.
	 * @deprecated use {@link #GrpcContextTrackingExecutor(String, ContextBinder, int)} instead.
	 */
	@Deprecated(forRemoval = true)
	public GrpcContextTrackingExecutor(String name, List<ContextTracker<?>> trackers, int poolSize)
	{
		this(name, new ContextBinder(trackers), poolSize);
	}



	/**
	 * See {@link GrpcModule#newContextTrackingExecutor(String, int, int)}.
	 * @deprecated use {@link #GrpcContextTrackingExecutor(String, ContextBinder, int, int)}
	 * instead.
	 */
	@Deprecated(forRemoval = true)
	public GrpcContextTrackingExecutor(
		String name,
		List<ContextTracker<?>> trackers,
		int poolSize,
		int queueSize
	) {
		this(name, new ContextBinder(trackers), poolSize, queueSize);
	}



	/** See {@link GrpcModule#newContextTrackingExecutor(String, int, int)}. */
	public GrpcContextTrackingExecutor(
		String name,
		ContextBinder ctxBinder,
		int poolSize,
		int queueSize
	) {
		this(name, ctxBinder, poolSize, new LinkedBlockingQueue<>(queueSize));
	}



	/**
	 * @deprecated use
	 * {@link #GrpcContextTrackingExecutor(String, ContextBinder, int, BlockingQueue)} instead.
	 */
	@Deprecated(forRemoval = true)
	public GrpcContextTrackingExecutor(
		String name,
		List<ContextTracker<?>> trackers,
		int poolSize,
		BlockingQueue<Runnable> workQueue
	) {
		this(name, new ContextBinder(trackers), poolSize, workQueue);
	}



	public GrpcContextTrackingExecutor(
		String name,
		ContextBinder ctxBinder,
		int poolSize,
		BlockingQueue<Runnable> workQueue
	) {
		super(poolSize, poolSize, 0L, TimeUnit.DAYS, workQueue, new NamingThreadFactory(name));
		this.name = name;
		this.ctxBinder = ctxBinder;
	}



	/**
	 * See {@link GrpcModule#newContextTrackingExecutor(String, int, BlockingQueue,
	 * RejectedExecutionHandler)}.
	 * @deprecated use {@link #GrpcContextTrackingExecutor(String, ContextBinder, int,
	 * BlockingQueue, RejectedExecutionHandler)} instead.
	 */
	@Deprecated(forRemoval = true)
	public GrpcContextTrackingExecutor(
		String name,
		List<ContextTracker<?>> trackers,
		int poolSize,
		BlockingQueue<Runnable> workQueue,
		RejectedExecutionHandler rejectionHandler
	) {
		this(
			name,
			new ContextBinder(trackers),
			poolSize,
			workQueue,
			rejectionHandler
		);
	}



	/**
	 * See {@link GrpcModule#newContextTrackingExecutor(String, int, BlockingQueue,
	 * RejectedExecutionHandler)}.
	 */
	public GrpcContextTrackingExecutor(
		String name,
		ContextBinder ctxBinder,
		int poolSize,
		BlockingQueue<Runnable> workQueue,
		RejectedExecutionHandler rejectionHandler
	) {
		this(
			name,
			ctxBinder,
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
	 * @deprecated use {@link #GrpcContextTrackingExecutor(String, ContextBinder, int, int, long,
	 * TimeUnit, BlockingQueue, ThreadFactory, RejectedExecutionHandler)} instead.
	 */
	@Deprecated(forRemoval = true)
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
		this(
			name,
			new ContextBinder(trackers),
			corePoolSize,
			maxPoolSize,
			keepAliveTime,
			unit,
			workQueue,
			threadFactory,
			handler
		);
	}



	/**
	 * See {@link GrpcModule#newContextTrackingExecutor(String, int, int, long, TimeUnit,
	 * BlockingQueue, ThreadFactory, RejectedExecutionHandler)}.
	 */
	public GrpcContextTrackingExecutor(
		String name,
		ContextBinder ctxBinder,
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
		this.ctxBinder = ctxBinder;
	}
}
