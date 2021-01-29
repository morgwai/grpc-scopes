/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.grpc.scopes;

import pl.morgwai.base.guice.scopes.ServerCallContext;



/**
 * A context of a given message from a request stream.
 * @see GrpcModule
 * @see ContextInterceptor
 */
public class MessageContext extends ServerCallContext {



	Object message;
	public Object getMessage() { return message; }



	MessageContext(Object message) { this.message = message; }
}
