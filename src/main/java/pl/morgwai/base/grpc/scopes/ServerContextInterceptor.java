// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import io.grpc.*;
import io.grpc.ServerCall.Listener;



/**
 * Creates a new {@link ServerRpcContext} for each new RPC ({@link ServerCall}) and a new
 * {@link ListenerEventContext} for each {@link Listener Listener} call and creation.
 * An instance can be obtained from {@link GrpcModule#serverInterceptor}.
 */
public class ServerContextInterceptor implements ServerInterceptor {



	final GrpcModule grpcModule;



	@Override
	public <RequestT, ResponseT> Listener<RequestT> interceptCall(
		ServerCall<RequestT, ResponseT> call,
		Metadata headers,
		ServerCallHandler<RequestT, ResponseT> handler
	) {
		final ServerRpcContext rpcContext = new ServerRpcContext(call, headers);
		try {
			final var listener = grpcModule.newListenerEventContext(rpcContext).executeWithinSelf(
					() -> handler.startCall(call, headers));
			return new ListenerWrapper<>(listener, rpcContext);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception neverHappens) {  // result of wrapping startCall(...) with a Callable
			throw new RuntimeException(neverHappens);
		}
	}



	class ListenerWrapper<RequestT> extends Listener<RequestT> {

		final Listener<RequestT> wrappedListener;
		final ServerRpcContext rpcContext;



		ListenerWrapper(Listener<RequestT> listenerToWrap, ServerRpcContext rpcContext) {
			this.wrappedListener = listenerToWrap;
			this.rpcContext = rpcContext;
		}



		private void executeWithinCtxs(Runnable wrappedListenerCall) {
			grpcModule.newListenerEventContext(rpcContext).executeWithinSelf(wrappedListenerCall);
		}



		// below just delegate methods to wrappedListener methods called within ctxs

		@Override public void onMessage(RequestT message) {
			executeWithinCtxs(() -> wrappedListener.onMessage(message));
		}

		@Override public void onReady() {
			executeWithinCtxs(wrappedListener::onReady);
		}

		@Override public void onHalfClose() {
			executeWithinCtxs(wrappedListener::onHalfClose);
		}

		@Override public void onCancel() {
			executeWithinCtxs(wrappedListener::onCancel);
		}

		@Override public void onComplete() {
			executeWithinCtxs(wrappedListener::onComplete);
		}
	}



	ServerContextInterceptor(GrpcModule grpcModule) {
		this.grpcModule = grpcModule;
	}
}
