// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import io.grpc.*;
import io.grpc.ClientCall.Listener;
import org.easymock.*;
import org.junit.Test;
import pl.morgwai.base.grpc.scopes.ClientContextInterceptor.ListenerWrapper;
import pl.morgwai.base.grpc.scopes.tests.ContextVerifier;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;



public class ClientContextInterceptorTest extends EasyMockSupport {



	final GrpcModule grpcModule = new GrpcModule();
	final ClientContextInterceptor interceptor = grpcModule.clientInterceptor;

	@Mock final Channel mockChannel = mock(Channel.class);
	@Mock final ClientCall<String, Integer> mockRpc = mock(ClientCall.class);



	@Test
	public void testInterceptCall() {
		final MockListener<Integer> mockListener = new MockListener<>();
		final var requestHeaders = new Metadata();
		final Capture<Listener<Integer>> listenerCapture = newCapture();
		final MethodDescriptor<String, Integer> methodDescriptor = null;
		final var options = CallOptions.DEFAULT;

		expect(mockChannel.newCall(same(methodDescriptor), same(options)))
			.andReturn(mockRpc);
		mockRpc.start(capture(listenerCapture), same(requestHeaders));
		replayAll();

		final ClientCall<String, Integer> decoratedRpc =
				interceptor.interceptCall(methodDescriptor, options, mockChannel);
		decoratedRpc.start(mockListener, requestHeaders);
		final Listener<Integer> decoratedListener = listenerCapture.getValue();

		mockListener.ctxVerifier = new ContextVerifier(
				grpcModule, ((ListenerWrapper<Integer>) decoratedListener).rpcContext);
		assertNull("event context should not be leaked",
				grpcModule.listenerEventContextTracker.getCurrentContext());

		final var responseHeaders = new Metadata();
		decoratedListener.onHeaders(responseHeaders);
		assertSame("response headers should not be modified",
				responseHeaders, mockListener.capturedResponseHeaders);
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
		assertSame("trailers should not be modified", trailers, mockListener.capturedTrailers);
		assertNull("event context should not be leaked",
				grpcModule.listenerEventContextTracker.getCurrentContext());

		decoratedListener.onReady();
		assertNull("event context should not be leaked",
				grpcModule.listenerEventContextTracker.getCurrentContext());
		verifyAll();
	}



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
