// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import io.grpc.*;
import io.grpc.ClientCall.Listener;



/**
 * Creates a new {@link ClientRpcContext} for each new RPC ({@link ClientCall}), then also creates
 * and starts tracking a new {@link ListenerEventContext} for each {@link Listener Listener} call.
 * Instance can be obtained from {@link GrpcModule#clientInterceptor}.
 */
public class ClientContextInterceptor implements ClientInterceptor {



	final GrpcModule grpcModule;



	@Override
	public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> interceptCall(
			MethodDescriptor<RequestT, ResponseT> method, CallOptions callOptions, Channel next) {
		return new RpcWrapper<>(next.newCall(method, callOptions));
	}



	class RpcWrapper<RequestT, ResponseT> extends ClientCall<RequestT, ResponseT> {

		final ClientCall<RequestT, ResponseT> wrappedRpc;

		RpcWrapper(ClientCall<RequestT, ResponseT> rpc) { wrappedRpc = rpc; }



		@Override
		public void start(Listener<ResponseT> listener, Metadata requestHeaders) {
			wrappedRpc.start(
				new ListenerWrapper<>(
						listener, grpcModule.newClientRpcContext(wrappedRpc, requestHeaders)),
				requestHeaders
			);
		}



		// below just delegate all ClientCall method calls to wrappedRpc

		@Override
		public void cancel(String message, Throwable cause) { wrappedRpc.cancel(message, cause); }

		@Override public void request(int numMessages) { wrappedRpc.request(numMessages); }

		@Override public void halfClose() { wrappedRpc.halfClose(); }

		@Override public void sendMessage(RequestT message) { wrappedRpc.sendMessage(message); }
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



	public ClientContextInterceptor(GrpcModule grpcModule) { this.grpcModule = grpcModule; }
}
