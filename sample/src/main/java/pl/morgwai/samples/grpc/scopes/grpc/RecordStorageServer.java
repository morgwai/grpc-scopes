/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.samples.grpc.scopes.grpc;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Scopes;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;

import pl.morgwai.base.grpc.scopes.GrpcModule;
import pl.morgwai.base.guice.scopes.ContextTrackingExecutor;
import pl.morgwai.samples.grpc.scopes.data_access.JpaRecordDao;
import pl.morgwai.samples.grpc.scopes.domain.RecordDao;



public class RecordStorageServer {



	static final String PERSISTENCE_UNIT_NAME = "RecordDb";
	static final int JDBC_CONNECTION_POOL_SIZE = 5;  // same as in persistence.xml
	EntityManagerFactory entityManagerFactory;
	ContextTrackingExecutor jpaExecutor;

	public static final int PORT = 6666;
	Server recordStorageServer;



	public void startAndAwaitTermination(
			int port, int jdbcConnectionPoolSize, String persistenceUnitName)
			throws IOException, InterruptedException {
		GrpcModule grpcModule = new GrpcModule();

		entityManagerFactory = Persistence.createEntityManagerFactory(persistenceUnitName);
		jpaExecutor = grpcModule.newContextTrackingExecutor(
				persistenceUnitName + "JpaExecutor", jdbcConnectionPoolSize);
		log.info("entity manager factory " + persistenceUnitName
				+ " and its JPA executor created successfully");

		Module jpaModule = (binder) -> {
			binder.bind(EntityManager.class)
				.toProvider(() -> entityManagerFactory.createEntityManager())
				.in(grpcModule.listenerCallScope);
			binder.bind(EntityManagerFactory.class).toInstance(entityManagerFactory);
			binder.bind(ContextTrackingExecutor.class).toInstance(jpaExecutor);
			binder.bind(RecordDao.class).to(JpaRecordDao.class).in(Scopes.SINGLETON);
		};
		Injector injector = Guice.createInjector(grpcModule, jpaModule);

		RecordStorageService service = injector.getInstance(RecordStorageService.class);
		recordStorageServer = ServerBuilder
			.forPort(port)
			.directExecutor()
			.addService(ServerInterceptors.intercept(service, grpcModule.contextInterceptor))
			.build();

		Runtime.getRuntime().addShutdownHook(shutdownHook);
		recordStorageServer.start();
		log.info("server started");
		recordStorageServer.awaitTermination();
		System.out.println("\nserver shutdown...");
	}



	Thread shutdownHook = new Thread(() -> {
		try {
			recordStorageServer.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {}
		System.out.println("gRPC server shutdown completed");

		jpaExecutor.shutdown();
		try {
			jpaExecutor.awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {}
		if ( ! jpaExecutor.isTerminated()) {
			List<Runnable> remianingTasks = jpaExecutor.shutdownNow();
			System.out.println(remianingTasks.size() + " tasks still remaining in jpaExecutor");
		} else {
			System.out.println("jpaExecutor shutdown completed");
		}

		entityManagerFactory.close();
		System.out.println("entity manager factory shutdown completed");
	});



	public static void main(String args[]) throws Exception {
		new RecordStorageServer()
			.startAndAwaitTermination(PORT, JDBC_CONNECTION_POOL_SIZE, PERSISTENCE_UNIT_NAME);
	}



	static final Logger log = Logger.getLogger(RecordStorageServer.class.getName());
}
