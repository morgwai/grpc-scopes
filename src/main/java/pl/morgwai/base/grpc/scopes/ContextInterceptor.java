// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;



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
			final var listener = rpcContext.executeWithinSelf(
				() -> grpcModule.newListenerEventContext().executeWithinSelf(
					() -> handler.startCall(call, headers)
				)
			);
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

		@Override
		public void onMessage(RequestT message) {
			rpcContext.executeWithinSelf(
				() -> grpcModule.newListenerEventContext().executeWithinSelf(
					() -> wrappedListener.onMessage(message)
				)
			);
		}

		@Override
		public void onHalfClose() {
			rpcContext.executeWithinSelf(
				() -> grpcModule.newListenerEventContext().executeWithinSelf(
					wrappedListener::onHalfClose
				)
			);
		}

		@Override
		public void onCancel() {
			rpcContext.executeWithinSelf(
				() -> grpcModule.newListenerEventContext().executeWithinSelf(
					wrappedListener::onCancel
				)
			);
		}

		@Override
		public void onComplete() {
			rpcContext.executeWithinSelf(
				() -> grpcModule.newListenerEventContext().executeWithinSelf(
					wrappedListener::onComplete
				)
			);
		}

		@Override
		public void onReady() {
			rpcContext.executeWithinSelf(
				() -> grpcModule.newListenerEventContext().executeWithinSelf(
					wrappedListener::onReady
				)
			);
		}

		ListenerWrapper(Listener<RequestT> wrappedListener, RpcContext rpcContext) {
			this.wrappedListener = wrappedListener;
			this.rpcContext = rpcContext;
		}
	}



	ContextInterceptor(GrpcModule grpcModule) {
		this.grpcModule = grpcModule;
	}
}
