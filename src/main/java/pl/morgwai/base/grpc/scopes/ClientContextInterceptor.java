// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import io.grpc.*;
import io.grpc.ClientCall.Listener;



/**
 * Creates a new {@link ClientRpcContext} for each new RPC ({@link ClientCall}) and a new
 * {@link ListenerEventContext} for each {@link Listener Listener} call.
 * An Instance can be obtained from {@link GrpcModule#clientInterceptor} or
 * {@link GrpcModule#nestingClientInterceptor}.
 */
public class ClientContextInterceptor implements ClientInterceptor {



	final GrpcModule grpcModule;
	final boolean nesting;



	@Override
	public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> interceptCall(
		MethodDescriptor<RequestT, ResponseT> method,
		CallOptions callOptions,
		Channel channel
	) {
		return new RpcWrapper<>(channel.newCall(method, callOptions));
	}



	class RpcWrapper<RequestT, ResponseT> extends ClientCall<RequestT, ResponseT> {

		final ClientCall<RequestT, ResponseT> wrappedRpc;

		RpcWrapper(ClientCall<RequestT, ResponseT> rpc) { wrappedRpc = rpc; }



		@Override
		public void start(Listener<ResponseT> listener, Metadata requestHeaders) {
			final var parentEventCtx = grpcModule.listenerEventContextTracker.getCurrentContext();
			final var rpcCtx  = (nesting && parentEventCtx != null)
					? new ClientRpcContext(
							wrappedRpc, requestHeaders, parentEventCtx.getRpcContext())
					: new ClientRpcContext(wrappedRpc, requestHeaders);
			wrappedRpc.start(new ListenerWrapper<>(listener, rpcCtx), requestHeaders);
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



	class ListenerWrapper<ResponseT> extends Listener<ResponseT> {

		final Listener<ResponseT> wrappedListener;
		final ClientRpcContext rpcContext;



		ListenerWrapper(Listener<ResponseT> listenerToWrap, ClientRpcContext rpcContext) {
			this.wrappedListener = listenerToWrap;
			this.rpcContext = rpcContext;
		}



		private void executeWithinCtxs(Runnable wrappedListenerCall) {
			grpcModule.newListenerEventContext(rpcContext).executeWithinSelf(wrappedListenerCall);
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
			rpcContext.setStatus(status);
			rpcContext.setTrailers(trailers);
			executeWithinCtxs(() -> wrappedListener.onClose(status, trailers));
		}
	}



	ClientContextInterceptor(GrpcModule grpcModule, boolean nesting) {
		this.grpcModule = grpcModule;
		this.nesting = nesting;
	}
}
