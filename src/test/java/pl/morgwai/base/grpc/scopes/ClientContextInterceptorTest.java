// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import com.google.inject.Key;
import com.google.inject.name.Names;
import io.grpc.*;
import io.grpc.ClientCall.Listener;
import org.easymock.*;
import org.junit.Before;
import org.junit.Test;
import pl.morgwai.base.grpc.scopes.ClientContextInterceptor.ListenerWrapper;
import pl.morgwai.base.grpc.scopes.tests.ContextVerifier;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;



public class ClientContextInterceptorTest extends EasyMockSupport {



	final GrpcModule grpcModule = new GrpcModule();
	ClientContextInterceptor interceptor;

	final MockListener<Integer> mockListener = new MockListener<>();
	final Metadata requestHeaders = new Metadata();
	final Capture<Listener<Integer>> listenerCapture = newCapture();
	final MethodDescriptor<String, Integer> methodDescriptor = null;
	final CallOptions options = CallOptions.DEFAULT;

	@Mock final Channel mockChannel = mock(Channel.class);
	@Mock final ClientCall<String, Integer> mockRpc = mock(ClientCall.class);



	@Before
	public void recordMockExpectations() {
		expect(mockChannel.newCall(same(methodDescriptor), same(options)))
			.andReturn(mockRpc);
		mockRpc.start(capture(listenerCapture), same(requestHeaders));
		replayAll();
	}



	void testStandAloneCall(boolean nestingRpcContext) {
		interceptor = nestingRpcContext
				? (ClientContextInterceptor) grpcModule.nestingClientInterceptor
				: (ClientContextInterceptor) grpcModule.clientInterceptor;

		interceptor.interceptCall(methodDescriptor, options, mockChannel)
				.start(mockListener, requestHeaders);
		final ListenerWrapper<Integer> decoratedListener =
				(ListenerWrapper<Integer>) listenerCapture.getValue();

		mockListener.ctxVerifier = new ContextVerifier(grpcModule, decoratedListener.rpcContext);
		assertSame("RPC should be stored into rpcCtx",
				mockRpc, decoratedListener.rpcContext.getRpc());
		assertNull("event context should not be leaked",
				grpcModule.listenerEventContextTracker.getCurrentContext());

		final var responseHeaders = new Metadata();
		decoratedListener.onHeaders(responseHeaders);
		assertSame("response headers should not be modified",
				responseHeaders, mockListener.capturedResponseHeaders);
		assertSame("response headers should be stored into rpcCtx",
				responseHeaders, decoratedListener.rpcContext.getResponseHeaders());
		assertNull("event context should not be leaked",
				grpcModule.listenerEventContextTracker.getCurrentContext());

		final Integer message = 666;
		decoratedListener.onMessage(message);
		assertSame("messages should not be modified", message, mockListener.capturedMessage);
		assertNull("event context should not be leaked",
				grpcModule.listenerEventContextTracker.getCurrentContext());

		final var status = Status.OK;
		final var trailers = new Metadata();
		decoratedListener.onClose(status, trailers);
		assertSame("status should not be modified", status, mockListener.capturedStatus);
		assertSame("status should be stored into rpcCtx",
				status, decoratedListener.rpcContext.getStatus().get());
		assertSame("trailers should not be modified", trailers, mockListener.capturedTrailers);
		assertSame("trailers should be stored into rpcCtx",
				trailers, decoratedListener.rpcContext.getTrailers().get());
		assertNull("event context should not be leaked",
				grpcModule.listenerEventContextTracker.getCurrentContext());

		decoratedListener.onReady();
		assertNull("event context should not be leaked",
				grpcModule.listenerEventContextTracker.getCurrentContext());
		verifyAll();
	}

	@Test public void testStandAloneCallWithNestingInterceptor() { testStandAloneCall(true); }
	@Test public void testStandAloneCallWithStandardInterceptor() { testStandAloneCall(false); }



	void testNestedCall(boolean nestingRpcContext) {
		interceptor = nestingRpcContext
				? (ClientContextInterceptor) grpcModule.nestingClientInterceptor
				: (ClientContextInterceptor) grpcModule.clientInterceptor;
		final Integer inheritedObject = 666;
		final var inheritedObjectKey = Key.get(Integer.class, Names.named("inherited"));
		final var parentRpcCtx = new ServerRpcContext(null, null);
		parentRpcCtx.provideIfAbsent(inheritedObjectKey, () -> inheritedObject);

		grpcModule.newListenerEventContext(parentRpcCtx).executeWithinSelf(
				() -> interceptor.interceptCall(methodDescriptor, options, mockChannel)
						.start(mockListener, requestHeaders));
		final ListenerWrapper<Integer> decoratedListener =
				(ListenerWrapper<Integer>) listenerCapture.getValue();

		if (nestingRpcContext) {
			assertSame("child RPC should inherit RPC scoped objects from the parent",
					inheritedObject,
					decoratedListener.rpcContext.provideIfAbsent(inheritedObjectKey, () -> 3));
		} else {
			assertNotSame("child RPC should NOT inherit RPC scoped objects from the parent",
					inheritedObject,
					decoratedListener.rpcContext.provideIfAbsent(inheritedObjectKey, () -> 3));
		}
		verifyAll();
	}

	@Test public void testNestedCallWithNestingInterceptor() { testNestedCall(true); }
	@Test public void testNestedCallWithStandardInterceptor() { testNestedCall(false); }



	static class MockListener<ResponseT> extends Listener<ResponseT> {

		ContextVerifier ctxVerifier;



		Metadata capturedResponseHeaders;

		@Override public void onHeaders(Metadata responseHeaders) {
			capturedResponseHeaders = responseHeaders;
			ctxVerifier.verifyCtxs();
		}



		ResponseT capturedMessage;

		@Override public void onMessage(ResponseT message) {
			capturedMessage = message;
			ctxVerifier.verifyCtxs();
		}



		Status capturedStatus;
		Metadata capturedTrailers;

		@Override public void onClose(Status status, Metadata trailers) {
			capturedStatus = status;
			capturedTrailers = trailers;
			ctxVerifier.verifyCtxs();
		}



		@Override public void onReady() {
			ctxVerifier.verifyCtxs();
		}
	}
}
