// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import com.google.inject.*;
import com.google.inject.Module;

import io.grpc.*;

import io.grpc.stub.StreamObserver;
import pl.morgwai.base.guice.scopes.ContextTrackingExecutor.DetailedRejectedExecutionException;
import pl.morgwai.base.guice.scopes.ContextTrackingExecutor.NamedThreadFactory;
import pl.morgwai.base.util.concurrent.Awaitable;
import pl.morgwai.base.guice.scopes.*;



/**
 * gRPC Guice {@link Scope}s, {@link ContextTracker}s and some helper methods.
 * <p>
 * <b>NO STATIC:</b> by a common convention, objects such as <code>Scope</code>s are usually stored
 * on <code>static</code> vars. Global context however has a lot of drawbacks. Instead, create just
 * 1 {@code GrpcModule} instance in your app initialization code (for example on a local var in your
 * <code>main</code> method) and then use its member scopes ({@link #rpcScope},
 * {@link #listenerEventScope}) in your Guice {@link Module}s and {@link #serverInterceptor}
 * to configure your bindings.</p>
 */
public class GrpcModule implements Module {



	/**
	 * Allows tracking of the
	 * {@link ListenerEventContext context of a Listener event}.
	 * @see #listenerEventScope
	 */
	public final ContextTracker<ListenerEventContext> listenerEventContextTracker =
			new ContextTracker<>();

	/**
	 * Scopes objects to the {@link ListenerEventContext context of a Listener event} (either
	 * {@link io.grpc.ServerCall.Listener server} or {@link io.grpc.ClientCall.Listener client}) and
	 * as a consequence also to the context of the corresponding user inbound
	 * {@link io.grpc.stub.StreamObserver} call.
	 */
	public final Scope listenerEventScope =
			new ContextScope<>("LISTENER_EVENT_SCOPE", listenerEventContextTracker);



	/**
	 * Scopes objects to the context of an RPC (either {@link ServerRpcContext} or
	 * {@link ClientRpcContext}).
	 */
	public final Scope rpcScope = new InducedContextScope<>(
			"RPC_SCOPE", listenerEventContextTracker, ListenerEventContext::getRpcContext);



	/**
	 * All gRPC services must be intercepted by this interceptor.
	 * @see io.grpc.ServerInterceptors#intercept(io.grpc.BindableService, java.util.List)
	 */
	public final ServerInterceptor serverInterceptor = new ServerContextInterceptor(this);

	/**
	 * All {@link Channel client Channels} must be intercepted either by this interceptor or by
	 * {@link #nestingClientInterceptor}. This interceptor will keep client RPC contexts separate
	 * even if the client calls were made within some enclosing RPC contexts.
	 * @see ClientInterceptors#intercept(Channel, ClientInterceptor...)
	 */
	public final ClientInterceptor clientInterceptor = new ClientContextInterceptor(this, false);

	/**
	 * All {@link Channel client Channels} must be intercepted either by this interceptor or by
	 * {@link #clientInterceptor}. If a client call was made within a context of some enclosing
	 * "parent" call (server call or a previous chained client call), this interceptor will join
	 * together such nested client RPC context with its parent RPC context, so that they will share
	 * all RPC scoped objects.
	 * @see ClientInterceptors#intercept(Channel, ClientInterceptor...)
	 */
	public final ClientInterceptor nestingClientInterceptor =
			new ClientContextInterceptor(this, true);



	/**
	 * Singleton of {@link #listenerEventScope}.
	 * {@link #configure(Binder)} binds {@code List<ContextTracker<?>>} to it for use with
	 * {@link GrpcContextTrackingExecutor#getActiveContexts(List)}.
	 */
	public final List<ContextTracker<?>> allTrackers = List.of(listenerEventContextTracker);



