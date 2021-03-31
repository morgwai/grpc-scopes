/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.grpc.scopes;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.ServerSideContext;



/**
 * Context of a single call to one of the methods of <code>ServerCall.Listener</code>.
 * Each method of a <code>Listener</code> is executed with a new <code>ListenerCallContext</code>.
 * <br/>
 * Within all <code>Listener</code> methods other than <code>onMessage(...)</code>,
 * {@link #getMessage()} will return <code>null</code>. This means that {@link #getMessage()} called
 * within methods implementing unary or server streaming remote procedures, will return
 * <code>null</code> as they are invoked by <code>Listener.onHalfClose()</code> method,
 * not <code>onMessage(...)</code>.
 *
 * @see GrpcModule#listenerCallScope corresponding <code>Scope</code>
 * @see <a href="https://grpc.github.io/grpc-java/javadoc/io/grpc/stub/ServerCalls.html">
 *      <code>ServerCalls</code> for relation between method's of <code>Listener</code> and user
 *      code</a>
 */
public class ListenerCallContext extends ServerSideContext<ListenerCallContext> {



	Object message;
	public Object getMessage() { return message; }



	ListenerCallContext(Object message, ContextTracker<ListenerCallContext> tracker) {
		super(tracker);
		this.message = message;
	}



	ListenerCallContext(ContextTracker<ListenerCallContext> tracker) {
		super(tracker);
	}
}
