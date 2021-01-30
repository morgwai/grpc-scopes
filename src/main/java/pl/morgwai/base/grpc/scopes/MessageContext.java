/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.grpc.scopes;

import io.grpc.ServerCall;
import pl.morgwai.base.guice.scopes.ServerCallContext;



/**
 * A context of a single message from a request stream.
 * Spans over request observer's <code>onNext(...)</code> method
 * (actually over <code>ServerCall.Listener.onMessage(...)</code> to be precise).<br/>
 * For convenience, a new <code>MessageContext</code> is also spanned over each method of
 * <code>ServerCall.Listener</code>. Within all methods other than <code>onMessage(...)</code>,
 * {@link #getMessage()} will return <code>null</code>. This means that {@link #getMessage()} called
 * within methods implementing unary or server streaming remote procedures, will return
 * <code>null</code> as they are invoked by <code>Listener</code>'s <code>onHalfClose()</code>
 * method, not <code>onMessage(...)</code>.
 *
 * @see GrpcModule#messageScope
 * @see ContextInterceptor#interceptCall(ServerCall, io.grpc.Metadata, io.grpc.ServerCallHandler)
 */
public class MessageContext extends ServerCallContext {



	Object message;
	public Object getMessage() { return message; }



	MessageContext(Object message) { this.message = message; }
}
