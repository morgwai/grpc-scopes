/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.samples.grpc.scopes.grpc;

import java.util.concurrent.Callable;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.RollbackException;

import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import pl.morgwai.base.grpc.utils.ConcurrentRequestObserver;
import pl.morgwai.base.guice.scopes.ContextTrackingExecutor;
import pl.morgwai.samples.grpc.scopes.domain.RecordDao;
import pl.morgwai.samples.grpc.scopes.domain.RecordEntity;
import pl.morgwai.samples.grpc.scopes.grpc.RecordStorageGrpc.RecordStorageImplBase;



public class RecordStorageService extends RecordStorageImplBase {



	@Inject
	RecordDao dao;

	@Inject
	ContextTrackingExecutor jpaExecutor;

	@Inject
	Provider<EntityManager> entityManagerProvider;



	@Override
	public void store(Record message, StreamObserver<NewRecordId> responseObserver) {
		jpaExecutor.execute(() -> {
			try {
				RecordEntity entity = process(message);

				performInTx(() -> {  // EntityManager is message scoped and this is the first time
						// an instance is requested within this scope: a new instance will be
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
				if (e.getStatus().getCode() == Code.CANCELLED) {
					log.info("client cancelled the call");
				} else {
					log.severe("server error: " + e);
					e.printStackTrace();
				}
			} catch (Exception e) {
				log.severe("server error: " + e);
				e.printStackTrace();
				responseObserver.onError(Status.INTERNAL.withCause(e).asException());
			} finally {
				entityManagerProvider.get().close();
			}
		});
	}



	@Override
	public StreamObserver<StoreRecordRequest> storeMultiple(
			StreamObserver<StoreRecordResponse> basicResponseObserver) {

		ServerCallStreamObserver<StoreRecordResponse> responseObserver =
				(ServerCallStreamObserver<StoreRecordResponse>) basicResponseObserver;

		ConcurrentRequestObserver<StoreRecordRequest, StoreRecordResponse> requestObserver =
				new ConcurrentRequestObserver<>(responseObserver) {

			@Override
			protected void onRequest(
					StoreRecordRequest request, StreamObserver<StoreRecordResponse> responseObserver
			) {
				jpaExecutor.execute(() -> {
					try {
						RecordEntity entity = process(request);
						performInTx(() -> { dao.persist(entity); return null; });
						responseObserver.onNext(
								StoreRecordResponse.newBuilder()
									.setRecordId(entity.getId())
									.setRequestId(request.getRequestId())
									.build());
						responseObserver.onCompleted();
					} catch (StatusRuntimeException e) {
						if (e.getStatus().getCode() == Code.CANCELLED) {
							log.info("client cancelled the call");
						} else {
							log.severe("server error: " + e);
							e.printStackTrace();
						}
					} catch (Exception e) {
						log.severe("server error: " + e);
						e.printStackTrace();
						responseObserver.onError(Status.INTERNAL.withCause(e).asException());
					} finally {
						entityManagerProvider.get().close();
					}
				});
			}

			@Override
			public void onError(Throwable t) {
				log.info("client error: " + t);
			}
		};

		responseObserver.request(5);
		return requestObserver;
	}



	@Override
	public void getAll(Empty request, StreamObserver<Record> basicResponseObserver) {
		ServerCallStreamObserver<Record> responseObserver =
				(ServerCallStreamObserver<Record>) basicResponseObserver;
		responseObserver.setOnReadyHandler(() -> {
			synchronized (responseObserver) {
				responseObserver.notifyAll();
			}
		});

		jpaExecutor.execute(() -> {
			try {
				for (pl.morgwai.samples.grpc.scopes.domain.RecordEntity record: dao.findAll()) {
					synchronized (responseObserver) {
						while( ! responseObserver.isReady()) responseObserver.wait();
					}
					responseObserver.onNext(toProto(record));
				}
				responseObserver.onCompleted();
			} catch (StatusRuntimeException e) {
				if (e.getStatus().getCode() == Code.CANCELLED) {
					log.info("client cancelled the call");
				} else {
					log.severe("server error: " + e);
					e.printStackTrace();
				}
			} catch (Exception e) {
				log.severe("server error: " + e);
				e.printStackTrace();
				responseObserver.onError(Status.INTERNAL.withCause(e).asException());
			} finally {
				entityManagerProvider.get().close();
			}
		});
	}



	protected <T> T performInTx(Callable<T> operation) throws Exception {
		return performInTx(entityManagerProvider, operation);
	}

	public static <T> T performInTx(
			Provider<EntityManager> entityManagerProvider, Callable<T> operation) throws Exception {
		EntityTransaction tx = entityManagerProvider.get().getTransaction();
		if ( ! tx.isActive()) tx.begin();
		try {
			T result = operation.call();
			if (tx.getRollbackOnly()) throw new RollbackException("tx marked rollbackOnly");
			tx.commit();
			return result;
		} catch (Exception e) {
			if (tx.isActive()) tx.rollback();
			throw e;
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
