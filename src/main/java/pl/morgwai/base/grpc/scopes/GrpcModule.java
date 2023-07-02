// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import com.google.inject.*;
import com.google.inject.Module;

import io.grpc.*;

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
	 * {@link #nestingClientInterceptor}. This interceptor will keep nested client RPC contexts
	 * separate from their parent server RPC contexts.
	 * @see ClientInterceptors#intercept(Channel, ClientInterceptor...)
	 */
	public final ClientInterceptor clientInterceptor = new ClientContextInterceptor(this, false);

	/**
	 * All {@link Channel client Channels} must be intercepted either by this interceptor or by
	 * {@link #clientInterceptor}. This interceptor will join together nested client RPC contexts
	 * with their parent server RPC contexts, so that all RPC scoped objects will be shared between
	 * parents and their nested RPCs.
	 * @see ClientInterceptors#intercept(Channel, ClientInterceptor...)
	 */
	public final ClientInterceptor nestingClientInterceptor =
			new ClientContextInterceptor(this, true);



	/**
	 * Singleton of {@link #listenerEventScope}.
	 * {@link #configure(Binder)} binds {@code List<ContextTracker<?>>} to it for use with
	 * {@link ContextTrackingExecutor#getActiveContexts(List)}.
	 */
	public final List<ContextTracker<?>> allTrackers =
			List.of(listenerEventContextTracker);



	/**
	 * Binds  {@link #listenerEventContextTracker} and both contexts for injection.
	 * Binds {@code List<ContextTracker<?>>} to {@link #allTrackers} that contains all trackers for
	 * use with {@link ContextTrackingExecutor#getActiveContexts(List)}.
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



	public List<ContextTrackingExecutor> getExecutors() {
		return Collections.unmodifiableList(executors);
	}

	final List<ContextTrackingExecutor> executors = new LinkedList<>();



	/**
	 * Constructs an executor backed by a new fixed size
	 * {@link java.util.concurrent.ThreadPoolExecutor} that uses a
	 * {@link ContextTrackingExecutor.NamedThreadFactory NamedThreadFactory} and an unbound
	 * {@link java.util.concurrent.LinkedBlockingQueue}.
	 * <p>
	 * To avoid {@link OutOfMemoryError}s, an external mechanism that limits maximum number of tasks
	 * (such as a load balancer or frontend) should be used.</p>
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(String name, int poolSize) {
		var executor = new ContextTrackingExecutor(name, poolSize, allTrackers);
		executors.add(executor);
		return executor;
	}



	/**
	 * Constructs an executor backed by a new fixed size
	 * {@link java.util.concurrent.ThreadPoolExecutor} that uses a
	 * {@link ContextTrackingExecutor.NamedThreadFactory NamedThreadFactory}.
	 * <p>
	 * {@link ContextTrackingExecutor#execute(Runnable)} throws a
	 * {@link java.util.concurrent.RejectedExecutionException} if {@code workQueue} is full. It
	 * should usually be handled by sending {@link io.grpc.Status#UNAVAILABLE} to the client.</p>
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(
		String name,
		int poolSize,
		BlockingQueue<Runnable> workQueue
	) {
		var executor = new ContextTrackingExecutor(name, poolSize, allTrackers, workQueue);
		executors.add(executor);
		return executor;
	}



	/**
	 * Constructs an executor backed by a new fixed size
	 * {@link java.util.concurrent.ThreadPoolExecutor}.
	 * <p>
	 * {@link ContextTrackingExecutor#execute(Runnable)} throws a
	 * {@link java.util.concurrent.RejectedExecutionException} if {@code workQueue} is full. It
	 * should usually be handled by sending {@link io.grpc.Status#UNAVAILABLE} to the client.</p>
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(
		String name,
		int poolSize,
		BlockingQueue<Runnable> workQueue,
		BiConsumer<Object, ContextTrackingExecutor> rejectionHandler
	) {
		var executor = new ContextTrackingExecutor(
				name, poolSize, allTrackers, workQueue, rejectionHandler);
		executors.add(executor);
		return executor;
	}



	public ContextTrackingExecutor newContextTrackingExecutor(
		String name,
		int poolSize,
		BlockingQueue<Runnable> workQueue,
		BiConsumer<Object, ContextTrackingExecutor> rejectionHandler,
		ThreadFactory threadFactory
	) {
		var executor = new ContextTrackingExecutor(
				name, poolSize, allTrackers, workQueue, rejectionHandler, threadFactory);
		executors.add(executor);
		return executor;
	}



	/**
	 * Constructs an executor backed by {@code backingExecutor}.
	 * <p>
	 * <b>NOTE:</b> {@code backingExecutor.execute(task)} must throw
	 * {@link java.util.concurrent.RejectedExecutionException} in case of rejection for
	 * {@link ContextTrackingExecutor#execute(io.grpc.stub.StreamObserver, Runnable)
	 * execute(responseObserver, task)} to work properly.</p>
	 * <p>
	 * {@code poolSize} is informative only, to be returned by
	 * {@link ContextTrackingExecutor#getPoolSize()}.</p>
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(
		String name,
		int poolSize,
		ExecutorService backingExecutor
	) {
		var executor = new ContextTrackingExecutor(name, poolSize, allTrackers, backingExecutor);
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
	 * {@link ContextTrackingExecutor#enforceTermination(long, java.util.concurrent.TimeUnit)
	 * Enforces termination} of all executors obtained from this module.
	 * @return an empty list if all executors were terminated, list of unterminated otherwise.
	 */
	public List<ContextTrackingExecutor> enforceTerminationOfAllExecutors(
		long timeout,
		TimeUnit unit
	) throws InterruptedException {
		return Awaitable.awaitMultiple(
				timeout,
				unit,
				ContextTrackingExecutor::toAwaitableOfEnforcedTermination,
				executors);
	}

	/**
	 * {@link ContextTrackingExecutor#awaitTermination(long, java.util.concurrent.TimeUnit)
	 * Awaits for termination} of all executors obtained from this module.
	 * @return an empty list if all executors were terminated, list of unterminated otherwise.
	 * @see #enforceTerminationOfAllExecutors(long, TimeUnit)
	 */
	public List<ContextTrackingExecutor> awaitTerminationOfAllExecutors(long timeout, TimeUnit unit)
			throws InterruptedException {
		return Awaitable.awaitMultiple(
				timeout,
				unit,
				ContextTrackingExecutor::toAwaitableOfTermination,
				executors);
	}

	/**
	 * {@link ContextTrackingExecutor#awaitTermination() Awaits for termination} of all executors
	 * obtained from this module.
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
