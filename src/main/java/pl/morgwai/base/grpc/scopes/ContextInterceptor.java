// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;



/**
 * Creates and starts tracking a new {@link RpcContext} for each new RPC ({@link ServerCall})
 * and a new {@link ListenerCallContext} for each {@link Listener} call.
 *
 * @see GrpcModule
 */
public class ContextInterceptor implements ServerInterceptor {



	final GrpcModule grpcModule;



	@Override
	public <Request, Response> Listener<Request> interceptCall(
			ServerCall<Request, Response> call,
			Metadata headers,
			ServerCallHandler<Request, Response> handler) {
		final RpcContext rpcContext = grpcModule.newRpcContext(call, headers);
		final Listener<Request> listener;
		try {
			listener = rpcContext.executeWithinSelf(() -> handler.startCall(call, headers));
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			return null;  // dead code: result of wrapping handler.startCall(...) with a Callable
		}

		return new Listener<Request>() {

			@Override
			public void onMessage(Request message) {
				rpcContext.executeWithinSelf(() -> {
					grpcModule.newListenerCallContext().executeWithinSelf(
						() -> listener.onMessage(message)
					);
				});
			}

			@Override
			public void onHalfClose() {
				rpcContext.executeWithinSelf(() -> {
					grpcModule.newListenerCallContext().executeWithinSelf(
						() -> listener.onHalfClose()
					);
				});
			}

			@Override
			public void onCancel() {
				rpcContext.executeWithinSelf(() -> {
					grpcModule.newListenerCallContext().executeWithinSelf(
						() -> listener.onCancel()
					);
				});
			}

			@Override
			public void onComplete() {
				rpcContext.executeWithinSelf(() -> {
					grpcModule.newListenerCallContext().executeWithinSelf(
						() -> listener.onComplete()
					);
				});
			}

			@Override
			public void onReady() {
				rpcContext.executeWithinSelf(() -> {
					grpcModule.newListenerCallContext().executeWithinSelf(
						() -> listener.onReady()
					);
				});
			}
		};
	}



	public ContextInterceptor(GrpcModule grpcModule) {
		this.grpcModule = grpcModule;
	}
}
