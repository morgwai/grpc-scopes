// Copyright 2022 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.Optional;

import io.grpc.*;



/**
 * Context of a client RPC ({@link ClientCall}).
 * A single instance spans over the lifetime of the corresponding response stream.
 * Specifically, all methods of a single {@link ClientCall.Listener} are executed within the same
 * {@code ClientRpcContext}.
 * <p>
 * This means that:</p>
 * <ul>
 *     <li>all callbacks to the response {@link io.grpc.stub.StreamObserver} supplied as an argument
 *         to this given call of the stub gRPC method,</li>
 *     <li>all invocations of this call's
 *         {@link io.grpc.stub.ClientCallStreamObserver#setOnReadyHandler(Runnable)
 *         registered onReady() handler}</li>
 * </ul>
 * <p>
 * ...all share the same {@link GrpcModule#rpcScope rpcScope}.</p>
 * @see <a href="https://github.com/grpc/grpc-java/blob/master/stub/src/main/java/io/grpc/stub/
ClientCalls.java">ClientCalls source</a>
 */
public class ClientRpcContext extends RpcContext {



	public ClientCall<?, ?> getRpc() { return rpc; }
	public final ClientCall<?, ?> rpc;

	public Metadata getResponseHeaders() { return responseHeaders; }
	private Metadata responseHeaders;

	/**
	 * Trailing metadata sent by the server upon the RPC completion. This will be {@code empty()}
	 * for most of the RPC lifetime:<ul>
	 *     <li>It is guaranteed to be not {@code empty()} only in
	 *             {@link io.grpc.stub.StreamObserver#onCompleted()}.</li>
	 *     <li>In {@link io.grpc.stub.StreamObserver#onNext(Object)} it will always be
	 *             {@code empty()}.</li>
	 *     <li>In {@link io.grpc.stub.StreamObserver#onError(Throwable)} it may be either.</li>
	 * </ul>
	 */
	public Optional<Metadata> getTrailers() { return Optional.ofNullable(trailers); }
	private Metadata trailers;

	/**
	 * Final status sent by the server upon the RPC completion. This will be {@code empty()} for
	 * most of the RPC lifetime similarly to {@link #getTrailers()}.
	 */
	public Optional<Status> getStatus() { return Optional.ofNullable(status); }
	private Status status;



	/** Called by {@link ClientContextInterceptor.ListenerProxy#onHeaders(Metadata)}. */
	void setResponseHeaders(Metadata responseHeaders) {
		if (this.responseHeaders != null) throw new IllegalStateException("headers already set");
		this.responseHeaders = responseHeaders;
	}



	/** Called by {@link ClientContextInterceptor.ListenerProxy#onClose(Status, Metadata)}. */
	void setStatusAndTrailers(Status status, Metadata trailers) {
		if (this.status != null) throw new IllegalStateException("status already set");
		this.status = status;
		this.trailers = trailers;
	}



	/** Constructor for nested ctxs (see {@link GrpcModule#nestingClientInterceptor}). */
	ClientRpcContext(ClientCall<?, ?> rpc, Metadata requestHeaders, RpcContext enclosingCtx) {
		super(requestHeaders, enclosingCtx);
		this.rpc = rpc;
	}

	/** Constructor for non-nested ctxs (see {@link GrpcModule#clientInterceptor}). */
	ClientRpcContext(ClientCall<?, ?> rpc, Metadata requestHeaders) {
		this(rpc, requestHeaders, null);
	}
}
