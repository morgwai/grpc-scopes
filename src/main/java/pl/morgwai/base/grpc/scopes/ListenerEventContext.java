// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.TrackableContext;



/**
 * Context of a single call to one of the methods of {@link io.grpc.ServerCall.Listener} and
 * listener creation in
 * {@link io.grpc.ServerCallHandler#startCall(io.grpc.ServerCall, io.grpc.Metadata)}.
 * Each such event is executed within a separate new {@code ListenerEventContext} instance.
 *
 * @see GrpcModule#listenerEventScope corresponding <code>Scope</code>
 * @see <a href="https://javadoc.io/doc/pl.morgwai.base/grpc-utils/latest/pl/morgwai/base/grpc/
utils/GrpcServerFlow.html">a simplified overview of relation between methods of
 * Listener and user's request observer</a>
 * @see <a href="https://github.com/grpc/grpc-java/blob/master/stub/src/main/java/io/grpc/stub/
ServerCalls.java">ServerCalls source for detailed info</a>
 */
public class ListenerEventContext extends TrackableContext<ListenerEventContext> {



	ListenerEventContext(ContextTracker<ListenerEventContext> tracker) {
		super(tracker);
	}
}