	/**
	 * Binds  {@link #listenerEventContextTracker} and both contexts for injection.
	 * Binds {@code List<ContextTracker<?>>} to {@link #allTrackers} that contains all trackers for
	 * use with {@link GrpcContextTrackingExecutor#getActiveContexts(List)}.
	 */
	@Override
	public void configure(Binder binder) {
		TypeLiteral<ContextTracker<ListenerEventContext>> messageContextTrackerType =
				new TypeLiteral<>() {};
		binder.bind(messageContextTrackerType).toInstance(listenerEventContextTracker);
		binder.bind(ListenerEventContext.class).toProvider(
				listenerEventContextTracker::getCurrentContext);
		binder.bind(RpcContext.class).toProvider(
				() -> listenerEventContextTracker.getCurrentContext().getRpcContext());

		TypeLiteral<List<ContextTracker<?>>> trackersType = new TypeLiteral<>() {};
		binder.bind(trackersType).toInstance(allTrackers);
	}



	public List<GrpcContextTrackingExecutor> getExecutors() {
		return Collections.unmodifiableList(executors);
	}

	final List<GrpcContextTrackingExecutor> executors = new LinkedList<>();



	/**
	 * Constructs an instance backed by a new fixed size {@link ThreadPoolExecutor} that uses an
	 * unbound {@link LinkedBlockingQueue} and a new {@link NamedThreadFactory}.
	 * <p>
	 * To avoid {@link OutOfMemoryError}s, an external mechanism that limits maximum number of tasks
	 * (such as a load balancer or a frontend proxy) should be used.</p>
	 */
	public GrpcContextTrackingExecutor newContextTrackingExecutor(String name, int poolSize) {
		var executor = new GrpcContextTrackingExecutor(name, poolSize, allTrackers);
		executors.add(executor);
		return executor;
	}



	/**
	 * Constructs an instance backed by a new fixed size {@link ThreadPoolExecutor} that uses
	 * {@code workQueue}, the default {@link RejectedExecutionHandler} and a new
	 * {@link NamedThreadFactory} named after this executor.
	 * <p>
	 * The default {@link RejectedExecutionHandler} throws a
	 * {@link DetailedRejectedExecutionException} if {@code workQueue} is full or the executor is
	 * shutting down. It
	 * should usually be handled by sending {@link io.grpc.Status#UNAVAILABLE} to the client.</p>
	 */
	public GrpcContextTrackingExecutor newContextTrackingExecutor(
		String name,
		int poolSize,
		BlockingQueue<Runnable> workQueue
	) {
		var executor = new GrpcContextTrackingExecutor(name, poolSize, allTrackers, workQueue);
		executors.add(executor);
		return executor;
	}



	/**
	 * Constructs an instance backed by a new fixed size {@link ThreadPoolExecutor} that uses
	 * {@code workQueue}, {@code rejectionHandler} and a new {@link NamedThreadFactory} named after
	 * this executor.
	 * <p>
	 * The first param of {@code rejectionHandler} is a rejected task: either {@link Runnable} or
	 * {@link Callable} depending whether {@link GrpcContextTrackingExecutor#execute(Runnable)} or
	 * {@link GrpcContextTrackingExecutor#execute(Callable)} was used.</p>
	 * <p>
	 * In order for {@link GrpcContextTrackingExecutor#execute(StreamObserver, Runnable)} to work
	 * properly, the {@code rejectionHandler} must throw a {@link RejectedExecutionException}.</p>
	 */
	public GrpcContextTrackingExecutor newContextTrackingExecutor(
		String name,
		int poolSize,
		BlockingQueue<Runnable> workQueue,
		BiConsumer<Object, ? super GrpcContextTrackingExecutor> rejectionHandler
	) {
		var executor = new GrpcContextTrackingExecutor(
				name, poolSize, allTrackers, workQueue, rejectionHandler);
		executors.add(executor);
		return executor;
	}



	/**
	 * Constructs an instance backed by a new fixed size {@link ThreadPoolExecutor} that uses
	 * {@code workQueue}, {@code rejectionHandler} and {@code threadFactory}.
	 * @see #newContextTrackingExecutor(String, int, BlockingQueue, BiConsumer)
	 */
	public GrpcContextTrackingExecutor newContextTrackingExecutor(
		String name,
		int poolSize,
		BlockingQueue<Runnable> workQueue,
		BiConsumer<Object, GrpcContextTrackingExecutor> rejectionHandler,
		ThreadFactory threadFactory
	) {
		var executor = new GrpcContextTrackingExecutor(
				name, poolSize, allTrackers, workQueue, rejectionHandler, threadFactory);
		executors.add(executor);
		return executor;
	}



