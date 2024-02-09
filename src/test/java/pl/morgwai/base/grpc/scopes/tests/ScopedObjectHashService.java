// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes.tests;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.inject.Inject;
import com.google.inject.Provider;
import io.grpc.*;
import io.grpc.Status.Code;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import pl.morgwai.base.grpc.scopes.tests.grpc.BackendGrpc.BackendStub;
import pl.morgwai.base.grpc.scopes.tests.grpc.*;
import pl.morgwai.base.grpc.scopes.tests.grpc.ScopedObjectHashGrpc.ScopedObjectHashImplBase;



public class ScopedObjectHashService extends ScopedObjectHashImplBase {



	@Inject Provider<RpcScopedService> rpcScopedProvider;
	@Inject Provider<EventScopedService> eventScopedProvider;
	@Inject	BackendStub backendConnector;

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

	/** Log of all seen RPC-scoped instances to check for duplicates. */
	final Set<RpcScopedService> rpcScopedLog = new HashSet<>();

	/** Log of all seen event-scoped instances to check for duplicates. */
	final Set<EventScopedService> eventScopedLog = new HashSet<>();



	/** Called at each event. */
	void verifyScoping(
		List<String> errors,
		RpcScopedService rpcScoped,
		EventScopedService eventScoped,
		String event
	) {
		if (rpcScoped != rpcScopedProvider.get()) {
			errors.add(String.format(SCOPING_ERROR, event, "RPC"));
		}
		if (eventScoped != eventScopedProvider.get()) {
			errors.add(String.format(SCOPING_ERROR, event, "event"));
		}
		if ( !eventScopedLog.add(eventScoped)) {
			errors.add(String.format(DUPLICATE_ERROR, event, "event"));
		}
	}

	static final String DUPLICATE_ERROR = "duplicated %2$s scoped object in %1$s";
	static final String SCOPING_ERROR = "scoping failed in %1$s for scope %2$s";



	/** Called once at the beginning of each call. */
	void verifyRpcScopingDuplication(List<String> errors, RpcScopedService rpcScoped, String event)
	{
		if ( !rpcScopedLog.add(rpcScoped)) {
			errors.add(String.format(DUPLICATE_ERROR, event, "RPC"));
		}
	}



	@Override
	public void unary(Request request, StreamObserver<ScopedObjectsHashes> basicResponseObserver) {
		List<String> errors = new ArrayList<>(3);
		final var responseObserver =
				(ServerCallStreamObserver<ScopedObjectsHashes>) basicResponseObserver;
		final var rpcScoped = rpcScopedProvider.get();

		responseObserver.setOnCloseHandler(() -> {
			final var eventScoped = eventScopedProvider.get();
			verifyScoping(errors, rpcScoped, eventScoped, "onClose");
			if (finalizationListener != null) {
				finalizationListener.accept(request.getCallId(), errors);
			}
		});

		responseObserver.setOnCancelHandler(() -> {
			final var eventScoped = eventScopedProvider.get();
			verifyScoping(errors, rpcScoped, eventScoped, "onCancel");
			if ( !cancelExpected) errors.add("onCancel called");
			if (finalizationListener != null) {
				finalizationListener.accept(request.getCallId(), errors);
			}
		});

		responseObserver.onNext(buildResponse("unary", rpcScoped, eventScopedProvider.get()));
		verifyScoping(errors, rpcScoped, eventScopedProvider.get(), "unary");
		verifyRpcScopingDuplication(errors, rpcScoped, "unary");
		if ( !errors.isEmpty()) {
			responseObserver.onError(Status.INTERNAL.asException());
		} else {
			responseObserver.onCompleted();
		}
	}



