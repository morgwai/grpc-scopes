// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import io.grpc.*;
import io.grpc.ServerCall.Listener;
import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Creates a new {@link ServerRpcContext} for each new RPC ({@link ServerCall}) and a new
 * {@link ListenerEventContext} for each {@link ServerCall.Listener} call and creation.
 * An instance can be obtained from {@link GrpcModule#serverInterceptor}.
 */
public class ServerContextInterceptor implements ServerInterceptor {



	final ContextTracker<ListenerEventContext> ctxTracker;



	ServerContextInterceptor(ContextTracker<ListenerEventContext> ctxTracker) {
		this.ctxTracker = ctxTracker;
	}



	@Override
	public <RequestT, ResponseT> Listener<RequestT> interceptCall(
		ServerCall<RequestT, ResponseT> rpc,
		Metadata headers,
		ServerCallHandler<RequestT, ResponseT> handler
	) {
		final var rpcContext = new ServerRpcContext(rpc, headers);
		final var listener = new ListenerEventContext(rpcContext, ctxTracker).executeWithinSelf(
			// in case of streaming requests this is where the user RPC method will be invoked:
			() -> handler.startCall(rpc, headers)
		);
		return new ListenerProxy<>(listener, rpcContext);
	}



	/**
	 * Executes each method of the wrapped {@link Listener} within a new
	 * {@link ListenerEventContext}.
	 */
	class ListenerProxy<RequestT> extends Listener<RequestT> {

		final Listener<RequestT> wrappedListener;
		final ServerRpcContext rpcContext;



		ListenerProxy(Listener<RequestT> listenerToWrap, ServerRpcContext rpcContext) {
			this.wrappedListener = listenerToWrap;
			this.rpcContext = rpcContext;
		}



		private void executeWithinCtxs(Runnable wrappedListenerCall) {
			new ListenerEventContext(rpcContext, ctxTracker).executeWithinSelf(wrappedListenerCall);
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
}
