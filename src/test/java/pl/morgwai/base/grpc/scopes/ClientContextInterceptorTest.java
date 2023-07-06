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
	final CallOptions options = CallOptions.DEFAULT;
	@SuppressWarnings("deprecation")
	final MethodDescriptor<String, Integer> methodDescriptor = MethodDescriptor.create(
		MethodType.UNARY, "testMethod", new StubMarshaller<>(), new StubMarshaller<>()
	);

	@Mock final Channel mockChannel = mock(Channel.class);
	@Mock final ClientCall<String, Integer> mockRpc = mock(ClientCall.class);



	@Before
	public void recordMockExpectations() {
		expect(mockChannel.newCall(same(methodDescriptor), same(options)))
			.andReturn(mockRpc);
		mockRpc.start(capture(listenerCapture), same(requestHeaders));
		replayAll();
	}



	void testStandAloneCall(boolean nesting) {
		interceptor = nesting
				? (ClientContextInterceptor) grpcModule.nestingClientInterceptor
				: (ClientContextInterceptor) grpcModule.clientInterceptor;

		final var rpc = interceptor.interceptCall(methodDescriptor, options, mockChannel);
		rpc.start(mockListener, requestHeaders);
		final var decoratedListener = (ListenerWrapper<Integer>) listenerCapture.getValue();

		mockListener.ctxVerifier = new ContextVerifier(grpcModule, decoratedListener.rpcContext);
		assertSame("RPC should be stored into rpcCtx",
				mockRpc, decoratedListener.rpcContext.getRpc());
		assertTrue("there should be no parentCtx",
				decoratedListener.rpcContext.getParentContext().isEmpty());
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



	final Integer inheritedObject = 666;
	final Integer anotherObject = 69;
	final Key<Integer> inheritedObjectKey = Key.get(Integer.class, Names.named("inherited"));
	final ServerRpcContext serverRpcContext = new ServerRpcContext(null, null);
	final ClientRpcContext standAloneClientRpcContext = new ClientRpcContext(null, null);
	final ClientRpcContext nestedClientRpcContext =
			new ClientRpcContext(null, null, serverRpcContext);



	void testNestedCall(boolean nesting, RpcContext parentRpcCtx, RpcContext rootRpcCtx) {
		interceptor = nesting
				? (ClientContextInterceptor) grpcModule.nestingClientInterceptor
				: (ClientContextInterceptor) grpcModule.clientInterceptor;
		rootRpcCtx.packageProtectedProvideIfAbsent(inheritedObjectKey, () -> inheritedObject);

		grpcModule.newListenerEventContext(parentRpcCtx).executeWithinSelf(
			() -> {
				final var rpc = interceptor.interceptCall(methodDescriptor, options, mockChannel);
				rpc.start(mockListener, requestHeaders);
			}
		);
		final var decoratedListener = (ListenerWrapper<Integer>) listenerCapture.getValue();

		if (nesting) {
			assertSame("parentCtx should be properly set",
					decoratedListener.rpcContext.getParentContext().get(), parentRpcCtx);
			assertSame("child RPC should inherit RPC scoped objects from the parent",
					inheritedObject,
					decoratedListener.rpcContext.provideIfAbsent(
							inheritedObjectKey, () -> anotherObject));
		} else {
			assertTrue("there should be no parentCtx",
					decoratedListener.rpcContext.getParentContext().isEmpty());
			assertNotSame("child RPC should NOT inherit RPC scoped objects from the parent",
					inheritedObject,
					decoratedListener.rpcContext.provideIfAbsent(
							inheritedObjectKey, () -> anotherObject));
		}
		verifyAll();
	}

	@Test public void testCallNestedInServerCallWithNestingInterceptor() {
		testNestedCall(true, serverRpcContext, serverRpcContext);
	}

	@Test public void testCallNestedInServerCallWithStandardInterceptor() {
		testNestedCall(false, serverRpcContext, serverRpcContext);
	}

	@Test public void testChainedCallWithNestingInterceptor() {
		testNestedCall(true, standAloneClientRpcContext, standAloneClientRpcContext);
	}

	@Test public void testChainedCallWithStandardInterceptor() {
		testNestedCall(false, standAloneClientRpcContext, standAloneClientRpcContext);
	}

	@Test public void testChainedCallNestedInServerCallWithNestingInterceptor() {
		testNestedCall(true, nestedClientRpcContext, nestedClientRpcContext.parentCtx);
	}

	@Test public void testChainedCallNestedInServerCallWithStandardInterceptor() {
		testNestedCall(false, nestedClientRpcContext, nestedClientRpcContext.parentCtx);
	}



	void testRemove(boolean nesting, RpcContext parentRpcCtx, RpcContext rootRpcCtx) {
		interceptor = nesting
				? (ClientContextInterceptor) grpcModule.nestingClientInterceptor
				: (ClientContextInterceptor) grpcModule.clientInterceptor;
		rootRpcCtx.packageProtectedProvideIfAbsent(inheritedObjectKey, () -> inheritedObject);

		grpcModule.newListenerEventContext(parentRpcCtx).executeWithinSelf(
			() -> {
				final var rpc = interceptor.interceptCall(methodDescriptor, options, mockChannel);
				rpc.start(mockListener, requestHeaders);
			}
		);
		final var decoratedListener = (ListenerWrapper<Integer>) listenerCapture.getValue();

		final Integer childObject = 3;
		decoratedListener.rpcContext.provideIfAbsent(inheritedObjectKey, () -> childObject);
		decoratedListener.rpcContext.removeScopedObject(inheritedObjectKey);
		if (nesting) {
			assertNotSame("inherited object should be removed from the parent RPC ctx",
					inheritedObject,
					parentRpcCtx.packageProtectedProvideIfAbsent(
							inheritedObjectKey, () -> anotherObject));
			assertNotSame("inherited object should be removed from the root RPC ctx",
					inheritedObject,
					rootRpcCtx.packageProtectedProvideIfAbsent(
							inheritedObjectKey, () -> anotherObject));
		} else {
			assertSame("object scoped to the root RPC should NOT be removed",
					inheritedObject,
					rootRpcCtx.packageProtectedProvideIfAbsent(
							inheritedObjectKey, () -> anotherObject));
			assertSame("object scoped to the parent RPC should NOT be removed",
					inheritedObject,
					parentRpcCtx.packageProtectedProvideIfAbsent(
							inheritedObjectKey, () -> anotherObject));
			assertNotSame("object scoped to the child RPC should be removed",
					childObject,
					decoratedListener.rpcContext.provideIfAbsent(
							inheritedObjectKey, () -> anotherObject));
		}
		verifyAll();
	}

	@Test public void testRemoveInNestedCallWithNestingInterceptor() {
		testRemove(true, serverRpcContext, serverRpcContext);
	}

	@Test public void testRemoveInNestedCallWithStandardInterceptor() {
		testRemove(false, serverRpcContext, serverRpcContext);
	}

	@Test public void testRemoveInChainedCallWithNestingInterceptor() {
		testRemove(true, standAloneClientRpcContext, standAloneClientRpcContext);
	}

	@Test public void testRemoveInChainedCallWithStandardInterceptor() {
		testRemove(false, standAloneClientRpcContext, standAloneClientRpcContext);
	}

	@Test public void testRemoveInChainedCallNestedInServerCallWithNestingInterceptor() {
		testRemove(true, nestedClientRpcContext, nestedClientRpcContext.parentCtx);
	}

	@Test public void testRemoveInChainedCallNestedInServerCallWithStandardInterceptor() {
		testRemove(false, nestedClientRpcContext, nestedClientRpcContext.parentCtx);
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



	static class StubMarshaller<T> implements MethodDescriptor.Marshaller<T> {
		@Override public InputStream stream(T value) { return null; }
		@Override public T parse(InputStream stream) { return null; }
	}
}
