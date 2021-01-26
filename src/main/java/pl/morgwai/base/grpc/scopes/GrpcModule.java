/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.grpc.scopes;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;

import pl.morgwai.base.guice.scopes.CallContextTracker;



/**
 * Configures injections of <code>CallContextTracker</code>s from {@link GrpcScopes}.
 */
public class GrpcModule implements Module {



	@Override
	public void configure(Binder binder) {
		TypeLiteral<CallContextTracker<RpcContext>> rpcContextTrackerType =
				new TypeLiteral<>() {};
		binder.bind(rpcContextTrackerType).toInstance(GrpcScopes.RPC_CONTEXT_TRACKER);

		TypeLiteral<CallContextTracker<MessageContext>> messageContextTrackerType =
				new TypeLiteral<>() {};
		binder.bind(messageContextTrackerType).toInstance(GrpcScopes.MESSAGE_CONTEXT_TRACKER);
	}
}
