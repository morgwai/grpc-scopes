// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.HashSet;
import java.util.Set;

import io.grpc.ServerCallHandler;
import io.grpc.ServerCall.Listener;
import org.junit.Test;

import static org.junit.Assert.*;



public class ContextInterceptorTest {



	final GrpcModule grpcModule = new GrpcModule();
	final ContextInterceptor interceptor = new ContextInterceptor(grpcModule);



	@Test
	public void testInterceptCall() {
		final var wrappedListener = interceptor.interceptCall(
			null,
			null,
			(ServerCallHandler<Integer, Integer>) (call, headers) -> {
				final var rpcCtx = grpcModule.rpcContextTracker.getCurrentContext();
				final var eventCtx = grpcModule.listenerEventContextTracker.getCurrentContext();
				assertNotNull(rpcCtx);
				assertNotNull(eventCtx);
				return new MockListener(rpcCtx, eventCtx);
			}
		);
		assertNull(grpcModule.rpcContextTracker.getCurrentContext());
		assertNull(grpcModule.listenerEventContextTracker.getCurrentContext());
		wrappedListener.onMessage(0);
		assertNull(grpcModule.rpcContextTracker.getCurrentContext());
		assertNull(grpcModule.listenerEventContextTracker.getCurrentContext());
		wrappedListener.onHalfClose();
		assertNull(grpcModule.rpcContextTracker.getCurrentContext());
		assertNull(grpcModule.listenerEventContextTracker.getCurrentContext());
		wrappedListener.onCancel();
		assertNull(grpcModule.rpcContextTracker.getCurrentContext());
		assertNull(grpcModule.listenerEventContextTracker.getCurrentContext());
		wrappedListener.onComplete();
		assertNull(grpcModule.rpcContextTracker.getCurrentContext());
		assertNull(grpcModule.listenerEventContextTracker.getCurrentContext());
		wrappedListener.onReady();
	}



	class MockListener extends Listener<Integer> {

		RpcContext rpcCtx;
		Set<ListenerEventContext> seenEventCtxs = new HashSet<>();

		MockListener(RpcContext rpcCtx, ListenerEventContext eventCtx) {
			this.rpcCtx = rpcCtx;
			seenEventCtxs.add(eventCtx);
		}



		void verifyCtxs() {
			assertSame("all listener methods should be executed within the same rpcCtx",
					rpcCtx, grpcModule.rpcContextTracker.getCurrentContext());
			final var eventCtx = grpcModule.listenerEventContextTracker.getCurrentContext();
			assertNotNull(eventCtx);
			assertTrue("each listener method should be executed within a new separate eventCtx",
					seenEventCtxs.add(eventCtx));
		}



		@Override
		public void onMessage(Integer message) {
			verifyCtxs();
		}

		@Override
		public void onHalfClose() {
			verifyCtxs();
		}

		@Override
		public void onCancel() {
			verifyCtxs();
		}

		@Override
		public void onComplete() {
			verifyCtxs();
		}

		@Override
		public void onReady() {
			verifyCtxs();
		}
	}
}
