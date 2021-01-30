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
	public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
			Metadata headers, ServerCallHandler<ReqT, RespT> handler) {
		final RpcContext rpcContext = new RpcContext(call);
		rpcContextTracker.setCurrentContext(rpcContext);
		Listener<ReqT> listener = handler.startCall(call, headers);
		rpcContextTracker.clearCurrentContext();

		return new Listener<ReqT>() {

			@Override
			public void onMessage(ReqT message) {
				rpcContext.setCurrentMessage(message);
				rpcContextTracker.setCurrentContext(rpcContext);
				listenerCallContextTracker.setCurrentContext(new ListenerCallContext(message));
				listener.onMessage(message);
				rpcContextTracker.clearCurrentContext();
				listenerCallContextTracker.clearCurrentContext();
			}

			@Override
			public void onHalfClose() {
				rpcContextTracker.setCurrentContext(rpcContext);
				listenerCallContextTracker.setCurrentContext(new ListenerCallContext(null));
				listener.onHalfClose();
				rpcContextTracker.clearCurrentContext();
				listenerCallContextTracker.clearCurrentContext();
			}

			@Override
			public void onCancel() {
				rpcContextTracker.setCurrentContext(rpcContext);
				listenerCallContextTracker.setCurrentContext(new ListenerCallContext(null));
				listener.onCancel();
				rpcContextTracker.clearCurrentContext();
				listenerCallContextTracker.clearCurrentContext();
			}

			@Override
			public void onComplete() {
				rpcContextTracker.setCurrentContext(rpcContext);
				listenerCallContextTracker.setCurrentContext(new ListenerCallContext(null));
				listener.onComplete();
				rpcContextTracker.clearCurrentContext();
				listenerCallContextTracker.clearCurrentContext();
			}

			@Override
			public void onReady() {
				rpcContextTracker.setCurrentContext(rpcContext);
				listenerCallContextTracker.setCurrentContext(new ListenerCallContext(null));
				listener.onReady();
				rpcContextTracker.clearCurrentContext();
				listenerCallContextTracker.clearCurrentContext();
			}
		};
	}



	public ContextInterceptor(GrpcModule grpcModule) {
		this.rpcContextTracker = grpcModule.rpcContextTracker;
		this.listenerCallContextTracker = grpcModule.listenerCallContextTracker;
	}
}
