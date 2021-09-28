// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.stub.StreamObserver;

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
	 * Binds {@link #rpcContextTracker} and {@link #listenerEventContextTracker} and corresponding
	 * contexts for injection. Binds {@code ContextTracker<?>[]} to {@link #allTrackers} that
	 * contains all trackers for use with
	 * {@link ContextTrackingExecutor#getActiveContexts(ContextTracker...)}.
	 */
	@Override
	public void configure(Binder binder) {
		TypeLiteral<ContextTracker<RpcContext>> rpcContextTrackerType =
				new TypeLiteral<>() {};
		binder.bind(rpcContextTrackerType).toInstance(rpcContextTracker);
		binder.bind(RpcContext.class).toProvider(
				() -> rpcContextTracker.getCurrentContext());

		TypeLiteral<ContextTracker<ListenerEventContext>> messageContextTrackerType =
				new TypeLiteral<>() {};
		binder.bind(messageContextTrackerType).toInstance(listenerEventContextTracker);
		binder.bind(ListenerEventContext.class).toProvider(
				() -> listenerEventContextTracker.getCurrentContext());

		TypeLiteral<ContextTracker<?>[]> trackerArrayType = new TypeLiteral<>() {};
		binder.bind(trackerArrayType).toInstance(allTrackers);
	}

	/**
	 * Contains all trackers. {@link #configure(Binder)} binds {@code ContextTracker<?>[]} to it
	 * for use with {@link ContextTrackingExecutor#getActiveContexts(ContextTracker...)}.
	 */
	public final ContextTracker<?>[] allTrackers = {listenerEventContextTracker, rpcContextTracker};



	/**
	 * Convenience "constructor" for {@link ContextTrackingExecutor}. (I really miss method
	 * extensions in Java).
	 *
	 * @see ContextTrackingExecutor#ContextTrackingExecutor(String, int, ContextTracker...)
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(String name, int poolSize) {
		return new ContextTrackingExecutor(name, poolSize, allTrackers);
	}



	/**
	 * Convenience "constructor" for {@link ContextTrackingExecutor}.
	 *
	 * @see ContextTrackingExecutor#ContextTrackingExecutor(String, int, BlockingQueue,
	 * ContextTracker...)
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(
			String name,
			int poolSize,
			BlockingQueue<Runnable> workQueue) {
		return new ContextTrackingExecutor(name, poolSize, workQueue, allTrackers);
	}



	/**
	 * Convenience "constructor" for {@link ContextTrackingExecutor}.
	 *
	 * @see ContextTrackingExecutor#ContextTrackingExecutor(String, int, BlockingQueue,
	 * ThreadFactory, ContextTracker...)
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(
			String name,
			int poolSize,
			BlockingQueue<Runnable> workQueue,
			ThreadFactory threadFactory,
			ContextTracker<?>... trackers) {
		return new ContextTrackingExecutor(name, poolSize, workQueue, threadFactory, allTrackers);
	}



	/**
	 * Convenience "constructor" for {@link ContextTrackingExecutor}.
	 * <p>
	 * <b>NOTE:</b> {@code backingExecutor.execute(task)} must throw
	 * {@link RejectedExecutionException} in case of rejection for
	 * {@link #execute(StreamObserver, Runnable)} to work properly.</p>
	 *
	 * @see pl.morgwai.base.guice.scopes.ContextTrackingExecutor#ContextTrackingExecutor(String,
	 * ExecutorService, int, ContextTracker...)
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(
			String name,
			ExecutorService backingExecutor,
			int poolSize,
			ContextTracker<?>... trackers) {
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