	/** Sends a response message with hashes of scoped objects from every non-final event. */
	@Override
	public StreamObserver<Request> streaming(
			StreamObserver<ScopedObjectsHashes> basicResponseObserver) {
		log.fine("client streaming call");
		final Integer[] requestIdHolder = {null};
		List<String> errors = new ArrayList<>(5);
		final var responseObserver =
				(ServerCallStreamObserver<ScopedObjectsHashes>) basicResponseObserver;
		final var clientResponseObserver = new SynchronizedStreamObserver<>(basicResponseObserver);
		final var rpcScoped = rpcScopedProvider.get();
		final var completionCounter = new AtomicInteger(2);  // 1 for client, 1 for backend

		responseObserver.setOnCloseHandler(() -> {
			if (log.isLoggable(Level.FINE)) log.fine("call " + requestIdHolder[0] + " completed");
			final var eventScoped = eventScopedProvider.get();
			verifyScoping(errors, rpcScoped, eventScoped, "onClose");
			if (finalizationListener != null) {
				finalizationListener.accept(requestIdHolder[0], errors);
			}
		});

		responseObserver.setOnReadyHandler(() -> {
			final var eventScoped = eventScopedProvider.get();
			verifyScoping(errors, rpcScoped, eventScoped, "onReady");
			clientResponseObserver.onNext(buildResponse("onReady", rpcScoped, eventScoped));
		});

		verifyScoping(errors, rpcScoped, eventScopedProvider.get(), "startCall");
		verifyRpcScopingDuplication(errors, rpcScoped, "streaming");
		if ( !errors.isEmpty()) {
			clientResponseObserver.onError(Status.INTERNAL.asException());
		} else {
			clientResponseObserver.onNext(
					buildResponse("startCall", rpcScoped, eventScopedProvider.get()));
		}

		final var backendResponseObserver = new StreamObserver<Empty>() {

			@Override public void onNext(Empty response) {
				if (log.isLoggable(Level.FINE)) {
					log.fine("backend response on call " + requestIdHolder[0]);
				}
				final var eventScoped = eventScopedProvider.get();
				verifyScoping(errors, rpcScoped, eventScoped, "backend.onNext");
				if ( !errors.isEmpty()) {
					clientResponseObserver.onError(Status.INTERNAL.asException());
				} else {
					clientResponseObserver.onNext(
							buildResponse("backend.onNext", rpcScoped, eventScoped));
				}
			}

			@Override public void onError(Throwable error) {
				if (log.isLoggable(Level.FINE)) {
					log.fine("backend cancellation on call " + requestIdHolder[0]);
				}
				final var eventScoped = eventScopedProvider.get();
				verifyScoping(errors, rpcScoped, eventScoped, "backend.onError");
				errors.add("backend.onError called " + error);
				clientResponseObserver.onError(Status.INTERNAL.asException());
			}

			@Override public void onCompleted() {
				if (log.isLoggable(Level.FINE)) {
					log.fine("backend completion on call " + requestIdHolder[0]);
				}
				final var eventScoped = eventScopedProvider.get();
				verifyScoping(errors, rpcScoped, eventScoped, "backend.onCompleted");
				if ( !errors.isEmpty()) {
					clientResponseObserver.onError(Status.INTERNAL.asException());
				} else {
					clientResponseObserver.onNext(
							buildResponse("backend.onCompleted", rpcScoped, eventScoped));
					if (completionCounter.decrementAndGet() == 0) {
						if (log.isLoggable(Level.FINE)) {
							log.fine("completing call " + requestIdHolder[0]
									+ "from backend completion");
						}
						clientResponseObserver.onCompleted();
					}
				}
			}
		};

		return new StreamObserver<>() {  // client request observer

			@Override public void onNext(Request request) {
				if (log.isLoggable(Level.FINER)) {
					log.finer("client request on call " + request.getCallId());
				}
				if (requestIdHolder[0] == null) {
					requestIdHolder[0] = request.getCallId();
					backendConnector.unary(
							BackendRequest.newBuilder().setCallId(requestIdHolder[0]).build(),
							backendResponseObserver);
				}
				final var eventScoped = eventScopedProvider.get();
				verifyScoping(errors, rpcScoped, eventScoped, "onNext");
				if ( !errors.isEmpty()) {
					clientResponseObserver.onError(Status.INTERNAL.asException());
				} else {
					clientResponseObserver.onNext(
							buildResponse("onNext", rpcScoped, eventScoped));
				}
			}

			@Override public void onError(Throwable error) {
				final var eventScoped = eventScopedProvider.get();
				verifyScoping(errors, rpcScoped, eventScoped, "onError");
				if ( ! (cancelExpected && isCancellation(error))) {
					if (log.isLoggable(Level.FINE)) {
						log.log(Level.FINE, "call " + requestIdHolder[0] + " completed with error",
								error);
					}
					errors.add("onError called " + error);
				} else {
					if (log.isLoggable(Level.FINE)) {
						log.fine("call " + requestIdHolder[0] + " cancelled as expected");
					}
				}
				if (finalizationListener != null) {
					finalizationListener.accept(requestIdHolder[0], errors);
				}
			}

			@Override public void onCompleted() {
				if (log.isLoggable(Level.FINE)) {
					log.fine("client completion on call " + requestIdHolder[0]);
				}
				final var eventScoped = eventScopedProvider.get();
				verifyScoping(errors, rpcScoped, eventScoped, "onCompleted");
				if ( !errors.isEmpty()) {
					clientResponseObserver.onError(Status.INTERNAL.asException());
				} else {
					clientResponseObserver.onNext(
							buildResponse("onCompleted", rpcScoped, eventScoped));
					if (completionCounter.decrementAndGet() == 0) {
						if (log.isLoggable(Level.FINE)) {
							log.fine("completing call " + requestIdHolder[0]
									+ "from client completion");
						}
						clientResponseObserver.onCompleted();
					}
				}
			}
		};
	}



	public static boolean isCancellation(Throwable t) {
		final Status status;
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
		String eventName,
		RpcScopedService rpcScoped,
		EventScopedService eventScoped
	) {
		return ScopedObjectsHashes.newBuilder()
			.setEventName(eventName)
			.setRpcScopedHash(rpcScoped.hashCode())
			.setEventScopedHash(eventScoped.hashCode())
			.build();
	}



	static class SynchronizedStreamObserver<MessageT> implements StreamObserver<MessageT> {

		final StreamObserver<MessageT> wrappedObserver;

		SynchronizedStreamObserver(StreamObserver<MessageT> observerToWrap) {
			wrappedObserver = observerToWrap;
		}

		@Override public synchronized void onNext(MessageT m) { wrappedObserver.onNext(m); }
		@Override public synchronized void onError(Throwable t) { wrappedObserver.onError(t); }
		@Override public synchronized void onCompleted() { wrappedObserver.onCompleted(); }
	}



	static final Logger log = Logger.getLogger(ScopedObjectHashService.class.getName());
}
