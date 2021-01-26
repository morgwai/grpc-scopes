/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.grpc.scopes;

import io.grpc.ServerCall;

import pl.morgwai.base.guice.scopes.CallContext;



/**
 * A <code>CallContext</code> of a given RPC (<code>ServerCall</code>).
 * @see GrpcScopes
 * @see ContextInterceptor
 */
public class RpcContext extends CallContext {



	ServerCall<?, ?> rpc;
	public ServerCall<?, ?> getRpc() { return rpc; }

	Object currentMessage;
	public Object getCurrentMessage() { return currentMessage; }



	RpcContext(ServerCall<?, ?> rpc) { this.rpc = rpc; }
	void setCurrentMessage(Object currentMessage) { this.currentMessage = currentMessage; }
}
