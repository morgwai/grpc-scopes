// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.concurrent.*;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pl.morgwai.base.guice.scopes.ContextBinder;
import pl.morgwai.base.utils.concurrent.NamingThreadFactory;
import pl.morgwai.base.utils.concurrent.TaskTrackingThreadPoolExecutor;



/**
 * {@link TaskTrackingThreadPoolExecutor} that automatically transfer {@code Contexts} to worker
 * {@code Threads} using {@link ContextBinder}.
 * <p>
 * Instances should usually be created using
 * {@link ExecutorManager#newContextTrackingExecutor(String, int)
 * ExecutorManager.newContextTrackingExecutor(...)} helper methods family.</p>
 */
public class GrpcContextTrackingExecutor extends TaskTrackingThreadPoolExecutor {



	public String getName() { return name; }
	public final String name;

	final ContextBinder ctxBinder;



	/** See {@link ExecutorManager#newContextTrackingExecutor(String, int)}. */
	public GrpcContextTrackingExecutor(String name, ContextBinder ctxBinder, int poolSize) {
		this(
			name,
			ctxBinder,
			poolSize,
			poolSize,
			0L,
			TimeUnit.DAYS,
			new LinkedBlockingQueue<>(),
			new NamingThreadFactory(name)
		);
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



	// below only constructor variants



	/**
	 * See {@link ExecutorManager#newContextTrackingExecutor(String, int, int, long, TimeUnit,
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



	/**
	 * See {@link ExecutorManager#newContextTrackingExecutor(String, int, int, long, TimeUnit,
	 * BlockingQueue, ThreadFactory)}.
	 */
	public GrpcContextTrackingExecutor(
		String name,
		ContextBinder ctxBinder,
		int corePoolSize,
		int maxPoolSize,
		long keepAliveTime,
		TimeUnit unit,
		BlockingQueue<Runnable> workQueue,
		ThreadFactory threadFactory
	) {
		super(corePoolSize, maxPoolSize, keepAliveTime, unit, workQueue, threadFactory);
		this.name = name;
		this.ctxBinder = ctxBinder;
	}



	/** See {@link ExecutorManager#newContextTrackingExecutor(String, int, int)}. */
	public GrpcContextTrackingExecutor(
		String name,
		ContextBinder ctxBinder,
		int poolSize,
		int queueSize
	) {
		this(
			name,
			ctxBinder,
			poolSize,
			poolSize,
			0L,
			TimeUnit.DAYS,
			new LinkedBlockingQueue<>(queueSize),
			new NamingThreadFactory(name)
		);
	}
}
