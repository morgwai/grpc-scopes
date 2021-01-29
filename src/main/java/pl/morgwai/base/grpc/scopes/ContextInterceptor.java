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
 * Creates and starts tracking a new <code>ServerCallContext</code> for each new RPC
 * (<code>ServerCall</code>) and message.
 */
public class ContextInterceptor implements ServerInterceptor {



	ContextTracker<RpcContext> rpcContextTracker;
	ContextTracker<MessageContext> messageContextTracker;



	@Override
	public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
			Metadata headers, ServerCallHandler<ReqT, RespT> next) {
		final RpcContext rpcContext = new RpcContext(call);
		rpcContextTracker.setCurrentContext(rpcContext);
		Listener<ReqT> listener = next.startCall(call, headers);
		rpcContextTracker.clearCurrentContext();

		return new Listener<ReqT>() {

			@Override
			public void onMessage(ReqT message) {
				rpcContext.setCurrentMessage(message);
				rpcContextTracker.setCurrentContext(rpcContext);
				messageContextTracker.setCurrentContext(new MessageContext(message));
				listener.onMessage(message);
				rpcContextTracker.clearCurrentContext();
				messageContextTracker.clearCurrentContext();
			}

			@Override
			public void onHalfClose() {
				rpcContextTracker.setCurrentContext(rpcContext);
				messageContextTracker.setCurrentContext(new MessageContext(null));
				listener.onHalfClose();
				rpcContextTracker.clearCurrentContext();
				messageContextTracker.clearCurrentContext();
			}

			@Override
			public void onCancel() {
				rpcContextTracker.setCurrentContext(rpcContext);
				messageContextTracker.setCurrentContext(new MessageContext(null));
				listener.onCancel();
				rpcContextTracker.clearCurrentContext();
				messageContextTracker.clearCurrentContext();
			}

			@Override
			public void onComplete() {
				rpcContextTracker.setCurrentContext(rpcContext);
				messageContextTracker.setCurrentContext(new MessageContext(null));
				listener.onComplete();
				rpcContextTracker.clearCurrentContext();
				messageContextTracker.clearCurrentContext();
			}

			@Override
			public void onReady() {
				rpcContextTracker.setCurrentContext(rpcContext);
				messageContextTracker.setCurrentContext(new MessageContext(null));
				listener.onReady();
				rpcContextTracker.clearCurrentContext();
				messageContextTracker.clearCurrentContext();
			}
		};
	}



	public ContextInterceptor(GrpcModule grpcModule) {
		this.rpcContextTracker = grpcModule.rpcContextTracker;
		this.messageContextTracker = grpcModule.messageContextTracker;
	}
}
