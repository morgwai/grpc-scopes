/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.grpc.scopes;

import io.grpc.ServerCall;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.ServerSideContext;



/**
 * Context of a given RPC (<code>ServerCall</code>).
 * A single instance spans over the whole processing of a given RPC: from the beginning of the
 * invocation of a given remote procedure, across all its messages, until the RPC is closed.
 * Specifically <code>ServerCallHandler.startCall(Metadata)</code> and all methods of the returned
 * <code>Listener</code> are executed within a given <code>RpcContext</code>.
 *
 * @see GrpcModule#rpcScope corresponding <code>Scope</code>
 * @see <a href="https://grpc.github.io/grpc-java/javadoc/io/grpc/stub/ServerCalls.html">
 *      <code>ServerCalls</code> for relation between method's of <code>Listener</code> and user
 *      code</a>
 */
public class RpcContext extends ServerSideContext<RpcContext> {



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
