// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.*;
import java.util.concurrent.*;

import com.google.inject.*;
import com.google.inject.Module;

import io.grpc.*;

import pl.morgwai.base.concurrent.Awaitable;
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
	 * as a consequence also of the corresponding {@link io.grpc.stub.StreamObserver} call.
	 */
	public final Scope listenerEventScope =
			new ContextScope<>("LISTENER_EVENT_SCOPE", listenerEventContextTracker);



	/**
	 * Scopes objects to the of an RPC (either {@link ServerRpcContext} or
	 * {@link ClientRpcContext}).
	 */
	public final Scope rpcScope = new InducedContextScope<>(
			"RPC_SCOPE", listenerEventContextTracker, ListenerEventContext::getRpcContext);



	/**
	 * {@link io.grpc.ServerInterceptor} that must be installed for all gRPC services that use
	 * {@link #rpcScope} and {@link #listenerEventScope}.
	 * @see io.grpc.ServerInterceptors#intercept(io.grpc.BindableService, java.util.List)
	 */
	public final ServerContextInterceptor serverInterceptor = new ServerContextInterceptor(this);

	/**
	 * {@link ClientInterceptor} that must be installed for all {@link Channel Channels}.
	 * @see ClientInterceptors#intercept(Channel, ClientInterceptor...)
	 */
	public final ClientContextInterceptor clientInterceptor = new ClientContextInterceptor(this);


	/**
	 * Contains {@link #rpcScope} and {@link #listenerEventScope}.
	 * {@link #configure(Binder)} binds {@code List<ContextTracker<?>>} to it for use with
	 * {@link ContextTrackingExecutor#getActiveContexts(List)}.
	 */
	public final List<ContextTracker<?>> allTrackers = List.of(listenerEventContextTracker);



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
			String name, int poolSize, BlockingQueue<Runnable> workQueue) {
		var executor = new ContextTrackingExecutor(name, poolSize, workQueue, allTrackers);
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
		String name, int poolSize, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory
	) {
		var executor =
				new ContextTrackingExecutor(name, poolSize, workQueue, threadFactory, allTrackers);
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
			String name, ExecutorService backingExecutor, int poolSize) {
		var executor = new ContextTrackingExecutor(name, backingExecutor, poolSize, allTrackers);
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
	public List<ContextTrackingExecutor> enforceTerminationOfAllExecutors(long timeoutMillis)
			throws InterruptedException {
		return Awaitable.awaitMultiple(
				timeoutMillis,
				ContextTrackingExecutor::awaitableOfEnforceTermination,
				executors);
	}

	/**
	 * {@link ContextTrackingExecutor#enforceTermination(long, java.util.concurrent.TimeUnit)
	 * Enforces termination} of all executors obtained from this module.
	 * @return an empty list if all executors were terminated, list of unterminated otherwise.
	 */
	public List<ContextTrackingExecutor> enforceTerminationOfAllExecutors(
		long timeout, TimeUnit unit
	) throws InterruptedException {
		return Awaitable.awaitMultiple(
				timeout,
				unit,
				ContextTrackingExecutor::awaitableOfEnforceTermination,
				executors);
	}



	/**
	 * {@link ContextTrackingExecutor#awaitTermination(long, java.util.concurrent.TimeUnit)
	 * Awaits for termination} of all executors obtained from this module.
	 * @return an empty list if all executors were terminated, list of unterminated otherwise.
	 * @see #enforceTerminationOfAllExecutors(long)
	 */
	public List<ContextTrackingExecutor> awaitTerminationOfAllExecutors(long timeoutMillis)
			throws InterruptedException {
		return Awaitable.awaitMultiple(
				timeoutMillis,
				ContextTrackingExecutor::awaitableOfAwaitTermination,
				executors);
	}

	/**
	 * {@link ContextTrackingExecutor#awaitTermination(long, java.util.concurrent.TimeUnit)
	 * Awaits for termination} of all executors obtained from this module.
	 * @return an empty list if all executors were terminated, list of unterminated otherwise.
	 * @see #enforceTerminationOfAllExecutors(long)
	 */
	public List<ContextTrackingExecutor> awaitTerminationOfAllExecutors(long timeout, TimeUnit unit)
			throws InterruptedException {
		return Awaitable.awaitMultiple(
				timeout,
				unit,
				ContextTrackingExecutor::awaitableOfAwaitTermination,
				executors);
	}

	/**
	 * {@link ContextTrackingExecutor#awaitTermination() Awaits for termination} of all executors
	 * obtained from this module.
	 * @see #enforceTerminationOfAllExecutors(long)
	 * @see #awaitTerminationOfAllExecutors(long)
	 */
	public void awaitTerminationOfAllExecutors() throws InterruptedException {
		for (var executor: executors) executor.awaitTermination();
	}



	// For internal use by ServerContextInterceptor.
	ServerRpcContext newServerRpcContext(ServerCall<?, ?> rpc, Metadata requestHeaders) {
		return new ServerRpcContext(rpc, requestHeaders);
	}

	// For internal use by ClientContextInterceptor.
	ClientRpcContext newClientRpcContext(ClientCall<?, ?> rpc, Metadata requestHeaders) {
		return new ClientRpcContext(rpc, requestHeaders);
	}

	// For internal use by interceptors.
	ListenerEventContext newListenerEventContext(RpcContext rpcContext) {
		return new ListenerEventContext(rpcContext, listenerEventContextTracker);
	}
}
