// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import io.grpc.Metadata;
import io.grpc.ServerCall;

import pl.morgwai.base.guice.scopes.ContextScope;
import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.ContextTrackingExecutor;



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
	 * Allows tracking of the {@link RpcContext context of a given RPC (<code>ServerCall</code>)}.
	 */
	public final ContextTracker<RpcContext> rpcContextTracker = new ContextTracker<>();

	/**
	 * Scopes objects to the {@link RpcContext context of a given RPC (<code>ServerCall</code>)}.
	 */
	public final Scope rpcScope = new ContextScope<>("RPC_SCOPE", rpcContextTracker);



	/**
	 * Allows tracking of the {@link ListenerEventContext context of a single
	 * <code>ServerCall.Listener</code> call} and as a consequence also of a corresponding request
	 * observer's call.
	 */
	public final ContextTracker<ListenerEventContext> listenerEventContextTracker =
			new ContextTracker<>();

	/**
	 * Scopes objects to the {@link ListenerEventContext context of a given <code>Listener</code>
	 * call} and as a consequence also of a corresponding request observer's call.
	 */
	public final Scope listenerEventScope =
			new ContextScope<>("LISTENER_EVENT_SCOPE", listenerEventContextTracker);



	/**
	 * {@link io.grpc.ServerInterceptor} that must be installed for all gRPC services that use
	 * {@link #rpcScope} and {@link #listenerEventScope}.
	 */
	public final ContextInterceptor contextInterceptor = new ContextInterceptor(this);



	/**
	 * Binds {@link #rpcContextTracker} and {@link #listenerEventContextTracker} and corresponding
	 * contexts for injection. Binds {@code ContextTracker<?>[]} to instance containing all
	 * trackers for use with {@link ContextTrackingExecutor#getActiveContexts(ContextTracker...)}.
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
		binder.bind(trackerArrayType).toInstance(trackers);
	}

	public final ContextTracker<?>[] trackers = {listenerEventContextTracker, rpcContextTracker};



	/**
	 * Convenience "constructor" for {@link ContextTrackingExecutor}. (I really miss method
	 * extensions in Java)
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(String name, int poolSize) {
		return new ContextTrackingExecutor(
				name, poolSize, rpcContextTracker, listenerEventContextTracker);
	}



	/**
	 * Convenience "constructor" for {@link ContextTrackingExecutor}.
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(
			String name,
			int poolSize,
			BlockingQueue<Runnable> workQueue) {
		return new ContextTrackingExecutor(
				name, poolSize, workQueue, rpcContextTracker, listenerEventContextTracker);
	}



	/**
	 * Convenience "constructor" for {@link ContextTrackingExecutor}.
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(
			String name,
			int corePoolSize,
			int maximumPoolSize,
			long keepAliveTime,
			TimeUnit unit,
			BlockingQueue<Runnable> workQueue,
			ThreadFactory threadFactory,
			RejectedExecutionHandler handler,
			ContextTracker<?>... trackers) {
		return new ContextTrackingExecutor(
				name,
				corePoolSize,
				maximumPoolSize,
				keepAliveTime,
				unit,
				workQueue,
				threadFactory,
				handler,
				rpcContextTracker, listenerEventContextTracker);
	}



	RpcContext newRpcContext(ServerCall<?, ?> rpc, Metadata headers) {
		return new RpcContext(rpc, headers, rpcContextTracker);
	}

	ListenerEventContext newListenerEventContext() {
		return new ListenerEventContext(listenerEventContextTracker);
	}
}
