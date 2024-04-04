// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.TrackableContext;



/**
 * Context of a single call to one of the methods of {@link io.grpc.ClientCall.Listener},
 * {@link io.grpc.ServerCall.Listener} and a server listener creation in
 * {@link io.grpc.ServerCallHandler#startCall(io.grpc.ServerCall, io.grpc.Metadata)
 * ServerCallHandler.startCall(...)}.
 * Each such event is executed within a separate new {@code ListenerEventContext} instance.
 *
 * @see GrpcModule#listenerEventScope corresponding Scope
 * @see <a href="https://javadoc.io/doc/pl.morgwai.base/grpc-utils/latest/pl/morgwai/base/grpc/
utils/GrpcServerFlow.html">a simplified overview of relation between methods of
 * Listener and user's request observer</a>
 * @see <a href="https://github.com/grpc/grpc-java/blob/master/stub/src/main/java/io/grpc/stub/
ServerCalls.java">ServerCalls source</a>
 * @see <a href="https://github.com/grpc/grpc-java/blob/master/stub/src/main/java/io/grpc/stub/
ClientCalls.java">ClientCalls source</a>
 */
public class ListenerEventContext extends TrackableContext<ListenerEventContext> {



	public RpcContext getRpcContext() { return  rpcContext; }
	public final RpcContext rpcContext;



	ListenerEventContext(RpcContext rpcContext, ContextTracker<ListenerEventContext> tracker) {
		super(tracker);
		this.rpcContext = rpcContext;
	}
}
