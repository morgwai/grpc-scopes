// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.TrackableContext;



/**
 * Context of a single call to one of the methods of either a {@link io.grpc.ClientCall.Listener} or
 * a {@link io.grpc.ServerCall.Listener} or a creation of a {@link io.grpc.ServerCall.Listener} in
 * {@link io.grpc.ServerCallHandler#startCall(io.grpc.ServerCall, io.grpc.Metadata)
 * ServerCallHandler.startCall(...)}.
 * Each such event is {@link #executeWithinSelf(pl.morgwai.base.function.Throwing4Computation)
 * executed} within a separate new {@code ListenerEventContext} instance.
 * <p>
 * In case of servers this means that:</p>
 * <ul>
 *     <li>each call to a method implementing a gRPC procedure,</li>
 *     <li>each callback to a request {@link io.grpc.stub.StreamObserver} returned by any method
 *         implementing a gRPC procedure,</li>
 *     <li>each invocation of a handler registered via a
 *         {@link io.grpc.stub.ServerCallStreamObserver}
 *         ({@link io.grpc.stub.ServerCallStreamObserver#setOnReadyHandler(Runnable) onReady()},
 *         {@link io.grpc.stub.ServerCallStreamObserver#setOnCloseHandler(Runnable) onClose()},
 *         {@link io.grpc.stub.ServerCallStreamObserver#setOnCancelHandler(Runnable) onCancel()})
 *         </li>
 * </ul>
 * <p>
 * ...have a separate {@link GrpcModule#listenerEventScope listenerEventScope}, <b>except</b> the
 * first call to the
 * {@link io.grpc.stub.ServerCallStreamObserver#setOnReadyHandler(Runnable) onReady() handler} of an
 * unary-request {@link io.grpc.ServerCall}, as internally gRPC invokes it in the same
 * {@link io.grpc.ServerCall.Listener Listener} event-handling method as the gRPC method.</p>
 * <p>
 * In case of clients this means that:</p>
 * <ul>
 *     <li>each callback to a response {@link io.grpc.stub.StreamObserver} supplied as an argument
 *         to a stub gRPC method,</li>
 *     <li>each invocation of a
 *        {@link io.grpc.stub.ClientCallStreamObserver#setOnReadyHandler(Runnable)
 *         registered onReady() handler}</li>
 * </ul>
 * <p>
 * ...have a separate {@link GrpcModule#listenerEventScope listenerEventScope}.</p>
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
