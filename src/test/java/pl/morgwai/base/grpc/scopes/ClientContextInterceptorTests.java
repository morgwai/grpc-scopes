// Copyright 2022 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.io.InputStream;
import org.junit.*;
import org.easymock.*;

import com.google.inject.Key;
import io.grpc.*;
import io.grpc.ClientCall.Listener;
import io.grpc.MethodDescriptor.MethodType;
import pl.morgwai.base.grpc.scopes.ClientContextInterceptor.ListenerProxy;
import pl.morgwai.base.grpc.scopes.tests.ContextVerifier;
import pl.morgwai.base.guice.scopes.ContextTracker;

import static org.junit.Assert.*;
import static org.easymock.EasyMock.*;
import static pl.morgwai.base.guice.scopes.ContextHelpers.produceIfAbsent;



public class ClientContextInterceptorTests extends EasyMockSupport {



	static class MockListener<ResponseT> extends Listener<ResponseT> {

		ContextVerifier ctxVerifier;



		@Override public void onHeaders(Metadata responseHeaders) {
			capturedResponseHeaders = responseHeaders;
			ctxVerifier.verifyCtxs();
		}

		Metadata capturedResponseHeaders;



		@Override public void onMessage(ResponseT message) {
			capturedMessage = message;
			ctxVerifier.verifyCtxs();
		}

		ResponseT capturedMessage;



		@Override public void onClose(Status status, Metadata trailers) {
			capturedStatus = status;
			capturedTrailers = trailers;
			ctxVerifier.verifyCtxs();
		}

		Status capturedStatus;
		Metadata capturedTrailers;



		@Override public void onReady() {
			ctxVerifier.verifyCtxs();
		}
	}



	final GrpcModule grpcModule = new GrpcModule();
	final ContextTracker<ListenerEventContext> ctxTracker = grpcModule.listenerEventScope.tracker;
	final MockListener<Integer> mockListener = new MockListener<>();
	final Metadata requestHeaders = new Metadata();
	final Capture<Listener<Integer>> listenerCapture = newCapture();
	final CallOptions options = CallOptions.DEFAULT;
	@SuppressWarnings("deprecation")
	final MethodDescriptor<String, Integer> methodDescriptor = MethodDescriptor.create(
			MethodType.UNARY, "testMethod", new StubMarshaller<>(), new StubMarshaller<>());
	@Mock Channel mockChannel;
	@Mock ClientCall<String, Integer> mockRpc;



	@Before
	public void setupMocks() {
		injectMocks(this);
		expect(mockChannel.newCall(same(methodDescriptor), same(options)))
			.andReturn(mockRpc)
			.times(1);
		mockRpc.start(capture(listenerCapture), same(requestHeaders));
		expectLastCall().times(1);
		replayAll();
	}



	@After
	public void verifyMocks() {
		verifyAll();
	}



	void testBasicIntercepting(ClientInterceptor interceptor) {

		// intercept and start a mock RPC, capture created Listener and ctx
		final var rpc = interceptor.interceptCall(methodDescriptor, options, mockChannel);
		rpc.start(mockListener, requestHeaders);
		final var decoratedListener = (ListenerProxy<Integer>) listenerCapture.getValue();
		final var rpcCtx = decoratedListener.rpcContext;

		// basic verifications
		mockListener.ctxVerifier = new ContextVerifier(ctxTracker, rpcCtx);
		assertSame("RPC should be stored into rpcCtx",
				mockRpc, rpcCtx.getRpc());
		assertNull("event context should not be leaked",
				ctxTracker.getCurrentContext());

		// verify onHeaders(...)
		final var responseHeaders = new Metadata();
		decoratedListener.onHeaders(responseHeaders);
		assertSame("response headers should not be modified",
				responseHeaders, mockListener.capturedResponseHeaders);
		assertSame("response headers should be stored into rpcCtx",
				responseHeaders, rpcCtx.getResponseHeaders());
		assertNull("event context should not be leaked",
				ctxTracker.getCurrentContext());

		// verify onReady()
		decoratedListener.onReady();
		assertNull("event context should not be leaked",
				ctxTracker.getCurrentContext());

		// verify onMessage(...)
		final Integer message = 666;
		decoratedListener.onMessage(message);
		assertSame("messages should not be modified",
				message, mockListener.capturedMessage);
		assertNull("event context should not be leaked",
				ctxTracker.getCurrentContext());

		// verify onClose(...)
		assertTrue("status should be empty before onClose",
				rpcCtx.getStatus().isEmpty());
		assertTrue("trailers should be empty before onClose",
				rpcCtx.getTrailers().isEmpty());
		final var status = Status.OK;
		final var trailers = new Metadata();
		decoratedListener.onClose(status, trailers);
		assertSame("status should not be modified",
				status, mockListener.capturedStatus);
		assertSame("status should be stored into rpcCtx",
				status, rpcCtx.getStatus().get());
		assertSame("trailers should not be modified",
				trailers, mockListener.capturedTrailers);
		assertSame("trailers should be stored into rpcCtx",
				trailers, rpcCtx.getTrailers().get());
		assertNull("event context should not be leaked",
				ctxTracker.getCurrentContext());
	}

