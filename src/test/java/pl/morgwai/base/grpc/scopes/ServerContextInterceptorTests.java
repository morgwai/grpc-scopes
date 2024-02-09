// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed unLevel.der the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import org.easymock.EasyMockSupport;
import org.junit.*;
import pl.morgwai.base.grpc.scopes.tests.ContextVerifier;

import static org.junit.Assert.*;



public class ServerContextInterceptorTests extends EasyMockSupport {



	final GrpcModule grpcModule = new GrpcModule();
	final ServerContextInterceptor interceptor =
			(ServerContextInterceptor) grpcModule.serverInterceptor;
	final ServerCall<Integer, Integer> mockRpc = mock(ServerCall.class);



	@Before
	public void setupMocks() {
		replayAll();
	}



	@After
	public void verifyMocks() {
		verifyAll();
	}



	@Test
	public void testInterceptCallWithRuntimeException() {
		final var thrown = new RuntimeException("thrown");
		try {
			interceptor.interceptCall(
				mockRpc,
				new Metadata(),
				(rpc, headers) -> { throw thrown; }
			);
			fail("RuntimeException thrown by the handler should be propagated");
		} catch (RuntimeException caught) {
			assertSame("caught exception should be the same as thrown", thrown, caught);
		}
	}



	@Test
	public void testInterceptCall() {
		final var requestHeaders = new Metadata();
		final MockListener mockListener = new MockListener();
		final var decoratedListener = interceptor.interceptCall(
			mockRpc,
			requestHeaders,
			(rpc, headers) -> {
				final var eventCtx = grpcModule.listenerEventContextTracker.getCurrentContext();
				assertNotNull("event context should be started",
						eventCtx);
				final var rpcCtx = eventCtx.getRpcContext();
				assertNotNull("RPC context should be started",
						rpcCtx);
				assertSame("RPC (ServerCall) object should not be modified",
						mockRpc, ((ServerRpcContext) rpcCtx).getRpc());
				assertSame("RPC (ServerCall) object should not be modified",
						mockRpc, rpc);
				assertSame("request headers should not be modified",
						requestHeaders, rpcCtx.getRequestHeaders());
				assertSame("request headers should not be modified",
						requestHeaders, headers);
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
		assertSame("messages should not be modified",
				message, mockListener.capturedMessage);
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
