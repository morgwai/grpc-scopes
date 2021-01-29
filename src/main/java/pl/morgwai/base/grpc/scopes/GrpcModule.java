/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.grpc.scopes;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;

import pl.morgwai.base.guice.scopes.ContextScope;
import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.ContextTrackingExecutor;
import pl.morgwai.base.guice.scopes.Scope;
import pl.morgwai.base.guice.scopes.ThreadLocalContextTracker;



/**
 * gRPC injection <code>Scope</code>s and <code>ContextTracker</code>s.<br/>
 * <br/>
 * <b>NO STATIC:</b> by a common convention, objects such as <code>Scope</code>s are usually stored
 * on <code>static</code> vars. Global context however has a lot of drawbacks. Instead, create just
 * 1 singleton instance in your app initialization code (like <code>main</code> method), pass it
 * (or its members) to your <code>ContextTrackingExecutor</code>s and Guice <code>Module</code>s
 * and then discard.
 */
public class GrpcModule implements Module {



	/**
	 * Allows tracking of the context of a given RPC (<code>ServerCall</code>).
	 */
	public final ContextTracker<RpcContext> rpcContextTracker =
			new ThreadLocalContextTracker<>();

	/**
	 * Allows tracking of the context of a given message from a request stream.
	 * Useful mostly for client streaming or bidirectional streaming calls.
	 */
	public final ContextTracker<MessageContext> messageContextTracker =
			new ThreadLocalContextTracker<>();



	/**
	 * Scopes objects to the context of a given RPC (<code>ServerCall</code>).
	 */
	public final Scope rpcScope =
			new ContextScope<>("RPC_SCOPE", rpcContextTracker);

	/**
	 * Scopes objects to the context of a given message from a given request stream.
	 * Useful mostly for client streaming or bidirectional streaming calls.
	 */
	public final Scope messageScope =
			new ContextScope<>("MESSAGE_SCOPE", messageContextTracker);



	/**
	 * Binds {@link #rpcContextTracker} and {@link #messageContextTracker} for injection.
	 */
	@Override
	public void configure(Binder binder) {
		TypeLiteral<ContextTracker<RpcContext>> rpcContextTrackerType =
				new TypeLiteral<>() {};
		binder.bind(rpcContextTrackerType).toInstance(rpcContextTracker);

		TypeLiteral<ContextTracker<MessageContext>> messageContextTrackerType =
				new TypeLiteral<>() {};
		binder.bind(messageContextTrackerType).toInstance(messageContextTracker);
	}



	/**
	 * Convenience "constructor" for <code>ContextTrackingExecutor</code>. (I really miss method
	 * extensions in Java)
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(String name, int poolSize) {
		return
			new ContextTrackingExecutor(name, poolSize, rpcContextTracker, messageContextTracker);
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
				rpcContextTracker, messageContextTracker);
	}
}
