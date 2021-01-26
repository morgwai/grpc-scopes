/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.grpc.scopes;

import pl.morgwai.base.guice.scopes.CallContext;



/**
 * A <code>CallContext</code> of a given message from a request stream.
 * @see GrpcScopes
 * @see ContextInterceptor
 */
public class MessageContext extends CallContext {



	Object message;
	public Object getMessage() { return message; }



	MessageContext(Object message) { this.message = message; }
}