/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.grpc.scopes;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.ServerSideContext;



/**
 * Context of a single call to one of the methods of <code>ServerCall.Listener</code>.
 * Each method of a <code>Listener</code> is executed with a new <code>ListenerCallContext</code>.
 *
 * @see GrpcModule#listenerCallScope corresponding <code>Scope</code>
 * @see <a href="https://grpc.github.io/grpc-java/javadoc/io/grpc/stub/ServerCalls.html">
 *      <code>ServerCalls</code> for relation between method's of <code>Listener</code> and user
 *      code</a>
 */
public class ListenerCallContext extends ServerSideContext<ListenerCallContext> {



	ListenerCallContext(ContextTracker<ListenerCallContext> tracker) {
		super(tracker);
	}
}
