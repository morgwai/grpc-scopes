// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.TrackableContext;



/**
 * Context of a single call to one of the methods of either a {@link io.grpc.ClientCall.Listener} or
 * a {@link io.grpc.ServerCall.Listener} or a creation of a {@link io.grpc.ServerCall.Listener} in
 * {@link io.grpc.ServerCallHandler#startCall(io.grpc.ServerCall, io.grpc.Metadata)
 * ServerCallHandler.startCall(...)}.
 * Each such event is {@link #executeWithinSelf(pl.morgwai.base.function.Throwing4Computation)}
 * executed} within a separate new {@code ListenerEventContext} instance.
 * <p>
 * In case of servers this means that:</p>
 * <ul>
 *     <li>all callbacks to request {@link io.grpc.stub.StreamObserver}s returned by methods
 *         implementing RPC procedures,</li>
 *     <li>methods implementing RPC procedures themselves,</li>
 *     <li>all invocations of handlers registered via
 *         {@link io.grpc.stub.ServerCallStreamObserver}s
 *         ({@link io.grpc.stub.ServerCallStreamObserver#setOnReadyHandler(Runnable) onReady()},
 *         {@link io.grpc.stub.ServerCallStreamObserver#setOnCloseHandler(Runnable) onClose()},
 *         {@link io.grpc.stub.ServerCallStreamObserver#setOnCancelHandler(Runnable) onCancel()})
 *         </li>
 * </ul>
 * <p>
 * ...have separate {@link GrpcModule#listenerEventScope listenerEventScope}s, <b>except</b> the
 * first call to
 * {@link io.grpc.stub.ServerCallStreamObserver#setOnReadyHandler(Runnable) onReady() handler} in
 * case of unary requests as it's invoked in the same {@link io.grpc.ServerCall.Listener Listener}
 * event-handling method as the RPC method.</p>
 * <p>
 * In case of clients this means that:</p>
 * <ul>
 *     <li>all callbacks to response {@link io.grpc.stub.StreamObserver}s supplied as arguments to
 *         stub RPC methods,</li>
 *     <li>all invocations of registered
 *         {@link io.grpc.stub.ClientCallStreamObserver onReady() handlers}</li>
 * </ul>
 * <p>
 * ...have separate {@link GrpcModule#listenerEventScope listenerEventScope}s.</p>
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
