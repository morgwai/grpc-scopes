/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.grpc.scopes;

import io.grpc.ServerCall;

import pl.morgwai.base.guice.scopes.ServerCallContext;



/**
 * A context of a given RPC (<code>ServerCall</code>).
 * Spans over the whole processing of a given RPC from the beginning of invocation of a given
 * remote procedure, across all its messages, until the RPC is closed.
 * Specifically <code>ServerCallHandler.startCall(Metadata)</code> and all methods of the returned
 * <code>Listener</code> are run within a given <code>RpcContext</code>.
 *
 * @see GrpcModule#rpcScope
 * @see ContextInterceptor#interceptCall(ServerCall, io.grpc.Metadata, io.grpc.ServerCallHandler)
 */
public class RpcContext extends ServerCallContext {



	ServerCall<?, ?> rpc;
	public ServerCall<?, ?> getRpc() { return rpc; }

	Object currentMessage;
	public Object getCurrentMessage() { return currentMessage; }



	// for internal use only
	RpcContext(ServerCall<?, ?> rpc) { this.rpc = rpc; }
	void setCurrentMessage(Object currentMessage) { this.currentMessage = currentMessage; }
}
