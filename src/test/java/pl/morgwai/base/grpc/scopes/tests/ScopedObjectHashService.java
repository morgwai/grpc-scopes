// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes.tests;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.Status.Code;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import pl.morgwai.base.grpc.scopes.tests.grpc.Request;
import pl.morgwai.base.grpc.scopes.tests.grpc.ScopedObjectHashGrpc.ScopedObjectHashImplBase;
import pl.morgwai.base.grpc.scopes.tests.grpc.ScopedObjectsHashes;



public class ScopedObjectHashService extends ScopedObjectHashImplBase {



	public static final String RPC_SCOPE = "RPC";
	public static final String EVENT_SCOPE = "listenerEvent";
	@Inject @Named(RPC_SCOPE) Provider<Service> rpcScopedProvider;
	@Inject @Named(EVENT_SCOPE) Provider<Service> eventScopedProvider;

	/**
	 * Whether to treat cancellation as an error (and add to error log of the given call) or as an
	 * expected event. By default false.
	 */
	public void setCancelExpected(boolean cancelExpected) { this.cancelExpected = cancelExpected; }
	boolean cancelExpected = false;

	/**
	 * Sets a callback invoked each time some call is finalized from the server perspective (either
	 * in {@code onClose()} or {@code onCancel()}). The first param will be the call ID and the
	 * second list of errors (scoping or others) that occurred.
	 */
	public void setFinalizationListener(BiConsumer<Integer, List<String>> finalizationListener) {
		this.finalizationListener = finalizationListener;
	}
	BiConsumer<Integer, List<String>> finalizationListener;



	/**
	 * Keeps the log of all RPC-scoped {@link Service} instances to check for duplications
	 */
	Set<Service> rpcScopedLog = new HashSet<>();

	/**
	 * Keeps the log of all event-scoped {@link Service} instances to check for duplications
	 */
	Set<Service> eventScopedLog = new HashSet<>();

	static final String DUPLICATE_ERROR = "duplicated %2$s scoped object in %1$s";
	static final String SCOPING_ERROR = "scoping failed in %1$s for scope %2$s";

	/**
	 * Called at each event.
	 */
	void verifyScoping(List<String> errors, Service rpcScoped, Service eventScoped, String event) {
		if (rpcScoped != rpcScopedProvider.get()) {
			errors.add(String.format(SCOPING_ERROR, event, RPC_SCOPE));
		}
		if (eventScoped != eventScopedProvider.get()) {
			errors.add(String.format(SCOPING_ERROR, event, EVENT_SCOPE));
		}
		if ( ! eventScopedLog.add(eventScoped)) {
			errors.add(String.format(DUPLICATE_ERROR, event, EVENT_SCOPE));
		}
	}

	/**
	 * Called once at the beginning of each call.
	 */
	void verifyRpcScopingDuplication(List<String> errors, Service rpcScoped, String event) {
		if ( ! rpcScopedLog.add(rpcScoped)) {
			errors.add(String.format(DUPLICATE_ERROR, event, RPC_SCOPE));
		}
	}



	@Override
	public void unary(Request request, StreamObserver<ScopedObjectsHashes> respObserver) {
		List<String> errors = new ArrayList<>(3);
		final var responseObserver =
				(ServerCallStreamObserver<ScopedObjectsHashes>) respObserver;
		final var rpcScoped = rpcScopedProvider.get();

		responseObserver.setOnCloseHandler(() -> {
			final var eventScoped = eventScopedProvider.get();
			verifyScoping(errors, rpcScoped, eventScoped, "onClose");
			finalizationListener.accept(request.getCallId(), errors);
		});

		responseObserver.setOnCancelHandler(() -> {
			final var eventScoped = eventScopedProvider.get();
			verifyScoping(errors, rpcScoped, eventScoped, "onCancel");
			if ( ! cancelExpected) errors.add("onCancel called");
			finalizationListener.accept(request.getCallId(), errors);
		});

		responseObserver.onNext(buildResponse("unary", rpcScoped, eventScopedProvider.get()));
		verifyScoping(errors, rpcScoped, eventScopedProvider.get(), "unary");
		verifyRpcScopingDuplication(errors, rpcScoped, "unary");

		if ( ! errors.isEmpty()) {
			responseObserver.onError(Status.INTERNAL.asException());
		} else {
			responseObserver.onCompleted();
		}
	}


	/**
	 * Sends a response message with hashes of scoped {@link Service} objects from every non-final
	 * event.
	 */
	@Override
	public StreamObserver<Request> streaming(StreamObserver<ScopedObjectsHashes> respObserver) {
		final Integer[] requestIdHolder = {null};
		List<String> errors = new ArrayList<>(5);
		final var responseObserver =
				(ServerCallStreamObserver<ScopedObjectsHashes>) respObserver;
		final var rpcScoped = rpcScopedProvider.get();

		responseObserver.setOnCloseHandler(() -> {
			final var eventScoped = eventScopedProvider.get();
			verifyScoping(errors, rpcScoped, eventScoped, "onClose");
			finalizationListener.accept(requestIdHolder[0], errors);
		});

		responseObserver.setOnReadyHandler(() -> {
			final var eventScoped = eventScopedProvider.get();
			verifyScoping(errors, rpcScoped, eventScoped, "onReady");
			responseObserver.onNext(buildResponse("onReady", rpcScoped, eventScoped));
		});

		responseObserver.onNext(buildResponse("startCall", rpcScoped, eventScopedProvider.get()));
		verifyScoping(errors, rpcScoped, eventScopedProvider.get(), "startCall");
		verifyRpcScopingDuplication(errors, rpcScoped, "streaming");

		return new StreamObserver<>() {

			@Override
			public void onNext(Request request) {
				requestIdHolder[0] = request.getCallId();
				final var eventScoped = eventScopedProvider.get();
				verifyScoping(errors, rpcScoped, eventScoped, "onNext");
				if ( ! errors.isEmpty()) {
					responseObserver.onError(Status.INTERNAL.asException());
				} else {
					responseObserver.onNext(buildResponse("onNext", rpcScoped, eventScoped));
				}
			}

			@Override
			public void onError(Throwable t) {
				final var eventScoped = eventScopedProvider.get();
				verifyScoping(errors, rpcScoped, eventScoped, "onError");
				if ( ! (cancelExpected && isCancellation(t))) {
					errors.add("onError called " + t);
				}
				finalizationListener.accept(requestIdHolder[0], errors);
			}

			@Override
			public void onCompleted() {
				final var eventScoped = eventScopedProvider.get();
				verifyScoping(errors, rpcScoped, eventScoped, "onCompleted");
				if ( ! errors.isEmpty()) {
					responseObserver.onError(Status.INTERNAL.asException());
				} else {
					responseObserver.onNext(buildResponse("onCompleted", rpcScoped, eventScoped));
					responseObserver.onCompleted();
				}
			}
		};
	}



	public static boolean isCancellation(Throwable t) {
		Status status;
		if (t instanceof StatusException) {
			status = ((StatusException) t).getStatus();
		} else if (t instanceof StatusRuntimeException) {
			status = ((StatusRuntimeException) t).getStatus();
		} else {
			return false;
		}
		return status.getCode() == Code.CANCELLED;
	}



	static ScopedObjectsHashes buildResponse(
		String eventName, Service rpcScoped, Service eventScoped
	) {
		return ScopedObjectsHashes.newBuilder()
			.setEventName(eventName)
			.setRpcScopedHash(rpcScoped.hashCode())
			.setEventScopedHash(eventScoped.hashCode())
			.build();
	}
}
