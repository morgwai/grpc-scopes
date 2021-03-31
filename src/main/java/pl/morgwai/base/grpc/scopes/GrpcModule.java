/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.grpc.scopes;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;

import io.grpc.ServerCall;

import pl.morgwai.base.guice.scopes.ContextScope;
import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.ContextTrackingExecutor;
import pl.morgwai.base.guice.scopes.ThreadLocalContextTracker;



/**
 * gRPC Guice <code>Scope</code>s, <code>ContextTracker</code>s and some helper methods.<br/>
 * <br/>
 * <b>NO STATIC:</b> by a common convention, objects such as <code>Scope</code>s are usually stored
 * on <code>static</code> vars. Global context however has a lot of drawbacks. Instead, create just
 * 1 singleton instance in your app initialization code (like <code>main</code> method), pass it
 * (or its members) to your <code>ContextTrackingExecutor</code>s and Guice <code>Module</code>s
 * and then discard.
 */
public class GrpcModule implements Module {



	/**
	 * Allows tracking of the {@link RpcContext context of a given RPC (<code>ServerCall</code>)}.
	 */
	public final ContextTracker<RpcContext> rpcContextTracker =
			new ThreadLocalContextTracker<>();

	/**
	 * Allows tracking of the {@link ListenerCallContext context of a single
	 * <code>ServerCall.Listener</code> call} and as a consequence also of a corresponding request
	 * observer's call.
	 */
	public final ContextTracker<ListenerCallContext> listenerCallContextTracker =
			new ThreadLocalContextTracker<>();



	/**
	 * Scopes objects to the {@link RpcContext context of a given RPC (<code>ServerCall</code>)}.
	 */
	public final Scope rpcScope =
			new ContextScope<>("RPC_SCOPE", rpcContextTracker);

	/**
	 * Scopes objects to the {@link ListenerCallContext context of a given <code>Listener</code>
	 * call} and as a consequence also of a corresponding request observer's call.
	 */
	public final Scope listenerCallScope =
			new ContextScope<>("LISTENER_CALL_SCOPE", listenerCallContextTracker);



	public final ContextInterceptor contextInterceptor = new ContextInterceptor(this);



	/**
	 * Binds {@link #rpcContextTracker} and {@link #listenerCallContextTracker} for injection.
	 */
	@Override
	public void configure(Binder binder) {
		TypeLiteral<ContextTracker<RpcContext>> rpcContextTrackerType =
				new TypeLiteral<>() {};
		binder.bind(rpcContextTrackerType).toInstance(rpcContextTracker);

		TypeLiteral<ContextTracker<ListenerCallContext>> messageContextTrackerType =
				new TypeLiteral<>() {};
		binder.bind(messageContextTrackerType).toInstance(listenerCallContextTracker);
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
	 * Convenience "constructor" for <code>ContextTrackingExecutor</code>. (I really miss method
	 * extensions in Java)
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(
			String name,
			int poolSize,
			BlockingQueue<Runnable> workQueue,
			ThreadFactory threadFactory,
			RejectedExecutionHandler handler) {
		return new ContextTrackingExecutor(name, poolSize, workQueue, threadFactory, handler,
				rpcContextTracker, listenerCallContextTracker);
	}



	RpcContext newRpcContext(ServerCall<?, ?> rpc) {
		return new RpcContext(rpc, rpcContextTracker);
	}

	ListenerCallContext newListenerCallContext(Object message) {
		return new ListenerCallContext(message, listenerCallContextTracker);
	}

	ListenerCallContext newListenerCallContext() {
		return new ListenerCallContext(listenerCallContextTracker);
	}
}
