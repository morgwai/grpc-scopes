// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import com.google.inject.*;
import io.grpc.*;
import pl.morgwai.base.guice.scopes.*;



/**
 * gRPC Guice {@link Scope}s, {@code Interceptor}s and a {@link ContextBinder}.
 * Usually at the startup of an app a single app-wide instance is created and its members
 * {@link #listenerEventScope} and {@link #rpcScope} are passed to other
 * {@link com.google.inject.Module}s to scope their bindings.
 * <p>
 * In order for the above {@link Scope}s to work, gRPC {@link BindableService Services} must be
 * {@link ServerInterceptors#intercept(BindableService, ServerInterceptor...) intercepted} with
 * {@link #serverInterceptor}, in case of servers. In case of clients, gRPC {@link Channel}s must be
 * {@link ClientInterceptors#intercept(Channel, ClientInterceptor...) intercepted} with either
 * {@link #clientInterceptor} or {@link #nestingClientInterceptor}.</p>
 * @see <a href="https://github.com/morgwai/guice-context-scopes#developing-portable-modules">
 *     Developing portable Modules</a>
 */
public class GrpcModule extends ScopeModule {



	/**
	 * Scopes {@code Object}s to {@link ListenerEventContext the Contexts of Listener events}
	 * (either {@link ServerCall.Listener server} or {@link ClientCall.Listener client}) and as a
	 * consequence also to the {@code Context}s of the corresponding user inbound
	 * {@link io.grpc.stub.StreamObserver} calls.
	 */
	public final ContextScope<ListenerEventContext> listenerEventScope =
			newContextScope("GrpcModule.listenerEventScope", ListenerEventContext.class);

	/**
	 * Scopes {@code Object}s to the {@code Context}s of RPCs (either a {@link ServerRpcContext} or
	 * a {@link ClientRpcContext}).
	 */
	public final Scope rpcScope = newInducedContextScope(
		"GrpcModule.rpcScope",
		RpcContext.class,
		listenerEventScope,
		ListenerEventContext::getRpcContext
	);

	public final ContextBinder ctxBinder = newContextBinder();



	/**
	 * All gRPC {@link BindableService Services} must be
	 * {@link ServerInterceptors#intercept(BindableService, ServerInterceptor...) intercepted} by
	 * this {@code Interceptor}.
	 */
	public final ServerInterceptor serverInterceptor =
			new ServerContextInterceptor(listenerEventScope.tracker);

	/**
	 * All {@link Channel client Channels} must be
	 * {@link ClientInterceptors#intercept(Channel, ClientInterceptor...) intercepted} either by
	 * this {@code Interceptor} or by {@link #clientInterceptor}.
	 * If a client call was made within a {@link RpcContext Context} of some enclosing "parent" call
	 * (server call or a previous chained client call), this {@code Interceptor} will join together
	 * {@link ClientRpcContext the Context of such call} with its parent {@link RpcContext}, so that
	 * they will share all {@link #rpcScope RPC-scoped} {@code Object}s.
	 */
	public final ClientInterceptor nestingClientInterceptor =
			new ClientContextInterceptor(listenerEventScope.tracker, true);

	/**
	 * All {@link Channel client Channels} must be
	 * {@link ClientInterceptors#intercept(Channel, ClientInterceptor...) intercepted} either by
	 * this {@code Interceptor} or by {@link #nestingClientInterceptor}.
	 * This {@code Interceptor} will keep {@link ClientRpcContext}s separate even if they were made
	 * within some enclosing {@link RpcContext}s.
	 */
	public final ClientInterceptor clientInterceptor =
			new ClientContextInterceptor(listenerEventScope.tracker, false);
}
