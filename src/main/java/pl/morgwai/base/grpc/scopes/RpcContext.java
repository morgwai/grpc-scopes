// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

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



	RpcContext(Metadata requestHeaders) {
		this(requestHeaders, null);
	}

	RpcContext(Metadata requestHeaders, RpcContext enclosingCtx) {
		super(enclosingCtx);
		this.requestHeaders = requestHeaders;
	}
}
