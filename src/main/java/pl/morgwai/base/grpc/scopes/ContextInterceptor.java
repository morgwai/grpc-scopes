/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.grpc.scopes;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;



/**
 * Creates and starts tracking a new {@link RpcContext} for each new RPC (<code>ServerCall</code>)
 * and a new {@link ListenerCallContext} for each <code>ServerCall.Listener</code> call.
 *
 * @see GrpcModule
 */
public class ContextInterceptor implements ServerInterceptor {



	GrpcModule grpcModule;



	@Override
	public <Request, Response> Listener<Request> interceptCall(
			ServerCall<Request, Response> call,
			Metadata headers,
			ServerCallHandler<Request, Response> handler) {
		try {
			final RpcContext rpcContext = grpcModule.newRpcContext(call);
			final Listener<Request> listener = rpcContext.callWithin(
				() -> handler.startCall(call, headers)
			);

			return new Listener<Request>() {

				@Override
				public void onMessage(Request message) {
					rpcContext.setCurrentMessage(message);
					rpcContext.runWithin(() -> {
						grpcModule.newListenerCallContext(message).runWithin(
							() -> listener.onMessage(message)
						);
					});
				}

				@Override
				public void onHalfClose() {
					rpcContext.runWithin(() -> {
						grpcModule.newListenerCallContext().runWithin(
							() -> listener.onHalfClose()
						);
					});
				}

				@Override
				public void onCancel() {
					rpcContext.runWithin(() -> {
						grpcModule.newListenerCallContext().runWithin(
							() -> listener.onCancel()
						);
					});
				}

				@Override
				public void onComplete() {
					rpcContext.runWithin(() -> {
						grpcModule.newListenerCallContext().runWithin(
							() -> listener.onComplete()
						);
					});
				}

				@Override
				public void onReady() {
					rpcContext.runWithin(() -> {
						grpcModule.newListenerCallContext().runWithin(
							() -> listener.onReady()
						);
					});
				}
			};
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			// unreachable code: result of wrapping handler.startCall(call, headers) in a Callable
			return null;
		}
	}



	public ContextInterceptor(GrpcModule grpcModule) {
		this.grpcModule = grpcModule;
	}
}
