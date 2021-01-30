/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.grpc.scopes;

import io.grpc.ServerCall;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.ServerCallContext;



/**
 * Context of a given RPC (<code>ServerCall</code>).
 * A single instance spans over the whole processing of a given RPC: from the beginning of the
 * invocation of a given remote procedure, across all its messages, until the RPC is closed.
 * Specifically <code>ServerCallHandler.startCall(Metadata)</code> and all methods of the returned
 * <code>Listener</code> are executed within a given <code>RpcContext</code>.
 *
 * @see GrpcModule#rpcScope corresponding <code>Scope</code>
 * @see io.grpc.stub.ServerCalls <code>io.grpc.stub.ServerCalls</code> for relation between
 *      method's of <code>Listener</code> and user code
 */
public class RpcContext extends ServerCallContext<RpcContext> {



	ServerCall<?, ?> rpc;
	public ServerCall<?, ?> getRpc() { return rpc; }

	Object currentMessage;
	public Object getCurrentMessage() { return currentMessage; }



	RpcContext(ServerCall<?, ?> rpc, ContextTracker<RpcContext> tracker) {
		super(tracker);
		this.rpc = rpc;
	}



	void setCurrentMessage(Object currentMessage) { this.currentMessage = currentMessage; }
}