	@Test public void testBasicInterceptingWithNestingInterceptor() {
		testBasicIntercepting(grpcModule.nestingClientInterceptor);
	}

	@Test public void testBasicInterceptingWithStandardInterceptor() {
		testBasicIntercepting(grpcModule.clientInterceptor);
	}



	static final Key<Integer> INT_KEY = Key.get(Integer.class);
	static final Key<String> STRING_KEY = Key.get(String.class);
	static final String stringFromEnclosing = "from enclosing";
	static final String stringFromInner = "from nested";
	static final String anotherString = "another";
	static final Integer intFromEnclosing = 1;
	static final Integer intFromInner = 2;
	static final Integer anotherInt = 3;
	static final ServerRpcContext enclosingCtx = new ServerRpcContext(null, null);



	@Test
	public void testCtxNesting() {
		final var interceptor = grpcModule.nestingClientInterceptor;

		// create and start a client RPC within enclosingCtx, capture the created client ctx
		new ListenerEventContext(enclosingCtx, ctxTracker)
			.executeWithinSelf(() -> {
				final var rpc = interceptor.interceptCall(methodDescriptor, options, mockChannel);
				rpc.start(mockListener, requestHeaders);
			});
		final var innerCtx = ((ListenerProxy<Integer>) listenerCapture.getValue()).rpcContext;

		produceIfAbsent(enclosingCtx, STRING_KEY, () -> stringFromEnclosing);
		assertSame("innerCtx should obtain String from enclosingCtx",
				stringFromEnclosing,
				produceIfAbsent(innerCtx, STRING_KEY, () -> stringFromInner));
		innerCtx.removeScopedObject(STRING_KEY);
		assertSame("removing String from innerCtx should remove it from enclosingCtx as well",
				anotherString,
				produceIfAbsent(enclosingCtx, STRING_KEY, () -> anotherString));

		produceIfAbsent(innerCtx, INT_KEY, () -> intFromInner);
		assertSame("enclosingCtx should obtain Integer from innerCtx",
				intFromInner,
				produceIfAbsent(enclosingCtx, INT_KEY, () -> intFromEnclosing));
		enclosingCtx.removeScopedObject(INT_KEY);
		assertSame("removing String from enclosingCtx should remove it from innerCtx as well",
				anotherInt,
				produceIfAbsent(innerCtx, INT_KEY, () -> anotherInt));
	}



	@Test
	public void testCtxIsolation() {
		final var interceptor = grpcModule.clientInterceptor;

		// create and start a client RPC within enclosingCtx, capture the created client ctx
		new ListenerEventContext(enclosingCtx, ctxTracker)
			.executeWithinSelf(() -> {
				final var rpc = interceptor.interceptCall(methodDescriptor, options, mockChannel);
				rpc.start(mockListener, requestHeaders);
			});
		final var innerCtx = ((ListenerProxy<Integer>) listenerCapture.getValue()).rpcContext;

		produceIfAbsent(enclosingCtx, STRING_KEY, () -> stringFromEnclosing);
		assertSame("innerCtx should obtain String from itself",
				stringFromInner,
				produceIfAbsent(innerCtx, STRING_KEY, () -> stringFromInner));
		innerCtx.removeScopedObject(STRING_KEY);
		assertSame("removing String from innerCtx should not affect enclosingCtx",
				stringFromEnclosing,
				produceIfAbsent(enclosingCtx, STRING_KEY, () -> anotherString));

		produceIfAbsent(innerCtx, INT_KEY, () -> intFromInner);
		assertSame("enclosingCtx should obtain Integer from itself",
				intFromEnclosing,
				produceIfAbsent(enclosingCtx, INT_KEY, () -> intFromEnclosing));
		enclosingCtx.removeScopedObject(INT_KEY);
		assertSame("removing String from enclosingCtx should not affect innerCtx",
				intFromInner,
				produceIfAbsent(innerCtx, INT_KEY, () -> anotherInt));
	}



	static class StubMarshaller<T> implements MethodDescriptor.Marshaller<T> {
		@Override public InputStream stream(T value) { return null; }
		@Override public T parse(InputStream stream) { return null; }
	}
}
