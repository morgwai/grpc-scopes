// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import com.google.inject.Key;
import com.google.inject.Provider;
import io.grpc.Metadata;
import pl.morgwai.base.guice.scopes.InjectionContext;



/**
 * Context of either a {@link io.grpc.ClientCall client} or a {@link io.grpc.ServerCall server} RPC.
 * {@link GrpcModule#rpcScope Induced} by {@link ListenerEventContext}.
 * @see ClientRpcContext
 * @see ServerRpcContext
 */
public abstract class RpcContext extends InjectionContext {



	public Metadata getRequestHeaders() { return requestHeaders; }
	public final Metadata requestHeaders;



	/**
	 * For nesting {@link ClientRpcContext}s,
	 * see {@link ClientRpcContext#produceIfAbsent(Key, Provider)}.
	 */
	<T> T packageExposedProduceIfAbsent(Key<T> key, Provider<T> producer) {
		return produceIfAbsent(key, producer);
	}



	RpcContext(Metadata requestHeaders) {
		this.requestHeaders = requestHeaders;
	}
}
