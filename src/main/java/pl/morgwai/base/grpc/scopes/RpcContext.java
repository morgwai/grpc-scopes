/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.grpc.scopes;

import io.grpc.ServerCall;

import pl.morgwai.base.guice.scopes.ServerCallContext;



/**
 * A context of a given RPC (<code>ServerCall</code>).
 * @see GrpcModule
 * @see ContextInterceptor
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
