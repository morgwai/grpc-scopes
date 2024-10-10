// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import org.junit.Test;

import com.google.inject.*;
import pl.morgwai.base.guice.scopes.ContextBinder;

import static org.junit.Assert.*;
import static pl.morgwai.base.grpc.scopes.GrpcModule.CTX_TRACKER_KEY;



public class GrpcModuleTests {



	final GrpcModule grpcModule = new GrpcModule();
	final Injector injector = Guice.createInjector(grpcModule);
	final RpcContext testRpcCtx = new ServerRpcContext(null, null);



	public static class TestComponent {

		@Inject ContextBinder ctxBinder;

		public Runnable bindToCtx(Runnable task) {
			return ctxBinder.bindToContext(task);
		}
	}

	@Test
	public void testInjectingContextBinderWithAllTrackers() throws Exception {
		final var component = injector.getInstance(TestComponent.class);
		assertNotNull("an instance of ContextBinder should be properly injected",
				component.ctxBinder);
		final var testEventCtx = new ListenerEventContext(testRpcCtx, grpcModule.ctxTracker);
		final Runnable testTask = () -> assertSame("testTask should be bound to testEventCtx",
				testEventCtx, grpcModule.ctxTracker.getCurrentContext());
		testEventCtx.executeWithinSelf(() -> component.bindToCtx(testTask)).run();
	}



	@Test
	public void testContextProviders() {
		final var testEventCtx = new ListenerEventContext(
				testRpcCtx, injector.getInstance(CTX_TRACKER_KEY));
		testEventCtx.executeWithinSelf(() ->{
			assertSame("enclosing testEventCtx should be provided",
					testEventCtx, injector.getInstance(ListenerEventContext.class));
			assertSame("testRpcCtx associated with the enclosing testEventCtx should be provided",
					testRpcCtx, injector.getInstance(RpcContext.class));
		});
	}
}
