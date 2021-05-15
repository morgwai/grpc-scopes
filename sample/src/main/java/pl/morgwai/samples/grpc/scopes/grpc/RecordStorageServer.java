// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.grpc.scopes.grpc;

import java.io.IOException;
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
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;

import pl.morgwai.base.grpc.scopes.GrpcModule;
import pl.morgwai.base.guice.scopes.ContextTrackingExecutor;
import pl.morgwai.samples.grpc.scopes.data_access.JpaRecordDao;
import pl.morgwai.samples.grpc.scopes.domain.RecordDao;



public class RecordStorageServer {



	EntityManagerFactory entityManagerFactory;
	ContextTrackingExecutor jpaExecutor;
	static final String PERSISTENCE_UNIT_NAME = "RecordDb";
	static final int JDBC_CONNECTION_POOL_SIZE = 5;  // same as in persistence.xml

	int port;
	static final String PORT_ENV_NAME = "PORT";
	public static final int DEFAULT_PORT = 6666;

	int maxConnectionIdleSeconds;
	static final String MAX_CONNECTION_IDLE_ENV_NAME = "MAX_CONNECTION_IDLE_SECONDS";
	static final int DEFAULT_MAX_CONNECTION_IDLE = 60;

	int maxConnectionAgeMinutes;
	static final String MAX_CONNECTION_AGE_ENV_NAME = "MAX_CONNECTION_AGE_MINUTES";
	static final int DEFAULT_MAX_CONNECTION_AGE = 5;

	int maxConnectionAgeGraceHours;
	static final String MAX_CONNECTION_AGE_GRACE_ENV_NAME = "MAX_CONNECTION_AGE_GRACE_HOURS";
	static final int DEFAULT_MAX_CONNECTION_AGE_GRACE = 24;

	Server recordStorageServer;



	void startAndAwaitTermination() throws IOException, InterruptedException {
		GrpcModule grpcModule = new GrpcModule();

		entityManagerFactory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		jpaExecutor = grpcModule.newContextTrackingExecutor(
				PERSISTENCE_UNIT_NAME + "JpaExecutor", JDBC_CONNECTION_POOL_SIZE);
		log.info("entity manager factory " + PERSISTENCE_UNIT_NAME
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
		recordStorageServer = NettyServerBuilder
			.forPort(port)
			.directExecutor()
			.maxConnectionIdle(maxConnectionIdleSeconds, TimeUnit.SECONDS)
			.maxConnectionAge(maxConnectionAgeMinutes, TimeUnit.MINUTES)
			.maxConnectionAgeGrace(maxConnectionAgeGraceHours, TimeUnit.HOURS)
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
			System.out.println("gRPC server shutdown completed");
		} catch (InterruptedException e) {}
		jpaExecutor.tryShutdownGracefully(5);
		entityManagerFactory.close();
		System.out.println("entity manager factory shutdown completed");
	});



	public static void main(String args[]) throws Exception {
		int port = getIntFromEnv(PORT_ENV_NAME, DEFAULT_PORT);
		int maxConnectionIdleSeconds =
				getIntFromEnv(MAX_CONNECTION_IDLE_ENV_NAME, DEFAULT_MAX_CONNECTION_IDLE);
		int maxConnectionAgeMinutes =
				getIntFromEnv(MAX_CONNECTION_AGE_ENV_NAME, DEFAULT_MAX_CONNECTION_AGE);
		int maxConnectionAgeGraceHours =
				getIntFromEnv(MAX_CONNECTION_AGE_GRACE_ENV_NAME, DEFAULT_MAX_CONNECTION_AGE_GRACE);

		new RecordStorageServer(
			port,
			maxConnectionIdleSeconds,
			maxConnectionAgeMinutes,
			maxConnectionAgeGraceHours
		).startAndAwaitTermination();
	}

	static int getIntFromEnv(String envVarName, int defaultValue) {
		int value = defaultValue;
		try {
			value = Integer.parseInt(System.getenv(envVarName));
			log.info(envVarName + '=' + value);
		} catch (Exception e) {
			log.info(envVarName + " unset or invalid, using default " + defaultValue);
		}
		return value;
	}

	RecordStorageServer(
			int port,
			int maxConnectionIdleSeconds,
			int maxConnectionAgeMinutes,
			int maxConnectionAgeGraceHours) {
		this.port = port;
		this.maxConnectionIdleSeconds = maxConnectionIdleSeconds;
		this.maxConnectionAgeMinutes = maxConnectionAgeMinutes;
		this.maxConnectionAgeGraceHours = maxConnectionAgeGraceHours;
	}



	static final Logger log = Logger.getLogger(RecordStorageServer.class.getName());
}
