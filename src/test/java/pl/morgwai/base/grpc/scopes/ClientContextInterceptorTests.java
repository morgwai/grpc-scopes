// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.io.InputStream;

import com.google.inject.Key;
import com.google.inject.name.Names;
import io.grpc.*;
import io.grpc.ClientCall.Listener;
import io.grpc.MethodDescriptor.MethodType;
import org.easymock.*;
import org.junit.Before;
import org.junit.Test;
import pl.morgwai.base.grpc.scopes.ClientContextInterceptor.ListenerProxy;
import pl.morgwai.base.grpc.scopes.tests.ContextVerifier;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;



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
	final MockListener<Integer> mockListener = new MockListener<>();
	final Metadata requestHeaders = new Metadata();
	final Capture<Listener<Integer>> listenerCapture = newCapture();
	final CallOptions options = CallOptions.DEFAULT;
	@SuppressWarnings("deprecation")
	final MethodDescriptor<String, Integer> methodDescriptor = MethodDescriptor.create(
			MethodType.UNARY, "testMethod", new StubMarshaller<>(), new StubMarshaller<>());
	@Mock final Channel mockChannel = mock(Channel.class);
	@Mock final ClientCall<String, Integer> mockRpc = mock(ClientCall.class);



	@Before
	public void recordMockExpectations() {
		expect(mockChannel.newCall(same(methodDescriptor), same(options)))
			.andReturn(mockRpc);
		mockRpc.start(capture(listenerCapture), same(requestHeaders));
		replayAll();
	}



	void testBasicIntercepting(ClientInterceptor interceptor) {

		// intercept and start a mock RPC, capture created Listener and ctx
		final var rpc = interceptor.interceptCall(methodDescriptor, options, mockChannel);
		rpc.start(mockListener, requestHeaders);
		final var decoratedListener = (ListenerProxy<Integer>) listenerCapture.getValue();
		final var rpcCtx = decoratedListener.rpcContext;

		// basic verifications
		mockListener.ctxVerifier = new ContextVerifier(grpcModule, rpcCtx);
		assertSame("RPC should be stored into rpcCtx",
				mockRpc, rpcCtx.getRpc());
		assertTrue("there should be no parentCtx",
				rpcCtx.getParentContext().isEmpty());
		assertNull("event context should not be leaked",
				grpcModule.listenerEventContextTracker.getCurrentContext());

		// verify onHeaders(...)
		final var responseHeaders = new Metadata();
		decoratedListener.onHeaders(responseHeaders);
		assertSame("response headers should not be modified",
				responseHeaders, mockListener.capturedResponseHeaders);
		assertSame("response headers should be stored into rpcCtx",
				responseHeaders, rpcCtx.getResponseHeaders());
		assertNull("event context should not be leaked",
				grpcModule.listenerEventContextTracker.getCurrentContext());

		// verify onReady()
		decoratedListener.onReady();
		assertNull("event context should not be leaked",
				grpcModule.listenerEventContextTracker.getCurrentContext());

		// verify onMessage(...)
		final Integer message = 666;
		decoratedListener.onMessage(message);
		assertSame("messages should not be modified",
				message, mockListener.capturedMessage);
		assertNull("event context should not be leaked",
				grpcModule.listenerEventContextTracker.getCurrentContext());

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
				grpcModule.listenerEventContextTracker.getCurrentContext());

		verifyAll();
	}

	@Test public void testBasicInterceptingWithNestingInterceptor() {
		testBasicIntercepting(grpcModule.nestingClientInterceptor);
	}

	@Test public void testBasicInterceptingWithStandardInterceptor() {
		testBasicIntercepting(grpcModule.clientInterceptor);
	}



	void testCtxNesting(boolean nesting, RpcContext parentRpcCtx) {
		final var interceptor = nesting
				? grpcModule.nestingClientInterceptor
				: grpcModule.clientInterceptor;
		final var rootRpcCtx =  // parent of the parent if exists, otherwise just the parent itself
				(parentRpcCtx instanceof ClientRpcContext
						&& ((ClientRpcContext) parentRpcCtx).getParentContext().isPresent())
				? ((ClientRpcContext) parentRpcCtx).getParentContext().get()
				: parentRpcCtx;
		final Integer rootProvidedObject = 666;
		final var key = Key.get(Integer.class, Names.named("testKey"));
		rootRpcCtx.packageProtectedProduceIfAbsent(key, () -> rootProvidedObject);

		// create and start a child RPC within the parent RPC ctx, capture the created child RPC ctx
		grpcModule.newListenerEventContext(parentRpcCtx).executeWithinSelf(
			() -> {
				final var rpc = interceptor.interceptCall(methodDescriptor, options, mockChannel);
				rpc.start(mockListener, requestHeaders);
			}
		);
		final var childRpcCtx = ((ListenerProxy<Integer>) listenerCapture.getValue()).rpcContext;

		// verify sharing/isolation of an RPC-scoped object between a child and its parent
		final Integer childProvidedObject = 69;
		if (nesting) {
			assertSame("parent ctx should be properly linked",
					childRpcCtx.getParentContext().get(), parentRpcCtx);
			assertSame("child RPC should share RPC-scoped objects with the parent",
					rootProvidedObject,
					childRpcCtx.produceIfAbsent(key, () -> childProvidedObject));
		} else {
			assertTrue("parent ctx should NOT be linked",
					childRpcCtx.getParentContext().isEmpty());
			assertSame("child RPC should NOT share RPC-scoped objects with the parent",
					childProvidedObject,
					childRpcCtx.produceIfAbsent(key, () -> childProvidedObject));
		}

		// remove an RPC-scoped object from ctx via the child and verify the parent and root ctx
		childRpcCtx.removeScopedObject(key);
		final var yetAnotherObject = 3;
		if (nesting) {
			assertNotSame("RPC-scoped object should be removed from the parent ctx",
					rootProvidedObject,
					parentRpcCtx.packageProtectedProduceIfAbsent(key, () -> yetAnotherObject));
			assertNotSame("RPC-scoped object should be removed from the root ctx",
					rootProvidedObject,
					rootRpcCtx.packageProtectedProduceIfAbsent(key, () -> yetAnotherObject));
		} else {
			assertSame("object scoped to the root RPC should NOT be removed",
					rootProvidedObject,
					rootRpcCtx.packageProtectedProduceIfAbsent(key, () -> yetAnotherObject));
			assertSame("object scoped to the parent RPC should NOT be removed",
					rootProvidedObject,
					parentRpcCtx.packageProtectedProduceIfAbsent(key, () -> yetAnotherObject));
			assertNotSame("object scoped to the child RPC should be removed",
					childProvidedObject,
					childRpcCtx.produceIfAbsent(key, () -> yetAnotherObject));
		}

		verifyAll();
	}

	final ServerRpcContext serverRpcContext = new ServerRpcContext(null, null);

	@Test public void testCallNestedInServerCallWithNestingInterceptor() {
		testCtxNesting(true, serverRpcContext);
	}

	@Test public void testCallNestedInServerCallWithStandardInterceptor() {
		testCtxNesting(false, serverRpcContext);
	}

	final ClientRpcContext standAloneClientRpcContext = new ClientRpcContext(null, null);

	@Test public void testChainedCallWithNestingInterceptor() {
		testCtxNesting(true, standAloneClientRpcContext);
	}

	@Test public void testChainedCallWithStandardInterceptor() {
		testCtxNesting(false, standAloneClientRpcContext);
	}

	final ClientRpcContext nestedClientRpcContext =
			new ClientRpcContext(null, null, serverRpcContext);

	@Test public void testChainedCallNestedInServerCallWithNestingInterceptor() {
		testCtxNesting(true, nestedClientRpcContext);
	}

	@Test public void testChainedCallNestedInServerCallWithStandardInterceptor() {
		testCtxNesting(false, nestedClientRpcContext);
	}



	static class StubMarshaller<T> implements MethodDescriptor.Marshaller<T> {
		@Override public InputStream stream(T value) { return null; }
		@Override public T parse(InputStream stream) { return null; }
	}
}
