// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes.tests.server.grpc;

import java.net.InetSocketAddress;
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
import pl.morgwai.base.grpc.scopes.tests.server.data_access.JpaRecordDao;
import pl.morgwai.base.grpc.scopes.tests.server.domain.RecordDao;
import pl.morgwai.base.logging.JulManualResetLogManager;



public class RecordStorageServer {



	static final String PORT_ENVVAR = "PORT";
	public static final int DEFAULT_PORT = 6666;

	static final String MAX_CONNECTION_IDLE_ENVVAR = "MAX_CONNECTION_IDLE_SECONDS";
	public static final int DEFAULT_MAX_CONNECTION_IDLE = 30;

	static final String MAX_CONNECTION_AGE_ENVVAR = "MAX_CONNECTION_AGE_SECONDS";
	public static final int DEFAULT_MAX_CONNECTION_AGE = 30;

	static final String MAX_CONNECTION_AGE_GRACE_ENVVAR = "MAX_CONNECTION_AGE_GRACE_SECONDS";
	public static final int DEFAULT_MAX_CONNECTION_AGE_GRACE = 5;



	final Server recordStorageServer;

	final EntityManagerFactory entityManagerFactory;
	public final ContextTrackingExecutor jpaExecutor;
	static final String PERSISTENCE_UNIT_NAME = "RecordDb";  // same as in persistence.xml
	static final int JDBC_CONNECTION_POOL_SIZE = 3;  // same as in persistence.xml

	final GrpcModule grpcModule;



	public RecordStorageServer(
			int port,
			int maxConnectionIdleSeconds,
			int maxConnectionAgeSeconds,
			int maxConnectionAgeGraceSeconds) throws Exception {
		grpcModule = new GrpcModule();

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
			.addService(ServerInterceptors.intercept(service, grpcModule.contextInterceptor))
			.build();
		recordStorageServer.start();
		log.info("server started on port " + getPort());
	}



	public int getPort() {
		return ((InetSocketAddress) recordStorageServer.getListenSockets().get(0)).getPort();
	}



	public boolean shutdownAndforceTermination(long timeoutMillis) throws InterruptedException {
		return Awaitable.awaitMultiple(
			timeoutMillis,
			(timeout) -> {
				recordStorageServer.shutdown();
				if (recordStorageServer.awaitTermination(timeout, TimeUnit.MILLISECONDS)) {
					log.info("gRPC server shutdown completed");
					grpcModule.shutdownAllExecutors();
					return true;
				} else {
					log.warning("gRPC server has NOT shutdown cleanly");
					grpcModule.shutdownAllExecutors();
					recordStorageServer.shutdownNow();
					return false;
				}
			},
			(timeout) -> grpcModule.enforceTerminationOfAllExecutors(timeout).isEmpty(),
			(ignored) -> {
				entityManagerFactory.close();
				log.info("entity manager factory shutdown completed");
				return true;
			}
		);
	}



	public boolean awaitTermination(long timeoutMillis) throws InterruptedException {
		return Awaitable.awaitMultiple(
			timeoutMillis,
			(timeout) -> recordStorageServer.awaitTermination(timeout, TimeUnit.MILLISECONDS),
			(timeout) -> grpcModule.awaitTerminationOfAllExecutors(timeout).isEmpty()
		);
	}



	public static void main(String[] args) throws Exception {
		final var server = new RecordStorageServer(
			getIntFromEnv(PORT_ENVVAR, DEFAULT_PORT),
			getIntFromEnv(MAX_CONNECTION_IDLE_ENVVAR, DEFAULT_MAX_CONNECTION_IDLE),
			getIntFromEnv(MAX_CONNECTION_AGE_ENVVAR, DEFAULT_MAX_CONNECTION_AGE),
			getIntFromEnv(MAX_CONNECTION_AGE_GRACE_ENVVAR, DEFAULT_MAX_CONNECTION_AGE_GRACE)
		);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				if ( ! server.shutdownAndforceTermination(5000l)) server.awaitTermination(500l);
				log.info("exiting, bye!");
				((JulManualResetLogManager) JulManualResetLogManager.getLogManager()).manualReset();
			} catch (InterruptedException ignored) {}
		}));
		server.recordStorageServer.awaitTermination();
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
		// only has effect if this was the main class
		System.setProperty(
				JulManualResetLogManager.JUL_LOG_MANAGER_PROPERTY,
				JulManualResetLogManager.class.getName());
	}

	static final Logger log = Logger.getLogger(RecordStorageServer.class.getName());
}
