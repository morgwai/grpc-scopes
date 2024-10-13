// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes;

import java.util.List;

import com.google.inject.Module;
import com.google.inject.*;
import io.grpc.*;
import pl.morgwai.base.guice.scopes.*;



/**
 * gRPC Guice {@link Scope}s, {@link ContextTracker}s, {@code Interceptor}s and some helper methods.
 * <p>
 * Usually a single app-wide instance is created at an app startup.<br/>
 * In case of servers, gRPC {@link BindableService Services} should be
 * {@link ServerInterceptors#intercept(BindableService, ServerInterceptor...) intercepted} with
 * {@link #serverInterceptor}.<br/>
 * In case of clients, gRPC {@link Channel}s should be
 * {@link ClientInterceptors#intercept(Channel, ClientInterceptor...) intercepted} with either
 * {@link #clientInterceptor} or {@link #nestingClientInterceptor}.</p>
 */
public class GrpcModule implements Module {



	/**
	 * Tracks {@link ListenerEventContext Contexts of Listener events}
	 * (both {@link ServerCall.Listener server} and {@link ClientCall.Listener client}).
	 * @see #listenerEventScope
	 */
	public final ContextTracker<ListenerEventContext> ctxTracker = new ContextTracker<>();

	/**
	 * Scopes {@code Object}s to {@link ListenerEventContext the Contexts of Listener events}
	 * (either {@link ServerCall.Listener server} or {@link ClientCall.Listener client}) and as a
	 * consequence also to the {@code Context}s of the corresponding user inbound
	 * {@link io.grpc.stub.StreamObserver} calls.
	 */
	public final Scope listenerEventScope =
			new ContextScope<>("GrpcModule.listenerEventScope", ctxTracker);

	/**
	 * Scopes {@code Object}s to the {@code Context}s of RPCs (either a {@link ServerRpcContext} or
	 * a {@link ClientRpcContext}).
	 */
	public final Scope rpcScope = new InducedContextScope<>(
		"GrpcModule.rpcScope",
		ctxTracker,
		ListenerEventContext::getRpcContext
	);



	/**
	 * All gRPC {@link BindableService Services} must be
	 * {@link ServerInterceptors#intercept(BindableService, ServerInterceptor...) intercepted} by
	 * this {@code Interceptor}.
	 */
	public final ServerInterceptor serverInterceptor =
			new ServerContextInterceptor(ctxTracker);

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
			new ClientContextInterceptor(ctxTracker, true);

	/**
	 * All {@link Channel client Channels} must be
	 * {@link ClientInterceptors#intercept(Channel, ClientInterceptor...) intercepted} either by
	 * this {@code Interceptor} or by {@link #nestingClientInterceptor}.
	 * This {@code Interceptor} will keep {@link ClientRpcContext}s separate even if they were made
	 * within some enclosing {@link RpcContext}s.
	 */
	public final ClientInterceptor clientInterceptor =
			new ClientContextInterceptor(ctxTracker, false);



	/** Singleton of {@link #ctxTracker}. */
	public final List<ContextTracker<?>> allTrackers = List.of(ctxTracker);

	/** {@code ContextBinder} created with {@link #allTrackers}. */
	public final ContextBinder ctxBinder = new ContextBinder(allTrackers);

	/** Calls {@link ContextTracker#getActiveContexts(List) getActiveContexts(allTrackers)}. */
	public List<TrackableContext<?>> getActiveContexts() {
		return ContextTracker.getActiveContexts(allTrackers);
	}



	static final TypeLiteral<ContextTracker<ListenerEventContext>> CTX_TRACKER_TYPE =
			new TypeLiteral<>() {};
	/** {@code Key} of {@link #ctxTracker}. */
	public static final Key<ContextTracker<ListenerEventContext>> CTX_TRACKER_KEY =
			Key.get(CTX_TRACKER_TYPE);



	/**
	 * Creates infrastructure {@link Binder#bind(Key) bindings}.
	 * Specifically binds the following:
	 * <ul>
	 *   <li>{@link ContextTracker#ALL_TRACKERS_KEY} to {@link #allTrackers}</li>
	 *   <li>{@link #CTX_TRACKER_KEY} to {@link #ctxTracker}</li>
	 *   <li>{@link ListenerEventContext} and {@link RpcContext}  to
	 * 	     {@link Provider}s returning instances current for the calling {@code Thread}</li>
	 * </ul>
	 */
	@Override
	public void configure(Binder binder) {
		binder.bind(ContextTracker.ALL_TRACKERS_KEY).toInstance(allTrackers);
		binder.bind(CTX_TRACKER_KEY).toInstance(ctxTracker);
		binder.bind(ListenerEventContext.class)
			.toProvider(ctxTracker::getCurrentContext);
		binder.bind(RpcContext.class)
			.toProvider(() -> ctxTracker.getCurrentContext().getRpcContext());
	}
}
