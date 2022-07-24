// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import io.grpc.*;
import io.grpc.ServerCall.Listener;



/**
 * Creates and starts tracking a new {@link RpcContext} for each new RPC ({@link ServerCall})
 * and a new {@link ListenerEventContext} for each {@link Listener} call and creation in
 * {@link ServerCallHandler#startCall(ServerCall, Metadata)}.
 *
 * @see GrpcModule
 */
public class ContextInterceptor implements ServerInterceptor {



	final GrpcModule grpcModule;



	@Override
	public <RequestT, ResponseT> Listener<RequestT> interceptCall(
			ServerCall<RequestT, ResponseT> call,
			Metadata headers,
			ServerCallHandler<RequestT, ResponseT> handler) {
		final RpcContext rpcContext = grpcModule.newRpcContext(call, headers);
		try {
			final var listener = grpcModule.newListenerEventContext(rpcContext).executeWithinSelf(
					() -> handler.startCall(call, headers));
			return new ListenerWrapper<>(listener, rpcContext);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			return null;  // dead code: result of wrapping handler.startCall(...) with a Callable
		}
	}



	class ListenerWrapper<RequestT> extends Listener<RequestT> {

		final Listener<RequestT> wrappedListener;
		final RpcContext rpcContext;



		ListenerWrapper(Listener<RequestT> wrappedListener, RpcContext rpcContext) {
			this.wrappedListener = wrappedListener;
			this.rpcContext = rpcContext;
		}



		private void executeWithinCtxs(Runnable wrappedListenerCall) {
			grpcModule.newListenerEventContext(rpcContext).executeWithinSelf(wrappedListenerCall);
		}



		// below just delegate within ctxs all Listener calls to wrappedListener

		@Override public void onMessage(RequestT message) {
			executeWithinCtxs(() -> wrappedListener.onMessage(message));
		}

		@Override public void onHalfClose() { executeWithinCtxs(wrappedListener::onHalfClose); }

		@Override public void onCancel() { executeWithinCtxs(wrappedListener::onCancel); }

		@Override public void onComplete() { executeWithinCtxs(wrappedListener::onComplete); }

		@Override public void onReady() { executeWithinCtxs(wrappedListener::onReady); }
	}



	ContextInterceptor(GrpcModule grpcModule) {
		this.grpcModule = grpcModule;
	}
}
