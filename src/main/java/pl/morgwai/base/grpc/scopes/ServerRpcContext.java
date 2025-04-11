// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import io.grpc.Metadata;
import io.grpc.ServerCall;



/**
 * Context of a server RPC ({@link io.grpc.ServerCall}).
 * A single instance spans over the whole processing of its RPC: from the invocation of the gRPC
 * procedure, across all its messages, until the RPC is
 * {@link io.grpc.stub.ServerCallStreamObserver#setOnCloseHandler(Runnable) closed} or
 * {@link io.grpc.stub.ServerCallStreamObserver#setOnCancelHandler(Runnable) canceled}.
 * <p>
 * Specifically, the call to
 * {@link io.grpc.ServerCallHandler#startCall(ServerCall, io.grpc.Metadata)
 * ServerCallHandler.startCall(...)} and all calls to methods of the returned
 * {@link io.grpc.ServerCall.Listener} are executed within the same {@code ServerRpcContext}. This
 * means that:</p>
 * <ul>
 *     <li>the call to the method implementing the gRPC procedure,</li>
 *     <li>all callbacks to the request {@link io.grpc.stub.StreamObserver} returned by this call,
 *         </li>
 *     <li>all invocations of handlers registered via this call's
 *         {@link io.grpc.stub.ServerCallStreamObserver}
 *         ({@link io.grpc.stub.ServerCallStreamObserver#setOnReadyHandler(Runnable) onReady()},
 *         {@link io.grpc.stub.ServerCallStreamObserver#setOnCloseHandler(Runnable) onClose()},
 *         {@link io.grpc.stub.ServerCallStreamObserver#setOnCancelHandler(Runnable) onCancel()})
 *         </li>
 * </ul>
 * <p>
 * ...all share the same {@link GrpcModule#rpcScope rpcScope}.</p>
 * @see <a href="https://javadoc.io/doc/pl.morgwai.base/grpc-utils/latest/pl/morgwai/base/grpc/
utils/GrpcServerFlow.html">an overview of relation between Listner methods and user-created request
 * StreamObserver methods</a>
 * @see <a href="https://github.com/grpc/grpc-java/blob/master/stub/src/main/java/io/grpc/stub/
ServerCalls.java">ServerCalls source</a>
 */
public class ServerRpcContext extends RpcContext {



	public ServerCall<?, ?> getRpc() { return rpc; }
	public final ServerCall<?, ?> rpc;



	ServerRpcContext(ServerCall<?, ?> rpc, Metadata headers) {
		super(headers);
		this.rpc = rpc;
	}
}
