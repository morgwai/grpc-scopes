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
 * {@link #listenerCallScope}) in your Guice {@link Module}s and {@link #contextInterceptor} to
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
	 * Allows tracking of the {@link ListenerCallContext context of a single
	 * <code>ServerCall.Listener</code> call} and as a consequence also of a corresponding request
	 * observer's call.
	 */
	public final ContextTracker<ListenerCallContext> listenerCallContextTracker =
			new ContextTracker<>();

	/**
	 * Scopes objects to the {@link ListenerCallContext context of a given <code>Listener</code>
	 * call} and as a consequence also of a corresponding request observer's call.
	 */
	public final Scope listenerCallScope =
			new ContextScope<>("LISTENER_CALL_SCOPE", listenerCallContextTracker);



	/**
	 * <code>ServerInterceptor</code> that must be installed for all gRPC services that use
	 * {@link #rpcScope} and {@link #listenerCallScope}.
	 */
	public final ContextInterceptor contextInterceptor = new ContextInterceptor(this);



	/**
	 * Binds {@link #rpcContextTracker} and {@link #listenerCallContextTracker} and corresponding
	 * contexts for injection.
	 */
	@Override
	public void configure(Binder binder) {
		TypeLiteral<ContextTracker<RpcContext>> rpcContextTrackerType =
				new TypeLiteral<>() {};
		binder.bind(rpcContextTrackerType).toInstance(rpcContextTracker);
		binder.bind(RpcContext.class).toProvider(
				() -> rpcContextTracker.getCurrentContext());

		TypeLiteral<ContextTracker<ListenerCallContext>> messageContextTrackerType =
				new TypeLiteral<>() {};
		binder.bind(messageContextTrackerType).toInstance(listenerCallContextTracker);
		binder.bind(ListenerCallContext.class).toProvider(
				() -> listenerCallContextTracker.getCurrentContext());
	}



	/**
	 * Convenience "constructor" for <code>ContextTrackingExecutor</code>. (I really miss method
	 * extensions in Java)
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(String name, int poolSize) {
		return new ContextTrackingExecutor(
				name, poolSize, rpcContextTracker, listenerCallContextTracker);
	}



	/**
	 * Convenience "constructor" for <code>ContextTrackingExecutor</code>.
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(
			String name,
			int poolSize,
			BlockingQueue<Runnable> workQueue) {
		return new ContextTrackingExecutor(
				name, poolSize, workQueue, rpcContextTracker, listenerCallContextTracker);
	}



	/**
	 * Convenience "constructor" for <code>ContextTrackingExecutor</code>.
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
				rpcContextTracker, listenerCallContextTracker);
	}



	RpcContext newRpcContext(ServerCall<?, ?> rpc) {
		return new RpcContext(rpc, rpcContextTracker);
	}

	ListenerCallContext newListenerCallContext() {
		return new ListenerCallContext(listenerCallContextTracker);
	}
}
