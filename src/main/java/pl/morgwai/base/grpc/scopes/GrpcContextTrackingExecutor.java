// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.ContextTrackingExecutor;
import pl.morgwai.base.util.concurrent.NamingThreadFactory;
import pl.morgwai.base.util.concurrent.TaskTrackingExecutor.TaskTrackingExecutorDecorator;



/**
 * A {@link ContextTrackingExecutor} with additional {@link #execute(StreamObserver, Runnable)
 * execute(responseObserver, task)} methods that send {@link Status#UNAVAILABLE} if {@code task} is
 * rejected.
 * <p>
 * Instances can be created using {@link GrpcModule#newContextTrackingExecutor(String, int)
 * GrpcModule.newContextTrackingExecutor(...)} helper methods family.</p>
 */
public class GrpcContextTrackingExecutor extends TaskTrackingExecutorDecorator {



	public String getName() { return name; }
	final String name;



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



	GrpcContextTrackingExecutor(
		String name,
		List<ContextTracker<?>> trackers,
		ThreadPoolExecutor backingExecutor
	) {
		this(name, trackers, (ExecutorService) backingExecutor);
		decorateRejectedExecutionHandler(backingExecutor);
		ContextTrackingExecutor.decorateRejectedExecutionHandler(backingExecutor);
	}

	GrpcContextTrackingExecutor(
		String name,
		List<ContextTracker<?>> trackers,
		ExecutorService backingExecutor
	) {
		super(new ContextTrackingExecutor(trackers, backingExecutor));
		this.name = name;
	}

	GrpcContextTrackingExecutor(String name, List<ContextTracker<?>> trackers, int poolSize) {
		this(
			name,
			trackers,
			new ThreadPoolExecutor(
				poolSize, poolSize, 0L, TimeUnit.SECONDS,
				new LinkedBlockingQueue<>(),
				new NamingThreadFactory(name)
			)
		);
	}

	GrpcContextTrackingExecutor(
		String name,
		List<ContextTracker<?>> trackers,
		int poolSize,
		BlockingQueue<Runnable> workQueue
	) {
		this(
			name,
			trackers,
			new ThreadPoolExecutor(
				poolSize, poolSize, 0L, TimeUnit.SECONDS,
				workQueue,
				new NamingThreadFactory(name)
			)
		);
	}

	GrpcContextTrackingExecutor(
		String name,
		List<ContextTracker<?>> trackers,
		int poolSize,
		BlockingQueue<Runnable> workQueue,
		BiConsumer<? super Runnable, ? super GrpcContextTrackingExecutor> rejectionHandler
	) {
		this(
			name,
			trackers,
			poolSize,
			workQueue,
			new RejectionHandler(rejectionHandler),
			new NamingThreadFactory(name)
		);
	}

	GrpcContextTrackingExecutor(
		String name,
		List<ContextTracker<?>> trackers,
		int poolSize,
		BlockingQueue<Runnable> workQueue,
		BiConsumer<? super Runnable, ? super GrpcContextTrackingExecutor> rejectionHandler,
		ThreadFactory threadFactory
	) {
		this(
			name,
			trackers,
			poolSize,
			workQueue,
			new RejectionHandler(rejectionHandler),
			threadFactory
		);
	}

	private static class RejectionHandler implements RejectedExecutionHandler {

		GrpcContextTrackingExecutor executor;
		final BiConsumer<? super Runnable, ? super GrpcContextTrackingExecutor> handler;

		RejectionHandler(BiConsumer<? super Runnable, ? super GrpcContextTrackingExecutor> handler)
		{
			this.handler = handler;
		}

		@Override
		public void rejectedExecution(Runnable rejectedTask, ThreadPoolExecutor backingExecutor) {
			handler.accept(rejectedTask, executor);
		}
	}

	private GrpcContextTrackingExecutor(
		String name,
		List<ContextTracker<?>> trackers,
		int poolSize,
		BlockingQueue<Runnable> workQueue,
		RejectionHandler rejectionHandler,
		ThreadFactory threadFactory
	) {
		this(
			name,
			trackers,
			new ThreadPoolExecutor(
				poolSize, poolSize, 0L, TimeUnit.SECONDS,
				workQueue,
				threadFactory,
				rejectionHandler
			)
		);
		rejectionHandler.executor = this;
	}
}
