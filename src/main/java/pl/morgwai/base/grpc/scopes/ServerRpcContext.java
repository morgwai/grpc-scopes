// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import io.grpc.Metadata;
import io.grpc.ServerCall;



/**
 * Context of a server RPC ({@link io.grpc.ServerCall}).
 * A single instance spans over the whole processing of a given RPC: from the beginning of the
 * invocation of a given remote procedure, across all its messages, until the RPC is closed.
 * Specifically {@link io.grpc.ServerCallHandler#startCall(ServerCall, io.grpc.Metadata)
 * ServerCallHandler.startCall(...)} and all methods of the returned
 * {@link io.grpc.ServerCall.Listener} are executed within the same {@code ServerRpcContext}.
 *
 * @see GrpcModule#rpcScope corresponding Scope
 * @see <a href="https://javadoc.io/doc/pl.morgwai.base/grpc-utils/latest/pl/morgwai/base/grpc/
utils/GrpcServerFlow.html">a simplified overview of relation between methods of
 * Listener and user's request observer</a>
 * @see <a href="https://github.com/grpc/grpc-java/blob/master/stub/src/main/java/io/grpc/stub/
ServerCalls.java">ServerCalls source for detailed info</a>
 */
public class ServerRpcContext extends RpcContext {



	public ServerCall<?, ?> getRpc() { return rpc; }
	public final ServerCall<?, ?> rpc;



	ServerRpcContext(ServerCall<?, ?> rpc, Metadata headers) {
		super(headers);
		this.rpc = rpc;
	}
}
