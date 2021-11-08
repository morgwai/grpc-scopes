// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import io.grpc.Metadata;
import io.grpc.ServerCall;

import pl.morgwai.base.guice.scopes.ContextScope;
import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * gRPC Guice {@link Scope}s, {@link ContextTracker}s and some helper methods.
 * <p>
 * <b>NO STATIC:</b> by a common convention, objects such as <code>Scope</code>s are usually stored
 * on <code>static</code> vars. Global context however has a lot of drawbacks. Instead, create just
 * 1 {@code GrpcModule} instance in your app initialization code (for example on a local var in your
 * <code>main</code> method) and then use its member scopes ({@link #rpcScope},
 * {@link #listenerEventScope}) in your Guice {@link Module}s and {@link #contextInterceptor} to
 * build your services.</p>
 */
public class GrpcModule implements Module {



	/**
	 * Allows tracking of the {@link RpcContext context of an RPC (ServerCall)}.
	 */
	public final ContextTracker<RpcContext> rpcContextTracker = new ContextTracker<>();

	/**
	 * Scopes objects to the {@link RpcContext context of an RPC (ServerCall)}.
	 */
	public final Scope rpcScope = new ContextScope<>("RPC_SCOPE", rpcContextTracker);



	/**
	 * Allows tracking of the
	 * {@link ListenerEventContext context of a Listener event} and as a consequence also of the
	 * corresponding request observer call.
	 */
	public final ContextTracker<ListenerEventContext> listenerEventContextTracker =
			new ContextTracker<>();

	/**
	 * Scopes objects to the {@link ListenerEventContext context of a Listener event} and as a
	 * consequence also of the corresponding request observer call.
	 */
	public final Scope listenerEventScope =
			new ContextScope<>("LISTENER_EVENT_SCOPE", listenerEventContextTracker);



	/**
	 * {@link io.grpc.ServerInterceptor} that must be installed for all gRPC services that use
	 * {@link #rpcScope} and {@link #listenerEventScope}.
	 *
	 * @see io.grpc.ServerInterceptors#intercept(io.grpc.BindableService, java.util.List)
	 */
	public final ContextInterceptor contextInterceptor = new ContextInterceptor(this);



	/**
	 * Contains {@link #rpcScope} and {@link #listenerEventScope}.
	 * {@link #configure(Binder)} binds {@code List<ContextTracker<?>>} to it for use with
	 * {@link ContextTrackingExecutor#getActiveContexts(List)}.
	 */
	public final List<ContextTracker<?>> allTrackers =
			List.of(listenerEventContextTracker, rpcContextTracker);



	/**
	 * Binds {@link #rpcContextTracker} and {@link #listenerEventContextTracker} and corresponding
	 * contexts for injection. Binds {@code List<ContextTracker<?>>} to {@link #allTrackers} that
	 * contains all trackers for use with {@link ContextTrackingExecutor#getActiveContexts(List)}.
	 */
	@Override
	public void configure(Binder binder) {
		TypeLiteral<ContextTracker<RpcContext>> rpcContextTrackerType =
				new TypeLiteral<>() {};
		binder.bind(rpcContextTrackerType).toInstance(rpcContextTracker);
		binder.bind(RpcContext.class).toProvider(rpcContextTracker::getCurrentContext);

		TypeLiteral<ContextTracker<ListenerEventContext>> messageContextTrackerType =
				new TypeLiteral<>() {};
		binder.bind(messageContextTrackerType).toInstance(listenerEventContextTracker);
		binder.bind(ListenerEventContext.class).toProvider(
				listenerEventContextTracker::getCurrentContext);

		TypeLiteral<List<ContextTracker<?>>> trackersType = new TypeLiteral<>() {};
		binder.bind(trackersType).toInstance(allTrackers);
	}



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
		return new ContextTrackingExecutor(name, poolSize, allTrackers);
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
			BlockingQueue<Runnable> workQueue) {
		return new ContextTrackingExecutor(name, poolSize, workQueue, allTrackers);
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
			ThreadFactory threadFactory) {
		return new ContextTrackingExecutor(name, poolSize, workQueue, threadFactory, allTrackers);
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
			ExecutorService backingExecutor,
			int poolSize) {
		return new ContextTrackingExecutor(name, backingExecutor, poolSize, allTrackers);
	}



	// For internal use by ContextInterceptor.
	RpcContext newRpcContext(ServerCall<?, ?> rpc, Metadata headers) {
		return new RpcContext(rpc, headers, rpcContextTracker);
	}

	// For internal use by ContextInterceptor.
	ListenerEventContext newListenerEventContext() {
		return new ListenerEventContext(listenerEventContextTracker);
	}
}
