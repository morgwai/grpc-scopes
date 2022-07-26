// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import io.grpc.ClientCall;
import io.grpc.Metadata;



/**
 * Context of a given client RPC ({@link ClientCall}).
 * A single instance spans over the lifetime of the response stream.
 * Specifically all methods of {@link io.grpc.ClientCall.Listener} are executed within the same
 * <code>ClientRpcContext</code>.
 *
 * @see GrpcModule#rpcScope corresponding <code>Scope</code>
 * @see <a href="https://github.com/grpc/grpc-java/blob/master/stub/src/main/java/io/grpc/stub/
ClientCalls.java">ClientCalls source for detailed info</a>
 */
public class ClientRpcContext extends RpcContext {



	public ClientCall<?, ?> getRpc() { return rpc; }
	final ClientCall<?, ?> rpc;

	public Metadata getResponseHeaders() { return responseHeaders; }
	void setResponseHeaders(Metadata responseHeaders) { this.responseHeaders = responseHeaders; }
	Metadata responseHeaders;



	ClientRpcContext(ClientCall<?, ?> rpc, Metadata requestHeaders) {
		super(requestHeaders);
		this.rpc = rpc;
	}
}