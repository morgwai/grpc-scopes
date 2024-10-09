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
 * Usually a single app-wide instance is created at the app startup.<br/>
 * In case of servers, gRPC {@link BindableService services} should be
 * {@link ServerInterceptors#intercept(BindableService, ServerInterceptor...) intercepted} with
 * {@link #serverInterceptor}.<br/>
 * In case of clients, gRPC {@link Channel}s should be
 * {@link ClientInterceptors#intercept(Channel, ClientInterceptor...) intercepted} with either
 * {@link #clientInterceptor} or {@link #nestingClientInterceptor}.</p>
 */
public class GrpcModule implements Module {



	/**
	 * Allows tracking of {@link ListenerEventContext Contexts of a Listener events}.
	 * @see #listenerEventScope
	 */
	public final ContextTracker<ListenerEventContext> listenerEventContextTracker =
			new ContextTracker<>();

	/**
	 * Scopes objects to the {@link ListenerEventContext Context of a Listener event} (either
	 * {@link io.grpc.ServerCall.Listener server} or {@link io.grpc.ClientCall.Listener client}) and
	 * as a consequence also to the context of the corresponding user inbound
	 * {@link io.grpc.stub.StreamObserver} call.
	 */
	public final Scope listenerEventScope =
			new ContextScope<>("GrpcModule.listenerEventScope", listenerEventContextTracker);

	/**
	 * Scopes objects to the {@code Context} of an RPC (either {@link ServerRpcContext} or
	 * {@link ClientRpcContext}).
	 */
	public final Scope rpcScope = new InducedContextScope<>(
		"GrpcModule.rpcScope",
		listenerEventContextTracker,
		ListenerEventContext::getRpcContext
	);

	/**
	 * Singleton of {@link #listenerEventContextTracker}.
	 * Type {@code List<ContextTracker<?>>} is bound to it in {@link #configure(Binder)} method.
	 */
	public final List<ContextTracker<?>> allTrackers = List.of(listenerEventContextTracker);

	/** {@code ContextBinder} created with {@link #allTrackers}. */
	public final ContextBinder contextBinder = new ContextBinder(allTrackers);

	/** Calls {@link ContextTracker#getActiveContexts(List) getActiveContexts(allTrackers)}. */
	public List<TrackableContext<?>> getActiveContexts() {
		return ContextTracker.getActiveContexts(allTrackers);
	}



	/**
	 * All gRPC {@link BindableService Services} must be
	 * {@link ServerInterceptors#intercept(BindableService, ServerInterceptor...) intercepted} by
	 * this {@code Interceptor}.
	 */
	public final ServerInterceptor serverInterceptor =
			new ServerContextInterceptor(listenerEventContextTracker);

	/**
	 * All {@link Channel client Channels} must be
	 * {@link ClientInterceptors#intercept(Channel, ClientInterceptor...) intercepted} either by
	 * this {@code Interceptor} or by {@link #clientInterceptor}. If a client call was made within
	 * a {@link RpcContext Context} of some enclosing "parent" call (server call or a previous
	 * chained client call), this {@code Interceptor} will join together
	 * {@link ClientRpcContext Context of such call} with its parent {@link RpcContext}, so that
	 * they will share all {@link #rpcScope RPC-scoped} objects.
	 */
	public final ClientInterceptor nestingClientInterceptor =
			new ClientContextInterceptor(listenerEventContextTracker, true);

	/**
	 * All {@link Channel client Channels} must be
	 * {@link ClientInterceptors#intercept(Channel, ClientInterceptor...) intercepted} either by
	 * this {@code Interceptor} or by {@link #nestingClientInterceptor}. This {@code Interceptor}
	 * will keep {@link ClientRpcContext}s separate even if they were made within some enclosing
	 * {@link RpcContext}s.
	 */
	public final ClientInterceptor clientInterceptor =
			new ClientContextInterceptor(listenerEventContextTracker, false);



	static final TypeLiteral<ContextTracker<ListenerEventContext>> listenerEventContextTrackerType =
			new TypeLiteral<>() {};
	static final TypeLiteral<List<ContextTracker<?>>> allTrackersType = new TypeLiteral<>() {};
	/** {@code Key} of {@link #listenerEventContextTracker}. */
	public static final Key<ContextTracker<ListenerEventContext>> listenerEventContextTrackerKey =
			Key.get(listenerEventContextTrackerType);
	/** {@code Key} of {@link #allTrackers}. */
	public static final Key<List<ContextTracker<?>>> allTrackersKey = Key.get(allTrackersType);



	/**
	 * Creates infrastructure bindings.
	 * Specifically binds the following:
	 * <ul>
	 *   <li>{@code List<ContextTracker<?>>} to {@code  allTrackers}</li>
	 *   <li>{@link pl.morgwai.base.guice.scopes.ContextBinder} to {@code contextBinder}</li>
	 *   <li>Their respective types to {@link #listenerEventContextTracker} and both contexts</li>
	 * </ul>
	 */
	@Override
	public void configure(Binder binder) {
		binder.bind(allTrackersKey).toInstance(allTrackers);
		binder.bind(ContextBinder.class).toInstance(contextBinder);
		binder.bind(listenerEventContextTrackerKey).toInstance(listenerEventContextTracker);
		binder.bind(ListenerEventContext.class).toProvider(
				listenerEventContextTracker::getCurrentContext);
		binder.bind(RpcContext.class).toProvider(
				() -> listenerEventContextTracker.getCurrentContext().getRpcContext());
	}
}
