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

import pl.morgwai.base.grpc.scopes.ContextTrackingExecutor;
import pl.morgwai.base.grpc.scopes.GrpcModule;
import pl.morgwai.base.logging.JulManualResetLogManager;
import pl.morgwai.samples.grpc.scopes.data_access.JpaRecordDao;
import pl.morgwai.samples.grpc.scopes.domain.RecordDao;



public class RecordStorageServer {



	static final String PORT_ENV_NAME = "PORT";
	public static final int DEFAULT_PORT = 6666;

	static final String MAX_CONNECTION_IDLE_ENV_NAME = "MAX_CONNECTION_IDLE_SECONDS";
	static final int DEFAULT_MAX_CONNECTION_IDLE = 60;

	static final String MAX_CONNECTION_AGE_ENV_NAME = "MAX_CONNECTION_AGE_MINUTES";
	static final int DEFAULT_MAX_CONNECTION_AGE = 5;

	static final String MAX_CONNECTION_AGE_GRACE_ENV_NAME = "MAX_CONNECTION_AGE_GRACE_HOURS";
	static final int DEFAULT_MAX_CONNECTION_AGE_GRACE = 24;



	final Server recordStorageServer;

	final EntityManagerFactory entityManagerFactory;
	final ContextTrackingExecutor jpaExecutor;
	static final String PERSISTENCE_UNIT_NAME = "RecordDb";  // same as in persistence.xml
	static final int JDBC_CONNECTION_POOL_SIZE = 3;  // same as in persistence.xml



	RecordStorageServer(
			int port,
			int maxConnectionIdleSeconds,
			int maxConnectionAgeMinutes,
			int maxConnectionAgeGraceHours) throws Exception {
		final var grpcModule = new GrpcModule();

		entityManagerFactory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		jpaExecutor = grpcModule.newContextTrackingExecutor(
				PERSISTENCE_UNIT_NAME + "JpaExecutor", JDBC_CONNECTION_POOL_SIZE);
		log.info("entity manager factory " + PERSISTENCE_UNIT_NAME
				+ " and its JPA executor created successfully");

		final Module jpaModule = (binder) -> {
			binder.bind(EntityManager.class)
				.toProvider(() -> entityManagerFactory.createEntityManager())
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
			.maxConnectionAge(maxConnectionAgeMinutes, TimeUnit.MINUTES)
			.maxConnectionAgeGrace(maxConnectionAgeGraceHours, TimeUnit.HOURS)
			.addService(ServerInterceptors.intercept(service, grpcModule.contextInterceptor))
			.build();
		recordStorageServer.start();
		log.info("server started on port " + port);

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				recordStorageServer.shutdown().awaitTermination(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {}
			if (recordStorageServer.isTerminated()) {
				log.info("gRPC server shutdown completed");
			} else {
				log.info("gRPC server has NOT shutdown cleanly");
			}
			jpaExecutor.tryShutdownGracefully(5);
			entityManagerFactory.close();
			log.info("entity manager factory shutdown completed");
			((JulManualResetLogManager) JulManualResetLogManager.getLogManager()).manualReset();
		}));
	}



	public static void main(String args[]) throws Exception {
		new RecordStorageServer(
			getIntFromEnv(PORT_ENV_NAME, DEFAULT_PORT),
			getIntFromEnv(MAX_CONNECTION_IDLE_ENV_NAME, DEFAULT_MAX_CONNECTION_IDLE),
			getIntFromEnv(MAX_CONNECTION_AGE_ENV_NAME, DEFAULT_MAX_CONNECTION_AGE),
			getIntFromEnv(MAX_CONNECTION_AGE_GRACE_ENV_NAME, DEFAULT_MAX_CONNECTION_AGE_GRACE)
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
		System.setProperty("java.util.logging.manager", JulManualResetLogManager.class.getName());
	}

	static final Logger log = Logger.getLogger(RecordStorageServer.class.getName());
}
