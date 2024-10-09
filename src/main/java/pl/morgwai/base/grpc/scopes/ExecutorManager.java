// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.*;
import java.util.concurrent.*;

import io.grpc.stub.StreamObserver;
import pl.morgwai.base.guice.scopes.ContextBinder;
import pl.morgwai.base.guice.scopes.ContextBoundRunnable;
import pl.morgwai.base.utils.concurrent.Awaitable;



/**
 * Utility that helps to automatically shutdown its created {@link GrpcContextTrackingExecutor}s
 * at an app shutdown.
 */
public class ExecutorManager {



	final List<GrpcContextTrackingExecutor> executors = new LinkedList<>();
	final ContextBinder ctxBinder;



	public ExecutorManager(ContextBinder ctxBinder) {
		this.ctxBinder = ctxBinder;
	}



	/** List of all {@code Executors} created by this {@code ExecutorManager}. */
	public List<GrpcContextTrackingExecutor> getExecutors() {
		return Collections.unmodifiableList(executors);
	}



	/**
	 * Constructs a fixed size, context tracking executor that uses an unbound
	 * {@link LinkedBlockingQueue} and a new
	 * {@link pl.morgwai.base.utils.concurrent.NamingThreadFactory} named after this executor.
	 * <p>
	 * To avoid {@link OutOfMemoryError}s, an external mechanism that limits maximum number of tasks
	 * (such as a load balancer or a frontend proxy) should be used.</p>
	 */
	public GrpcContextTrackingExecutor newContextTrackingExecutor(String name, int poolSize) {
		final var executor = new GrpcContextTrackingExecutor(name, ctxBinder, poolSize);
		executors.add(executor);
		return executor;
	}



	/**
	 * Constructs a fixed size, context tracking executor that uses a {@link LinkedBlockingQueue} of
	 * size {@code queueSize}, the default {@link RejectedExecutionHandler} and a new
	 * {@link pl.morgwai.base.utils.concurrent.NamingThreadFactory} named after this executor.
	 * <p>
	 * The default {@link RejectedExecutionHandler} throws a {@link RejectedExecutionException} if
	 * the queue is full or the executor is shutting down. It should usually be handled by
	 * sending {@link io.grpc.Status#UNAVAILABLE} to the client.</p>
	 */
	public GrpcContextTrackingExecutor newContextTrackingExecutor(
		String name,
		int poolSize,
		int queueSize
	) {
		final var executor = new GrpcContextTrackingExecutor(name, ctxBinder, poolSize, queueSize);
		executors.add(executor);
		return executor;
	}



	/**
	 * Constructs a context tracking executor.
	 * @see ThreadPoolExecutor#ThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue,
	 *     ThreadFactory) ThreadPoolExecutor constructor docs for param details
	 */
	public GrpcContextTrackingExecutor newContextTrackingExecutor(
		String name,
		int corePoolSize,
		int maxPoolSize,
		long keepAliveTime,
		TimeUnit unit,
		BlockingQueue<Runnable> workQueue,
		ThreadFactory threadFactory
	) {
		final var executor = new GrpcContextTrackingExecutor(
			name,
			ctxBinder,
			corePoolSize,
			maxPoolSize,
			keepAliveTime,
			unit,
			workQueue,
			threadFactory
		);
		executors.add(executor);
		return executor;
	}



	/**
	 * Constructs a context tracking executor.
	 * <p>
	 * {@code rejectionHandler} will receive a task wrapped with a {@link ContextBoundRunnable}.</p>
	 * <p>
	 * In order for {@link GrpcContextTrackingExecutor#execute(StreamObserver, Runnable)} to work
	 * properly, the {@code rejectionHandler} must throw a {@link RejectedExecutionException}.</p>
	 * @see ThreadPoolExecutor#ThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue,
	 *     ThreadFactory, RejectedExecutionHandler) ThreadPoolExecutor constructor docs for param
	 *     details
	 */
	public GrpcContextTrackingExecutor newContextTrackingExecutor(
		String name,
		int corePoolSize,
		int maxPoolSize,
		long keepAliveTime,
		TimeUnit unit,
		BlockingQueue<Runnable> workQueue,
		ThreadFactory threadFactory,
		RejectedExecutionHandler handler
	) {
		final var executor = new GrpcContextTrackingExecutor(
			name,
			ctxBinder,
			corePoolSize,
			maxPoolSize,
			keepAliveTime,
			unit,
			workQueue,
			threadFactory,
			handler
		);
		executors.add(executor);
		return executor;
	}



	/** Shutdowns all executors obtained from this {@code ExecutorManager}. */
	public void shutdown() {
		for (var executor: executors) executor.shutdown();
	}



	/**
	 * {@link GrpcContextTrackingExecutor#toAwaitableOfEnforcedTermination() Enforces termination}
	 * of all executors obtained from this {@code ExecutorManager}.
	 * @return an empty list if all executors were terminated, list of unterminated otherwise.
	 */
	public List<GrpcContextTrackingExecutor> enforceTermination(
		long timeout,
		TimeUnit unit
	) throws InterruptedException {
		return Awaitable.awaitMultiple(
			timeout,
			unit,
			executors.stream().map(Awaitable.entryMapper(
					GrpcContextTrackingExecutor::toAwaitableOfEnforcedTermination))
		);
	}



	/**
	 * {@link GrpcContextTrackingExecutor#toAwaitableOfTermination() Awaits for termination} of all
	 * executors obtained from this {@code ExecutorManager}.
	 * @return an empty list if all executors were terminated, list of unterminated otherwise.
	 */
	public List<GrpcContextTrackingExecutor> awaitTermination(
		long timeout,
		TimeUnit unit
	) throws InterruptedException {
		return Awaitable.awaitMultiple(
			timeout,
			unit,
			executors.stream().map(Awaitable.entryMapper(
					GrpcContextTrackingExecutor::toAwaitableOfTermination))
		);
	}



	/**
	 * {@link GrpcContextTrackingExecutor#awaitTermination() Awaits for termination} of all
	 * executors obtained from this {@code ExecutorManager}.
	 */
	public void awaitTermination() throws InterruptedException {
		for (var executor: executors) executor.awaitTermination();
	}



	/** Creates {@link Awaitable.WithUnit} of {@link #enforceTermination(long, TimeUnit)}. */
	public Awaitable.WithUnit toAwaitableOfEnforcedTermination() {
		return (timeout, unit) -> enforceTermination(timeout, unit).isEmpty();
	}



	/** Creates {@link Awaitable.WithUnit} of {@link #awaitTermination(long, TimeUnit)}. */
	public Awaitable.WithUnit toAwaitableOfTermination() {
		return (timeout, unit) -> awaitTermination(timeout, unit).isEmpty();
	}
}
