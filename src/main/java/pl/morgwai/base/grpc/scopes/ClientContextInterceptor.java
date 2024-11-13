// Copyright 2022 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import io.grpc.*;
import io.grpc.ClientCall.Listener;
import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Creates a new {@link ClientRpcContext} for each new RPC ({@link ClientCall}) and a new
 * {@link ListenerEventContext} for each {@link Listener Listener} call.
 * An Instance can be obtained from {@link GrpcModule#clientInterceptor} or
 * {@link GrpcModule#nestingClientInterceptor}.
 */
public class ClientContextInterceptor implements ClientInterceptor {



	final ContextTracker<ListenerEventContext> ctxTracker;
	final boolean nesting;



	ClientContextInterceptor(ContextTracker<ListenerEventContext> ctxTracker, boolean nesting) {
		this.ctxTracker = ctxTracker;
		this.nesting = nesting;
	}



	@Override
	public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> interceptCall(
		MethodDescriptor<RequestT, ResponseT> method,
		CallOptions callOptions,
		Channel channel
	) {
		return new RpcProxy<>(channel.newCall(method, callOptions));
	}



	/**
	 * Upon {@link #start(Listener, Metadata) start of the wrapped RPC} creates its
	 * {@link ClientRpcContext Context} and wraps its {@link Listener} with a {@link ListenerProxy}.
	 */
	class RpcProxy<RequestT, ResponseT> extends ClientCall<RequestT, ResponseT> {

		final ClientCall<RequestT, ResponseT> wrappedRpc;



		RpcProxy(ClientCall<RequestT, ResponseT> rpcToWrap) {
			this.wrappedRpc = rpcToWrap;
		}



		@Override
		public void start(Listener<ResponseT> listener, Metadata requestHeaders) {
			final var enclosingEventCtx = ctxTracker.getCurrentContext();
			final var rpcCtx  = (nesting && enclosingEventCtx != null)
				? new ClientRpcContext(wrappedRpc, requestHeaders, enclosingEventCtx.rpcContext)
				: new ClientRpcContext(wrappedRpc, requestHeaders);
			wrappedRpc.start(new ListenerProxy<>(listener, rpcCtx), requestHeaders);
		}



		// below just delegate all ClientCall method calls to wrappedRpc

		@Override public void cancel(String message, Throwable cause) {
			wrappedRpc.cancel(message, cause);
		}

		@Override public void request(int numMessages) {
			wrappedRpc.request(numMessages);
		}

		@Override public void halfClose() {
			wrappedRpc.halfClose();
		}

		@Override public void sendMessage(RequestT message) {
			wrappedRpc.sendMessage(message);
		}
	}



	/**
	 * Executes each method of the wrapped {@link Listener} within a new
	 * {@link ListenerEventContext}.
	 */
	class ListenerProxy<ResponseT> extends Listener<ResponseT> {

		final Listener<ResponseT> wrappedListener;
		final ClientRpcContext rpcContext;



		ListenerProxy(Listener<ResponseT> listenerToWrap, ClientRpcContext rpcContext) {
			this.wrappedListener = listenerToWrap;
			this.rpcContext = rpcContext;
		}



		private void executeWithinCtxs(Runnable wrappedListenerCall) {
			new ListenerEventContext(rpcContext, ctxTracker).executeWithinSelf(wrappedListenerCall);
		}



		@Override public void onHeaders(Metadata responseHeaders) {
			rpcContext.setResponseHeaders(responseHeaders);
			executeWithinCtxs(() -> wrappedListener.onHeaders(responseHeaders));
		}



		@Override public void onMessage(ResponseT message) {
			executeWithinCtxs(() -> wrappedListener.onMessage(message));
		}



		@Override public void onReady() {
			executeWithinCtxs(wrappedListener::onReady);
		}



		@Override public void onClose(Status status, Metadata trailers) {
			rpcContext.setStatusAndTrailers(status, trailers);
			executeWithinCtxs(() -> wrappedListener.onClose(status, trailers));
		}
	}
}
