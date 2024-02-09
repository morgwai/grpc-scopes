// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.grpc.scopes.grpc;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.persistence.*;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import pl.morgwai.base.grpc.scopes.GrpcContextTrackingExecutor;
import pl.morgwai.samples.grpc.scopes.domain.RecordDao;
import pl.morgwai.samples.grpc.scopes.domain.RecordEntity;
import pl.morgwai.samples.grpc.scopes.grpc.RecordStorageGrpc.RecordStorageImplBase;

import static pl.morgwai.base.grpc.utils.ConcurrentInboundObserver
		.newSimpleConcurrentServerRequestObserver;



public class RecordStorageService extends RecordStorageImplBase {



	@Inject @Named(JPA_EXECUTOR_NAME) GrpcContextTrackingExecutor jpaExecutor;
	static final String JPA_EXECUTOR_NAME = "JpaExecutor";

	@Inject @Named(CONCURRENCY_LEVEL) int concurrencyLevel;
	static final String CONCURRENCY_LEVEL = "concurrencyLevel";

	@Inject RecordDao dao;

	@Inject Provider<EntityManager> entityManagerProvider;



	@Override
	public void store(Record message, StreamObserver<NewRecordId> responseObserver) {
		jpaExecutor.execute(responseObserver, () -> {
			try {
				executeWithinTx(() -> {
					// EntityManager is message scoped and this is the first time an instance is
					// requested within this scope: a new instance will be provided by
					// entityManagerProvider to create a transaction and stored for a later use
					// within this scope.

					final RecordEntity entity = process(message);
						// for demo purposes we assume that process(...) may be touching other
						// entities, so we execute it within TX also.

					dao.persist(entity);
						// this method will run within the same scope, so dao's
						// entityManagerProvider will provide the same instance of EntityManager
						// that was stored above during transaction starting. Therefore this method
						// will run within transaction started above.

					responseObserver.onNext(NewRecordId.newBuilder().setId(entity.getId()).build());
					responseObserver.onCompleted();
						// Note: gRPC operations are not TX-nal by nature, so it is totally possible
						// that TX is committed, but the connection breaks or client cancels before
						// he actually receives NewRecordId response message.
						// Furthermore, responseObserver methods are async, so it may take a while
						// between onNext(...) and onCompleted() are called here and the moment the
						// messages are even put on the wire. If it is important to minimize
						// inconsistencies, TX should be committed in a callback set with
						// responseObserver.setOnCloseHandler(handler).
				});
			} catch (StatusRuntimeException e) {
				log.fine("client cancelled");
			} catch (Throwable t) {
				log.log(Level.SEVERE, "server error", t);
				responseObserver.onError(Status.INTERNAL.withCause(t).asException());
				if (t instanceof Error) throw (Error) t;
			} finally {
				entityManagerProvider.get().close();
			}
		});
	}



	@Override
	public StreamObserver<StoreRecordRequest> storeMultiple(
		StreamObserver<StoreRecordResponse> responseObserver
	) {
		// we assume that requests are independent from each other so they are processed in parallel
		// by jpaExecutor
		return newSimpleConcurrentServerRequestObserver(
			(ServerCallStreamObserver<StoreRecordResponse>) responseObserver,
			concurrencyLevel,
			(request, responseSubstreamObserver) -> jpaExecutor.execute(responseObserver, () -> {
				try {
					executeWithinTx(() -> {
						final RecordEntity entity = process(request);
						dao.persist(entity);
						responseSubstreamObserver.onNext(
							StoreRecordResponse.newBuilder()
								.setRequestId(request.getRequestId())
								.setRecordId(entity.getId())
								.build()
						);
						responseSubstreamObserver.onCompleted();
					});
				} catch (StatusRuntimeException e) {
					log.fine("client cancelled");
				} catch (Throwable t) {
					log.log(Level.SEVERE, "server error", t);
					responseSubstreamObserver.onError(Status.INTERNAL.withCause(t).asException());
					if (t instanceof Error) throw (Error) t;
				} finally {
					entityManagerProvider.get().close();
				}
			}),
			(error, thisObserver) -> log.fine("client cancelled")
		);
	}



	@Override
	public void getAll(Empty request, StreamObserver<Record> basicResponseObserver) {
		final var responseObserver = (ServerCallStreamObserver<Record>) basicResponseObserver;

		responseObserver.setOnReadyHandler(() -> {
			synchronized (responseObserver) {
				responseObserver.notifyAll();
			}
		});

		jpaExecutor.execute(responseObserver, () -> {
			try {
				// read-only operation: no need for a TX
				final var recordCursor = dao.findAll().iterator();
				while (recordCursor.hasNext()) {
					synchronized (responseObserver) {
						while ( !responseObserver.isReady()) responseObserver.wait();
					}
					responseObserver.onNext(toProto(recordCursor.next()));
				}
				responseObserver.onCompleted();
			} catch (StatusRuntimeException e) {
				log.fine("client cancelled");
			} catch (Throwable t) {
				log.log(Level.SEVERE, "server error", t);
				responseObserver.onError(Status.INTERNAL.withCause(t).asException());
				if (t instanceof Error) throw (Error) t;
			} finally {
				entityManagerProvider.get().close();
			}
		});
	}



	/**
	 * Executes {@code task} in a new {@link EntityTransaction} created by the {@link EntityManager}
	 * of the current {@link pl.morgwai.base.grpc.scopes.ListenerEventContext}.
	 * If the task completes successfully, the transaction is committed, in case any
	 * {@code Exception} is thrown, it is rolled back.
	 */
	void executeWithinTx(ThrowingRunnable task) throws Exception {
		executeWithinTx(entityManagerProvider, task);
	}

	static void executeWithinTx(
		Provider<EntityManager> entityManagerProvider,
		ThrowingRunnable task
	) throws Exception {
		final var tx = entityManagerProvider.get().getTransaction();
		tx.begin();
		try {
			task.run();
			if ( !tx.isActive()) return;
			if (tx.getRollbackOnly()) {
				tx.rollback();
			} else {
				tx.commit();
			}
		} catch (Throwable t) {
			if (tx.isActive()) tx.rollback();
			throw t;
		}
	}

	@FunctionalInterface
	interface ThrowingRunnable {
		void run() throws Exception;
	}



	/**
	 * Processes the request {@code proto} and constructs new DB {@link RecordEntity}.
	 * For demo purposes we assume that this process may touch other entities.
	 * @return new not-persisted entity
	 */
	public static RecordEntity process(Record proto) {
		return new RecordEntity(proto.getContent());
	}

	/**
	 * Processes the request {@code proto} and constructs new DB {@link RecordEntity}.
	 * For demo purposes we assume that this process may touch other entities.
	 * @return new not-persisted entity
	 */
	public static RecordEntity process(StoreRecordRequest proto) {
		return new RecordEntity(proto.getContent());
	}

	/**
	 * Creates a new {@link Record} proto from entity data.
	 * If the {@code entity} is already cached, this is a fast in-memory only operation.
	 */
	public static Record toProto(RecordEntity entity) {
		return Record.newBuilder().setId(entity.getId()).setContent(entity.getContent()).build();
	}



	static final Logger log = Logger.getLogger(RecordStorageService.class.getName());
}
