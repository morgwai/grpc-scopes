// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import io.grpc.Metadata;
import io.grpc.ServerCall;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.ServerSideContext;



/**
 * Context of a given RPC ({@link io.grpc.ServerCall}).
 * A single instance spans over the whole processing of a given RPC: from the beginning of the
 * invocation of a given remote procedure, across all its messages, until the RPC is closed.
 * Specifically {@link io.grpc.ServerCallHandler#startCall(ServerCall, io.grpc.Metadata)} and all
 * methods of the returned {@link io.grpc.ServerCall.Listener} are executed within the same
 * <code>RpcContext</code>.
 *
 * @see GrpcModule#rpcScope corresponding <code>Scope</code>
 * @see <a href="https://gist.github.com/morgwai/6967bcf51b8ba586847c7f1922c99b88">a simplified
 *      overview of relation between methods of <code>Listener</code> and user code</a>
 * @see <a href="https://github.com/grpc/grpc-java/blob/master/stub/src/main/java/io/grpc/stub/
ServerCalls.java">ServerCalls source for detailed info</a>
 */
public class RpcContext extends ServerSideContext<RpcContext> {



	final ServerCall<?, ?> rpc;
	public ServerCall<?, ?> getRpc() { return rpc; }

	final Metadata headers;
	public Metadata getHeaders() { return headers; }



	RpcContext(ServerCall<?, ?> rpc, Metadata headers, ContextTracker<RpcContext> tracker) {
		super(tracker);
		this.rpc = rpc;
		this.headers = headers;
	}
}
