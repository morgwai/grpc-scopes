// Copyright 2022 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes.tests;

import java.util.HashSet;
import java.util.Set;

import pl.morgwai.base.grpc.scopes.*;

import static org.junit.Assert.*;



public class ContextVerifier {



	final GrpcModule grpcModule;
	final RpcContext rpcCtx;
	final Set<ListenerEventContext> seenEventCtxs = new HashSet<>();



	public ContextVerifier(GrpcModule grpcModule, RpcContext rpcCtx) {
		this.grpcModule = grpcModule;
		this.rpcCtx = rpcCtx;
	}



	public void verifyCtxs() {
		final var eventCtx = grpcModule.listenerEventContextTracker.getCurrentContext();
		assertNotNull("event context should be started",
				eventCtx);
		assertTrue("each listener method should be executed within a new separate eventCtx",
				seenEventCtxs.add(eventCtx));
		assertSame("all listener methods should be executed within the same rpcCtx",
				rpcCtx, eventCtx.getRpcContext());
	}



	public void add(ListenerEventContext eventCtx) {
		seenEventCtxs.add(eventCtx);
	}
}
