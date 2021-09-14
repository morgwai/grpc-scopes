// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.ServerSideContext;



/**
 * Context of a single call to one of the methods of {@link io.grpc.ServerCall.Listener} and
 * listener creation in
 * {@link io.grpc.ServerCallHandler#startCall(io.grpc.ServerCall, io.grpc.Metadata)}.
 * Each such event is executed within a separate new {@code ListenerEventContext} instance.
 *
 * @see GrpcModule#listenerEventScope corresponding <code>Scope</code>
 * @see <a href="https://gist.github.com/morgwai/6967bcf51b8ba586847c7f1922c99b88">a simplified
 *      overview of relation between methods of <code>Listener</code> and user code</a>
 * @see <a href="https://github.com/grpc/grpc-java/blob/master/stub/src/main/java/io/grpc/stub/
ServerCalls.java">ServerCalls source for detailed info</a>
 */
public class ListenerEventContext extends ServerSideContext<ListenerEventContext> {



	ListenerEventContext(ContextTracker<ListenerEventContext> tracker) {
		super(tracker);
	}
}
