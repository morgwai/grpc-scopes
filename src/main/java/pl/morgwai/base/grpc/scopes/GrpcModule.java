// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.*;
import java.util.concurrent.*;

import com.google.inject.*;
import com.google.inject.Module;

import io.grpc.*;

import io.grpc.stub.StreamObserver;
import pl.morgwai.base.utils.concurrent.Awaitable;
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
	 * Allows tracking of the {@link ListenerEventContext context of a Listener event}.
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
	 * Singleton of {@link #listenerEventScope}.
	 * {@link #configure(Binder)} binds {@code List<ContextTracker<?>>} to it for use with
	 * {@link ContextTracker#getActiveContexts(List)}.
	 */
	public final List<ContextTracker<?>> allTrackers = List.of(listenerEventContextTracker);



	/**
	 * All gRPC services must be intercepted by this interceptor.
	 * @see io.grpc.ServerInterceptors#intercept(io.grpc.BindableService, java.util.List)
	 */
	public final ServerInterceptor serverInterceptor = new ServerContextInterceptor(this);

	/**
	 * All {@link Channel client Channels} must be intercepted either by this interceptor or by
	 * {@link #nestingClientInterceptor}. This interceptor will keep client RPC contexts separate
	 * even if they were made within some enclosing RPC contexts.
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
	 * Binds  {@link #listenerEventContextTracker} and both contexts for injection.
	 * Binds {@code List<ContextTracker<?>>} to {@link #allTrackers} that contains all trackers for
	 * use with {@link ContextTracker#getActiveContexts(List)}.
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



	/** List of all executors created by this module. */
	public List<GrpcContextTrackingExecutor> getExecutors() {
		return Collections.unmodifiableList(executors);
	}

	final List<GrpcContextTrackingExecutor> executors = new LinkedList<>();



	/**
	 * Constructs a fixed size, context tracking executor that uses an unbound
	 * {@link LinkedBlockingQueue} and a new
	 * {@link pl.morgwai.base.utils.concurrent.NamingThreadFactory} named after this executor.
	 * <p>
	 * To avoid {@link OutOfMemoryError}s, an external mechanism that limits maximum number of tasks
	 * (such as a load balancer or a frontend proxy) should be used.</p>
	 */
	public GrpcContextTrackingExecutor newContextTrackingExecutor(String name, int poolSize) {
		final var executor = new GrpcContextTrackingExecutor(name, allTrackers, poolSize);
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
		final var executor =
				new GrpcContextTrackingExecutor(name, allTrackers, poolSize, queueSize);
		executors.add(executor);
		return executor;
	}

	/**
	 * Constructs a fixed size, context tracking executor that uses {@code workQueue},
	 * {@code rejectionHandler} and a new
	 * {@link pl.morgwai.base.utils.concurrent.NamingThreadFactory} named after this executor.
	 * <p>
	 * {@code rejectionHandler} will receive a task wrapped with a {@link ContextBoundTask}.</p>
	 * <p>
	 * In order for {@link GrpcContextTrackingExecutor#execute(StreamObserver, Runnable)} to work
	 * properly, the {@code rejectionHandler} must throw a {@link RejectedExecutionException}.</p>
	 */
	public GrpcContextTrackingExecutor newContextTrackingExecutor(
		String name,
		int poolSize,
		BlockingQueue<Runnable> workQueue,
		RejectedExecutionHandler rejectionHandler
	) {
		final var executor = new GrpcContextTrackingExecutor(
				name, allTrackers, poolSize, workQueue, rejectionHandler);
		executors.add(executor);
		return executor;
	}

	/**
	 * Constructs a context tracking executor.
	 * @see ThreadPoolExecutor#ThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue,
	 *     ThreadFactory, RejectedExecutionHandler) ThreadPoolExecutor constructor docs for param
	 *     details
	 * @see #newContextTrackingExecutor(String, int, BlockingQueue, RejectedExecutionHandler)
	 *     notes on <code>rejectionHandler</code>
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
			allTrackers,
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



	/** Shutdowns all executors obtained from this module. */
	public void shutdownAllExecutors() {
		for (var executor: executors) executor.shutdown();
	}

	/**
	 * {@link GrpcContextTrackingExecutor#toAwaitableOfEnforcedTermination() Enforces termination}
	 * of all executors obtained from this module.
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
			executors
		);
	}

	/**
	 * {@link GrpcContextTrackingExecutor#toAwaitableOfTermination() Awaits for termination} of all
	 * executors obtained from this module.
	 * @return an empty list if all executors were terminated, list of unterminated otherwise.
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
