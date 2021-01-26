/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.grpc.scopes;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

import static pl.morgwai.base.grpc.scopes.GrpcScopes.MESSAGE_CONTEXT_TRACKER;
import static pl.morgwai.base.grpc.scopes.GrpcScopes.RPC_CONTEXT_TRACKER;



/**
 * Creates and starts tracking of <code>CallContext</code> for each new RPC
 * (<code>ServerCall</code>) and message.
 */
public class ContextInterceptor implements ServerInterceptor {



	@Override
	public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
			Metadata headers, ServerCallHandler<ReqT, RespT> next) {
		final RpcContext rpcContext = new RpcContext(call);
		RPC_CONTEXT_TRACKER.setCurrentContext(rpcContext);
		Listener<ReqT> listener = next.startCall(call, headers);
		RPC_CONTEXT_TRACKER.clearCurrentContext();

		return new Listener<ReqT>() {

			@Override
			public void onMessage(ReqT message) {
				rpcContext.setCurrentMessage(message);
				RPC_CONTEXT_TRACKER.setCurrentContext(rpcContext);
				MESSAGE_CONTEXT_TRACKER.setCurrentContext(new MessageContext(message));
				listener.onMessage(message);
				RPC_CONTEXT_TRACKER.clearCurrentContext();
				MESSAGE_CONTEXT_TRACKER.clearCurrentContext();
			}

			@Override
			public void onHalfClose() {
				RPC_CONTEXT_TRACKER.setCurrentContext(rpcContext);
				MESSAGE_CONTEXT_TRACKER.setCurrentContext(new MessageContext(null));
				listener.onHalfClose();
				RPC_CONTEXT_TRACKER.clearCurrentContext();
				MESSAGE_CONTEXT_TRACKER.clearCurrentContext();
			}

			@Override
			public void onCancel() {
				RPC_CONTEXT_TRACKER.setCurrentContext(rpcContext);
				MESSAGE_CONTEXT_TRACKER.setCurrentContext(new MessageContext(null));
				listener.onCancel();
				RPC_CONTEXT_TRACKER.clearCurrentContext();
				MESSAGE_CONTEXT_TRACKER.clearCurrentContext();
			}

			@Override
			public void onComplete() {
				RPC_CONTEXT_TRACKER.setCurrentContext(rpcContext);
				MESSAGE_CONTEXT_TRACKER.setCurrentContext(new MessageContext(null));
				listener.onComplete();
				RPC_CONTEXT_TRACKER.clearCurrentContext();
				MESSAGE_CONTEXT_TRACKER.clearCurrentContext();
			}

			@Override
			public void onReady() {
				RPC_CONTEXT_TRACKER.setCurrentContext(rpcContext);
				MESSAGE_CONTEXT_TRACKER.setCurrentContext(new MessageContext(null));
				listener.onReady();
				RPC_CONTEXT_TRACKER.clearCurrentContext();
				MESSAGE_CONTEXT_TRACKER.clearCurrentContext();
			}
		};
	}
}
