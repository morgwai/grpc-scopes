/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.grpc.scopes;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;

import pl.morgwai.base.guice.scopes.CallContextScope;
import pl.morgwai.base.guice.scopes.CallContextTracker;
import pl.morgwai.base.guice.scopes.Scope;
import pl.morgwai.base.guice.scopes.ThreadLocalCallContextTracker;



/**
 * gRPC injection <code>Scope</code>s and <code>CallContextTracker</code>s.<br/>
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
	public final CallContextTracker<RpcContext> rpcContextTracker =
			new ThreadLocalCallContextTracker<>();

	/**
	 * Allows tracking of the context of a given message from a request stream.
	 * Useful mostly for client streaming or bidirectional streaming calls.
	 */
	public final CallContextTracker<MessageContext> messageContextTracker =
			new ThreadLocalCallContextTracker<>();



	/**
	 * Scopes objects to the context of a given RPC (<code>ServerCall</code>).
	 */
	public final Scope rpcScope =
			new CallContextScope<>("RPC_SCOPE", rpcContextTracker);

	/**
	 * Scopes objects to the context of a given message from a given request stream.
	 * Useful mostly for client streaming or bidirectional streaming calls.
	 */
	public final Scope messageScope =
			new CallContextScope<>("MESSAGE_SCOPE", messageContextTracker);



	/**
	 * Binds {@link #rpcContextTracker} and {@link #messageContextTracker} for injection.
	 */
	@Override
	public void configure(Binder binder) {
		TypeLiteral<CallContextTracker<RpcContext>> rpcContextTrackerType =
				new TypeLiteral<>() {};
		binder.bind(rpcContextTrackerType).toInstance(rpcContextTracker);

		TypeLiteral<CallContextTracker<MessageContext>> messageContextTrackerType =
				new TypeLiteral<>() {};
		binder.bind(messageContextTrackerType).toInstance(messageContextTracker);
	}
}
