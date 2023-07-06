// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import com.google.inject.Key;
import com.google.inject.Provider;
import io.grpc.Metadata;
import pl.morgwai.base.guice.scopes.InjectionContext;



/**
 * Context of either a client or a server RPC.
 * @see ClientRpcContext
 * @see ServerRpcContext
 */
public abstract class RpcContext extends InjectionContext {



	final Metadata requestHeaders;
	public Metadata getRequestHeaders() { return requestHeaders; }



	/**
	 * For nested {@link ClientRpcContext}s
	 * (see {@link ClientRpcContext#provideIfAbsent(Key, Provider)}).
	 */
	<T> T packageProtectedProvideIfAbsent(Key<T> key, Provider<T> provider) {
		return provideIfAbsent(key, provider);
	}



	RpcContext(Metadata requestHeaders) {
		this.requestHeaders = requestHeaders;
	}
}
