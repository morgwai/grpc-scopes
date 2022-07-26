// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.grpc.scopes.grpc;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import com.google.inject.Guice;
import com.google.inject.Module;
import com.google.inject.Scopes;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;

import pl.morgwai.base.concurrent.Awaitable;
import pl.morgwai.base.grpc.scopes.ContextTrackingExecutor;
import pl.morgwai.base.grpc.scopes.GrpcModule;
import pl.morgwai.base.logging.JulManualResetLogManager;
import pl.morgwai.samples.grpc.scopes.data_access.JpaRecordDao;
import pl.morgwai.samples.grpc.scopes.domain.RecordDao;



public class RecordStorageServer {



	static final String PORT_ENVVAR = "PORT";
	public static final int DEFAULT_PORT = 6666;

	static final String MAX_CONNECTION_IDLE_ENVVAR = "MAX_CONNECTION_IDLE_SECONDS";
	static final int DEFAULT_MAX_CONNECTION_IDLE = 30;

	static final String MAX_CONNECTION_AGE_ENVVAR = "MAX_CONNECTION_AGE_SECONDS";
	static final int DEFAULT_MAX_CONNECTION_AGE = 30;

	static final String MAX_CONNECTION_AGE_GRACE_ENVVAR = "MAX_CONNECTION_AGE_GRACE_SECONDS";
	static final int DEFAULT_MAX_CONNECTION_AGE_GRACE = 5;



	final Server recordStorageServer;

	final EntityManagerFactory entityManagerFactory;
	final ContextTrackingExecutor jpaExecutor;
	static final String PERSISTENCE_UNIT_NAME = "RecordDb";  // same as in persistence.xml
	static final int JDBC_CONNECTION_POOL_SIZE = 3;  // same as in persistence.xml



	RecordStorageServer(
			int port,
			int maxConnectionIdleSeconds,
			int maxConnectionAgeSeconds,
			int maxConnectionAgeGraceSeconds) throws Exception {
		final var grpcModule = new GrpcModule();

		entityManagerFactory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		jpaExecutor = grpcModule.newContextTrackingExecutor(
				PERSISTENCE_UNIT_NAME + "JpaExecutor", JDBC_CONNECTION_POOL_SIZE);
		log.info("entity manager factory " + PERSISTENCE_UNIT_NAME
				+ " and its JPA executor created successfully");

		final Module jpaModule = (binder) -> {
			binder.bind(EntityManager.class)
				.toProvider(entityManagerFactory::createEntityManager)
				.in(grpcModule.listenerEventScope);
			binder.bind(EntityManagerFactory.class).toInstance(entityManagerFactory);
			binder.bind(ContextTrackingExecutor.class).toInstance(jpaExecutor);
			binder.bind(RecordDao.class).to(JpaRecordDao.class).in(Scopes.SINGLETON);
		};
		final var injector = Guice.createInjector(grpcModule, jpaModule);

		final var service = injector.getInstance(RecordStorageService.class);
		recordStorageServer = NettyServerBuilder
			.forPort(port)
			.directExecutor()
			.maxConnectionIdle(maxConnectionIdleSeconds, TimeUnit.SECONDS)
			.maxConnectionAge(maxConnectionAgeSeconds, TimeUnit.SECONDS)
			.maxConnectionAgeGrace(maxConnectionAgeGraceSeconds, TimeUnit.SECONDS)
			.addService(ServerInterceptors.intercept(service, grpcModule.serverInterceptor))
			.build();
		recordStorageServer.start();
		log.info("server started on port " + port);

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				log.info("shutting down");
				System.out.println("shutting down, timeout 5s");
				Awaitable.awaitMultiple(
					5l, TimeUnit.SECONDS,
					(timeoutMillis) -> {
						recordStorageServer.shutdown();
						if (recordStorageServer.awaitTermination(
								percent(70, timeoutMillis), TimeUnit.MILLISECONDS)) {
							log.info("gRPC server shutdown completed");
							grpcModule.shutdownAllExecutors();
							return true;
						} else {
							grpcModule.shutdownAllExecutors();
							recordStorageServer.shutdownNow();
							log.warning("gRPC server has NOT shutdown cleanly");
							return recordStorageServer.awaitTermination(
									percent(10, timeoutMillis), TimeUnit.MILLISECONDS);
						}
					},
					(timeoutMillis) -> {
						if (grpcModule.enforceTerminationOfAllExecutors(percent(80, timeoutMillis))
								.isEmpty()) {
							return true;
						}
						return grpcModule.awaitTerminationOfAllExecutors(percent(20, timeoutMillis))
								.isEmpty();
					}
				);
			} catch (InterruptedException ignored) {}
			entityManagerFactory.close();
			log.info("exiting, bye!");
			((JulManualResetLogManager) JulManualResetLogManager.getLogManager()).manualReset();
		}));
	}

	static long percent(int p, long x) { return x > 1l ? x*p/100l : x; }



	public static void main(String[] args) throws Exception {
		new RecordStorageServer(
			getIntFromEnv(PORT_ENVVAR, DEFAULT_PORT),
			getIntFromEnv(MAX_CONNECTION_IDLE_ENVVAR, DEFAULT_MAX_CONNECTION_IDLE),
			getIntFromEnv(MAX_CONNECTION_AGE_ENVVAR, DEFAULT_MAX_CONNECTION_AGE),
			getIntFromEnv(MAX_CONNECTION_AGE_GRACE_ENVVAR, DEFAULT_MAX_CONNECTION_AGE_GRACE)
		).recordStorageServer.awaitTermination();
	}

	static int getIntFromEnv(String envVarName, int defaultValue) {
		String value = System.getenv(envVarName);
		if (value == null) {
			log.info(envVarName + " unset, using default " + defaultValue);
			return defaultValue;
		}
		return Integer.parseInt(value);
	}



	static {
		System.setProperty(
				JulManualResetLogManager.JUL_LOG_MANAGER_PROPERTY,
				JulManualResetLogManager.class.getName());
	}

	static final Logger log = Logger.getLogger(RecordStorageServer.class.getName());
}
