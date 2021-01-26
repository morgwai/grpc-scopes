/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.grpc.scopes;

import pl.morgwai.base.guice.scopes.CallContextScope;
import pl.morgwai.base.guice.scopes.CallContextTracker;
import pl.morgwai.base.guice.scopes.Scope;
import pl.morgwai.base.guice.scopes.ThreadLocalCallContextTracker;



/**
 * gRPC injection <code>Scope</code>s and <code>CallContextTracker</code>s.<br/>
 * <code>CallContextTracker</code>s are configured for injection in {@link GrpcModule}.
 */
public interface GrpcScopes {



	/**
	 * Allows tracking of the context of a given RPC (<code>ServerCall</code>).
	 */
	static final CallContextTracker<RpcContext> RPC_CONTEXT_TRACKER =
			new ThreadLocalCallContextTracker<>();

	/**
	 * Allows tracking of the context of a given message from a request stream.
	 * Useful mostly for client or bidirectional streaming calls.
	 */
	static final CallContextTracker<MessageContext> MESSAGE_CONTEXT_TRACKER =
			new ThreadLocalCallContextTracker<>();



	/**
	 * Scopes objects to the context of a given RPC (<code>ServerCall</code>).
	 */
	static final Scope RPC_SCOPE =
			new CallContextScope<>("RPC_SCOPE", RPC_CONTEXT_TRACKER);

	/**
	 * Scopes objects to the context of a given message from a given request stream.
	 * Useful mostly for client or bidirectional streaming calls.
	 */
	static final Scope MESSAGE_SCOPE =
			new CallContextScope<>("MESSAGE_SCOPE", MESSAGE_CONTEXT_TRACKER);
}
