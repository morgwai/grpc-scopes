/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.grpc.scopes;

import io.grpc.ServerCall;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.ServerCallContext;



/**
 * A context of a single call to one of the methods of <code>ServerCall.Listener</code>.
 * Each invocation of the <code>Listener</code> method is preceded with a creation of a new
 * <code>ListenerCallContext</code> instance that spans over it. As a consequence a given instance
 * of <code>ListenerCallContext</code> spans over invocation of the request observer's method
 * corresponding to a given <code>Listener</code> method.<br/>
 * Within all <code>Listener</code> methods other than <code>onMessage(...)</code>,
 * {@link #getMessage()} will return <code>null</code>. This means that {@link #getMessage()} called
 * within methods implementing unary or server streaming remote procedures, will return
 * <code>null</code> as they are invoked by <code>Listener.onHalfClose()</code> method,
 * not <code>onMessage(...)</code>.
 *
 * @see GrpcModule#listenerCallScope
 * @see ContextInterceptor#interceptCall(ServerCall, io.grpc.Metadata, io.grpc.ServerCallHandler)
 */
public class ListenerCallContext extends ServerCallContext<ListenerCallContext> {



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
