// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import io.grpc.*;
import io.grpc.ServerCall.Listener;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.junit.Test;
import pl.morgwai.base.grpc.scopes.tests.ContextVerifier;

import static org.junit.Assert.*;



public class ServerContextInterceptorTest extends EasyMockSupport {



	final GrpcModule grpcModule = new GrpcModule();
	final ServerContextInterceptor interceptor =
			(ServerContextInterceptor) grpcModule.serverInterceptor;

	@Mock final ServerCall<Integer, Integer> mockRpc = mock(ServerCall.class);



	@Test
	public void testInterceptCall() {
		final var requestHeaders = new Metadata();
		final MockListener mockListener = new MockListener();
		final var decoratedListener = interceptor.interceptCall(
			mockRpc,
			requestHeaders,
			(call, headers) -> {
				final var eventCtx = grpcModule.listenerEventContextTracker.getCurrentContext();
				assertNotNull("event context should be started", eventCtx);
				final var rpcCtx = eventCtx.getRpcContext();
				assertNotNull("RPC context should be started", rpcCtx);
				assertSame("RPC (ServerCall) object should not be modified",
						mockRpc, ((ServerRpcContext) rpcCtx).getRpc());
				assertSame("request headers should not be modified",
						requestHeaders, rpcCtx.getRequestHeaders());
				final var ctxVerifier = new ContextVerifier(grpcModule, rpcCtx);
				ctxVerifier.add(eventCtx);
				mockListener.ctxVerifier = ctxVerifier;
				return mockListener;
			}
		);
		assertNull("event context should not be leaked",
				grpcModule.listenerEventContextTracker.getCurrentContext());

		final Integer message = 69;
		decoratedListener.onMessage(message);
		assertSame("messages should not be modified", message, mockListener.capturedMessage);
		assertNull("event context should not be leaked",
				grpcModule.listenerEventContextTracker.getCurrentContext());

		decoratedListener.onHalfClose();
		assertNull("event context should not be leaked",
				grpcModule.listenerEventContextTracker.getCurrentContext());
		decoratedListener.onCancel();
		assertNull("event context should not be leaked",
				grpcModule.listenerEventContextTracker.getCurrentContext());
		decoratedListener.onComplete();
		assertNull("event context should not be leaked",
				grpcModule.listenerEventContextTracker.getCurrentContext());
		decoratedListener.onReady();
		assertNull("event context should not be leaked",
				grpcModule.listenerEventContextTracker.getCurrentContext());
	}



	static class MockListener extends Listener<Integer> {

		ContextVerifier ctxVerifier;



		Integer capturedMessage;

		@Override public void onMessage(Integer message) {
			capturedMessage = message;
			ctxVerifier.verifyCtxs();
		}



		@Override public void onHalfClose() { ctxVerifier.verifyCtxs(); }
		@Override public void onCancel() { ctxVerifier.verifyCtxs(); }
		@Override public void onComplete() { ctxVerifier.verifyCtxs(); }
		@Override public void onReady() { ctxVerifier.verifyCtxs(); }
	}
}