	/**
	 * Constructs an instance backed by {@code backingExecutor}. A {@link RejectedExecutionHandler}
	 * of the {@code backingExecutor} will receive a {@link Runnable} that consists of several
	 * layers of wrappers around the original task, use
	 * {@link pl.morgwai.base.guice.scopes.ContextTrackingExecutor#unwrapRejectedTask(Runnable)} to
	 * obtain the original task.
	 * @param poolSize informative only: to be returned by
	 *     {@link pl.morgwai.base.guice.scopes.ContextTrackingExecutor#getPoolSize()}.
	 * @see #newContextTrackingExecutor(String, int, BlockingQueue, BiConsumer)
	 */
	public GrpcContextTrackingExecutor newContextTrackingExecutor(
		String name,
		int poolSize,
		ExecutorService backingExecutor
	) {
		final var executor =
				new GrpcContextTrackingExecutor(name, poolSize, allTrackers, backingExecutor);
		executors.add(executor);
		return executor;
	}



	/**
	 * Shutdowns all executors obtained from this module.
	 */
	public void shutdownAllExecutors() {
		for (var executor: executors) executor.shutdown();
	}



	/**
	 * {@link GrpcContextTrackingExecutor#enforceTermination(long, java.util.concurrent.TimeUnit)
	 * Enforces termination} of all executors obtained from this module.
	 * @return an empty list if all executors were terminated, list of unterminated otherwise.
	 */
	public List<GrpcContextTrackingExecutor> enforceTerminationOfAllExecutors(
		long timeout,
		TimeUnit unit
	) throws InterruptedException {
		return Awaitable.awaitMultiple(
				timeout,
				unit,
				GrpcContextTrackingExecutor::toAwaitableOfEnforcedTermination,
				executors);
	}

	/**
	 * {@link GrpcContextTrackingExecutor#awaitTermination(long, java.util.concurrent.TimeUnit)
	 * Awaits for termination} of all executors obtained from this module.
	 * @return an empty list if all executors were terminated, list of unterminated otherwise.
	 * @see #enforceTerminationOfAllExecutors(long, TimeUnit)
	 */
	public List<GrpcContextTrackingExecutor> awaitTerminationOfAllExecutors(
		long timeout,
		TimeUnit unit
	) throws InterruptedException {
		return Awaitable.awaitMultiple(
			timeout,
			unit,
			GrpcContextTrackingExecutor::toAwaitableOfTermination,
			executors
		);
	}

	/**
	 * {@link GrpcContextTrackingExecutor#awaitTermination() Awaits for termination} of all
	 * executors obtained from this module.
	 * @see #enforceTerminationOfAllExecutors(long, TimeUnit)
	 * @see #awaitTerminationOfAllExecutors(long, TimeUnit)
	 */
	public void awaitTerminationOfAllExecutors() throws InterruptedException {
		for (var executor: executors) executor.awaitTermination();
	}

	/**
	 * Creates {@link Awaitable.WithUnit} of
	 * {@link #enforceTerminationOfAllExecutors(long, TimeUnit)}.
	 */
	public Awaitable.WithUnit toAwaitableOfEnforcedTerminationOfAllExecutors() {
		shutdownAllExecutors();
		return (timeout, unit) -> enforceTerminationOfAllExecutors(timeout, unit).isEmpty();
	}

	/**
	 * Creates {@link Awaitable.WithUnit} of
	 * {@link #awaitTerminationOfAllExecutors(long, TimeUnit)}.
	 */
	public Awaitable.WithUnit toAwaitableOfTerminationOfAllExecutors() {
		shutdownAllExecutors();
		return (timeout, unit) -> awaitTerminationOfAllExecutors(timeout, unit).isEmpty();
	}



	// For internal use by interceptors.
	ListenerEventContext newListenerEventContext(RpcContext rpcContext) {
		return new ListenerEventContext(rpcContext, listenerEventContextTracker);
	}
}
