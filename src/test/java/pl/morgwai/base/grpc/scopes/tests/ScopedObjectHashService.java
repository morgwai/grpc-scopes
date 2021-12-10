// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes.tests;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import pl.morgwai.base.grpc.scopes.tests.grpc.Empty;
import pl.morgwai.base.grpc.scopes.tests.grpc.ScopedObjectHashGrpc.ScopedObjectHashImplBase;
import pl.morgwai.base.grpc.scopes.tests.grpc.ScopedObjectsHashes;



public class ScopedObjectHashService extends ScopedObjectHashImplBase {



	public static final String RPC_SCOPE = "RPC";
	public static final String EVENT_SCOPE = "listenerEvent";
	@Inject @Named(RPC_SCOPE) Provider<Service> rpcScopedProvider;
	@Inject @Named(EVENT_SCOPE) Provider<Service> eventScopedProvider;

	@Inject Consumer<String> errorReporter;
	Set<Service> rpcScopedLog = new HashSet<>();
	Set<Service> eventScopedLog = new HashSet<>();



	boolean hasScopingError(Service rpcScoped, Service eventScoped, String event) {
		if (rpcScoped != rpcScopedProvider.get()) {
			errorReporter.accept(String.format(SCOPING_ERROR, event, RPC_SCOPE));
			return true;
		}
		if (eventScoped != eventScopedProvider.get()) {
			errorReporter.accept(String.format(SCOPING_ERROR, event, EVENT_SCOPE));
			return true;
		}
		if ( ! eventScopedLog.add(eventScoped)) {
			errorReporter.accept(String.format(DUPLICATE_ERROR, event, EVENT_SCOPE));
			return true;
		}
		return false;
	}



	boolean isRpcScopedObjectDuplicated(Service rpcScoped, String event) {
		if ( ! rpcScopedLog.add(rpcScoped)) {
			errorReporter.accept(String.format(DUPLICATE_ERROR, event, RPC_SCOPE));
			return true;
		}
		return false;
	}



	ScopedObjectsHashes buildResonse(Service rpcScoped, Service eventScoped) {
		return ScopedObjectsHashes.newBuilder()
			.setRpcScopedHash(rpcScoped.hashCode())
			.setEventScopedHash(eventScoped.hashCode())
			.build();
	}



	@Override
	public void unary(Empty request, StreamObserver<ScopedObjectsHashes> responseObserver) {
		final var rpcScoped = rpcScopedProvider.get();
		final var eventScoped = eventScopedProvider.get();
		if (hasScopingError(rpcScoped, eventScoped, "unary")
				|| isRpcScopedObjectDuplicated(rpcScoped, "unary")) {
			responseObserver.onError(Status.INTERNAL.asException());
		} else {
			responseObserver.onNext(buildResonse(rpcScoped, eventScoped));
			responseObserver.onCompleted();
		}
	}



	@Override
	public StreamObserver<Empty> streaming(StreamObserver<ScopedObjectsHashes> respObserver) {
		final var responseObserver =
				(ServerCallStreamObserver<ScopedObjectsHashes>) respObserver;
		final var rpcScoped = rpcScopedProvider.get();
		if (hasScopingError(rpcScoped, eventScopedProvider.get(), "creation")
				|| isRpcScopedObjectDuplicated(rpcScoped, "streaming")) {
			responseObserver.onError(Status.INTERNAL.asException());
			return super.streaming(responseObserver);
		}

		responseObserver.setOnCancelHandler(() -> {
			final var eventScoped = eventScopedProvider.get();
			hasScopingError(rpcScoped, eventScoped, "onCancel");
		});

		responseObserver.setOnCloseHandler(() -> {
			final var eventScoped = eventScopedProvider.get();
			hasScopingError(rpcScoped, eventScoped, "onClose");
		});

		responseObserver.setOnReadyHandler(() -> {
			final var eventScoped = eventScopedProvider.get();
			if (hasScopingError(rpcScoped, eventScoped, "onReady")) {
				responseObserver.onError(Status.INTERNAL.asException());
			} else {
				responseObserver.onNext(buildResonse(rpcScoped, eventScoped));
			}
		});

		responseObserver.onNext(buildResonse(rpcScoped, eventScopedProvider.get()));

		return new StreamObserver<>() {

			@Override
			public void onNext(Empty value) {
				final var eventScoped = eventScopedProvider.get();
				if (hasScopingError(rpcScoped, eventScoped, "onNext")) {
					responseObserver.onError(Status.INTERNAL.asException());
				} else {
					responseObserver.onNext(buildResonse(rpcScoped, eventScoped));
				}
			}

			@Override
			public void onError(Throwable t) {
				errorReporter.accept("onError called " + t);
			}

			@Override
			public void onCompleted() {
				final var eventScoped = eventScopedProvider.get();
				if (hasScopingError(rpcScoped, eventScoped, "onCompleted")) {
					responseObserver.onError(Status.INTERNAL.asException());
				} else {
					responseObserver.onNext(buildResonse(rpcScoped, eventScoped));
					responseObserver.onCompleted();
				}
			}
		};
	}



	static final String DUPLICATE_ERROR = "duplicated %2$s scoped object in %1$s";
	static final String SCOPING_ERROR = "scoping failed in %1$s for scope %2$s";
}
