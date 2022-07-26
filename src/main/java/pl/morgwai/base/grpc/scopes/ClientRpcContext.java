// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.Optional;

import com.google.inject.Key;
import com.google.inject.Provider;
import io.grpc.*;



/**
 * Context of a client RPC ({@link ClientCall}).
 * A single instance spans over the lifetime of the response stream.
 * Specifically all methods of {@link io.grpc.ClientCall.Listener} are executed within the same
 * <code>ClientRpcContext</code>.
 *
 * @see GrpcModule#rpcScope corresponding <code>Scope</code>
 * @see <a href="https://github.com/grpc/grpc-java/blob/master/stub/src/main/java/io/grpc/stub/
ClientCalls.java">ClientCalls source for detailed info</a>
 */
public class ClientRpcContext extends RpcContext {



	public ClientCall<?, ?> getRpc() { return rpc; }
	final ClientCall<?, ?> rpc;

	public Metadata getResponseHeaders() { return responseHeaders; }
	void setResponseHeaders(Metadata responseHeaders) { this.responseHeaders = responseHeaders; }
	Metadata responseHeaders;

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
	void setTrailers(Metadata trailers) { this.trailers = trailers; }
	Metadata trailers;

	/**
	 * Final status sent by the server upon the RPC completion. This will be {@code empty()} for
	 * most of the RPC lifetime similarly to {@link #getTrailers()}.
	 */
	public Optional<Status> getStatus() { return Optional.ofNullable(status); }
	public void setStatus(Status status) { this.status = status; }
	Status status;

	final ServerRpcContext parentCtx;



	@Override
	public <T> T provideIfAbsent(Key<T> key, Provider<T> provider) {
		if (parentCtx == null) {
			return super.provideIfAbsent(key, provider);
		} else {
			return parentCtx.provideIfAbsent(key, provider);
		}
	}



	ClientRpcContext(ClientCall<?, ?> rpc, Metadata requestHeaders) {
		super(requestHeaders);
		this.rpc = rpc;
		parentCtx = null;
	}



	ClientRpcContext(ClientCall<?, ?> rpc, Metadata requestHeaders, ServerRpcContext parentCtx) {
		super(requestHeaders);
		this.rpc = rpc;
		this.parentCtx = parentCtx;
	}
}
