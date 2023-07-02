// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.grpc.scopes.grpc;

import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.RollbackException;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import pl.morgwai.base.grpc.scopes.ContextTrackingExecutor;
import pl.morgwai.samples.grpc.scopes.domain.RecordDao;
import pl.morgwai.samples.grpc.scopes.domain.RecordEntity;
import pl.morgwai.samples.grpc.scopes.grpc.RecordStorageGrpc.RecordStorageImplBase;

import static pl.morgwai.base.grpc.utils.ConcurrentInboundObserver
		.newSimpleConcurrentServerRequestObserver;



public class RecordStorageService extends RecordStorageImplBase {



	@Inject RecordDao dao;
	@Inject ContextTrackingExecutor jpaExecutor;
	@Inject Provider<EntityManager> entityManagerProvider;



	@Override
	public void store(Record message, StreamObserver<NewRecordId> responseObserver) {
		jpaExecutor.execute(responseObserver, () -> {
			try {
				final RecordEntity entity = process(message);

				executeWithinTx(() -> {  // EntityManager is message scoped and this is the first
						// time an instance is requested within this scope: a new instance will be
						// provided by entityManagerProvider to create a transaction and stored for
						// a later use within this scope.

					dao.persist(entity); // this method will run within the same scope, so dao's
						// entityManagerProvider will provide the same instance of EntityManager
						// that was stored above during transaction starting. Therefore this method
						// will run within transaction started above.

					return null;
				});
				responseObserver.onNext(NewRecordId.newBuilder().setId(entity.getId()).build());
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



	@Override
	public StreamObserver<StoreRecordRequest> storeMultiple(
			StreamObserver<StoreRecordResponse> basicResponseObserver) {
		final var responseObserver =
				(ServerCallStreamObserver<StoreRecordResponse>) basicResponseObserver;
		return newSimpleConcurrentServerRequestObserver(
			responseObserver,
			jpaExecutor.getPoolSize() + 1,  // +1 is to account for request message delivery delay
			(request, individualObserver) -> jpaExecutor.execute(responseObserver, () -> {
				try {
					final RecordEntity entity = process(request);
					executeWithinTx(() -> { dao.persist(entity); return null; });
					individualObserver.onNext(
							StoreRecordResponse.newBuilder()
								.setRequestId(request.getRequestId())
								.setRecordId(entity.getId())
								.build());
					individualObserver.onCompleted();
				} catch (StatusRuntimeException e) {
					log.fine("client cancelled");
				} catch (Throwable t) {
					log.log(Level.SEVERE, "server error", t);
					individualObserver.onError(Status.INTERNAL.withCause(t).asException());
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
				for (var record: dao.findAll()) {
					synchronized (responseObserver) {
						while( !responseObserver.isReady()) responseObserver.wait();
					}
					responseObserver.onNext(toProto(record));
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



	protected <T> T executeWithinTx(Callable<T> operation) throws Exception {
		return executeWithinTx(entityManagerProvider, operation);
	}

	public static <T> T executeWithinTx(
			Provider<EntityManager> entityManagerProvider, Callable<T> operation) throws Exception {
		EntityTransaction tx = entityManagerProvider.get().getTransaction();
		if ( !tx.isActive()) tx.begin();
		try {
			T result = operation.call();
			if (tx.getRollbackOnly()) throw new RollbackException("tx marked rollbackOnly");
			tx.commit();
			return result;
		} catch (Throwable t) {
			if (tx.isActive()) tx.rollback();
			throw t;
		}
	}



	public static RecordEntity process(Record proto) {
		// NOTE:  JPA in this project is used only for demo purposes.
		// Unless some domain logic needs to be involved before storing the record or before sending
		// response (unlike here), converting a proto to an entity does not make sense, as it only
		// adds overhead.
		return new RecordEntity(proto.getContent());
	}

	public static RecordEntity process(StoreRecordRequest proto) {
		return new RecordEntity(proto.getContent());
	}

	public static Record toProto(RecordEntity entity) {
		return Record.newBuilder().setId(entity.getId()).setContent(entity.getContent()).build();
	}



	static final Logger log = Logger.getLogger(RecordStorageService.class.getName());
}
