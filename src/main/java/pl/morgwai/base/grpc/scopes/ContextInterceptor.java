/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.grpc.scopes;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Creates and starts tracking a new {@link RpcContext} for each new RPC (<code>ServerCall</code>)
 * and a new {@link ListenerCallContext} for each <code>ServerCall.Listener</code> call.
 *
 * @see GrpcModule
 */
public class ContextInterceptor implements ServerInterceptor {



	ContextTracker<RpcContext> rpcContextTracker;
	ContextTracker<ListenerCallContext> listenerCallContextTracker;



	@Override
	public <Request, Response> Listener<Request> interceptCall(
			ServerCall<Request, Response> call,
			Metadata headers,
			ServerCallHandler<Request, Response> handler) {
		try {
			final RpcContext rpcContext = new RpcContext(call);
			final Listener<Request> listener = rpcContextTracker.callWithin(
				rpcContext,
				() -> handler.startCall(call, headers)
			);

			return new Listener<Request>() {

				@Override
				public void onMessage(Request message) {
					rpcContext.setCurrentMessage(message);
					rpcContextTracker.runWithin(rpcContext, () -> {
						listenerCallContextTracker.runWithin(
							new ListenerCallContext(message),
							() -> listener.onMessage(message)
						);
					});
				}

				@Override
				public void onHalfClose() {
					rpcContextTracker.runWithin(rpcContext, () -> {
						listenerCallContextTracker.runWithin(
							new ListenerCallContext(null),
							() -> listener.onHalfClose()
						);
					});
				}

				@Override
				public void onCancel() {
					rpcContextTracker.runWithin(rpcContext, () -> {
						listenerCallContextTracker.runWithin(
							new ListenerCallContext(null),
							() -> listener.onCancel()
						);
					});
				}

				@Override
				public void onComplete() {
					rpcContextTracker.runWithin(rpcContext, () -> {
						listenerCallContextTracker.runWithin(
							new ListenerCallContext(null),
							() -> listener.onComplete()
						);
					});
				}

				@Override
				public void onReady() {
					rpcContextTracker.runWithin(rpcContext, () -> {
						listenerCallContextTracker.runWithin(
							new ListenerCallContext(null),
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
		this.rpcContextTracker = grpcModule.rpcContextTracker;
		this.listenerCallContextTracker = grpcModule.listenerCallContextTracker;
	}
}
