// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.grpc.scopes.grpc;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;

import javax.persistence.*;

import com.google.inject.Module;
import com.google.inject.*;
import com.google.inject.name.Names;
import io.grpc.*;
import pl.morgwai.base.grpc.scopes.*;
import pl.morgwai.base.grpc.utils.GrpcAwaitable;
import pl.morgwai.base.jul.JulManualResetLogManager;
import pl.morgwai.base.utils.concurrent.Awaitable;
import pl.morgwai.samples.grpc.scopes.data_access.JpaRecordDao;
import pl.morgwai.samples.grpc.scopes.domain.RecordDao;

import static java.util.concurrent.TimeUnit.SECONDS;

import static pl.morgwai.base.jul.JulConfigurator.*;
import static pl.morgwai.samples.grpc.scopes.grpc.RecordStorageService.CONCURRENCY_LEVEL;
import static pl.morgwai.samples.grpc.scopes.grpc.RecordStorageService.JPA_EXECUTOR_NAME;



public class RecordStorageServer {



	/** TCP port to listen on. */
	static final String PORT_ENVVAR = "PORT";
	public static final int DEFAULT_PORT = 6666;

	/** Value for {@link ServerBuilder#maxConnectionIdle(long, TimeUnit)}. */
	static final String MAX_CONNECTION_IDLE_ENVVAR = "MAX_CONNECTION_IDLE_SECONDS";
	static final int DEFAULT_MAX_CONNECTION_IDLE = 60;

	/** Value for {@link ServerBuilder#maxConnectionAge(long, TimeUnit)}. */
	static final String MAX_CONNECTION_AGE_ENVVAR = "MAX_CONNECTION_AGE_SECONDS";
	static final int DEFAULT_MAX_CONNECTION_AGE = 60;

	/** Value for {@link ServerBuilder#maxConnectionAgeGrace(long, TimeUnit)}. */
	static final String MAX_CONNECTION_AGE_GRACE_ENVVAR = "MAX_CONNECTION_AGE_GRACE_SECONDS";
	static final int DEFAULT_MAX_CONNECTION_AGE_GRACE = 180;

	/** Threadpool size for {@link #jpaExecutor}. */
	static final String JPA_EXECUTOR_THREADPOOL_SIZE_ENVVAR = "JPA_EXECUTOR_THREADPOOL_SIZE";
	static final int DEFAULT_JPA_EXECUTOR_THREADPOOL_SIZE = 10;



	final Server recordStorageServer;

	/** Name of the persistence unit defined in {@code persistence.xml} file. */
	static final String PERSISTENCE_UNIT_NAME = "RecordDb";
	final EntityManagerFactory entityManagerFactory;

	/**
	 * JPA operations will be executed on this executor.
	 * Its thread pool size is obtained from {@value #JPA_EXECUTOR_THREADPOOL_SIZE_ENVVAR} env var
	 * and should be at least the size of DB-connection pool size defined in {@code persistence.xml}
	 * ({@code hibernate.c3p0.maxPoolSize} in this case). Note that most JPA implementations are
	 * able to perform many operation in cache and deffer or even completely bypass actual
	 * connection acquisition, so usually the thread pool should be significantly greater than the
	 * connection pool. The exact number should be determined either by load tests or even better by
	 * real usage data.
	 */
	final GrpcContextTrackingExecutor jpaExecutor;



	RecordStorageServer(
		int port,
		int maxConnectionIdleSeconds,
		int maxConnectionAgeSeconds,
		int maxConnectionAgeGraceSeconds,
		int jpaExecutorThreadpoolSize
	) throws Exception {
		final var grpcModule = new GrpcModule();
		final var exeuctorManager = new ExecutorManager(grpcModule.contextBinder);

		entityManagerFactory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		jpaExecutor = exeuctorManager.newContextTrackingExecutor(
			PERSISTENCE_UNIT_NAME + JPA_EXECUTOR_NAME,
			jpaExecutorThreadpoolSize
		);
		log.info("entity manager factory " + PERSISTENCE_UNIT_NAME + " and its associated "
				+ jpaExecutor.getName() + " created successfully");

		final Module jpaModule = (binder) -> {
			binder.bind(EntityManager.class)
				.toProvider(entityManagerFactory::createEntityManager)
				.in(grpcModule.listenerEventScope);
			binder.bind(EntityManagerFactory.class)
				.toInstance(entityManagerFactory);
			binder.bind(GrpcContextTrackingExecutor.class)
				.annotatedWith(Names.named(JPA_EXECUTOR_NAME))
				.toInstance(jpaExecutor);
			binder.bind(RecordDao.class)
				.to(JpaRecordDao.class)
				.in(Scopes.SINGLETON);
			binder.bind(Integer.class)
				.annotatedWith(Names.named(CONCURRENCY_LEVEL))
				.toInstance(jpaExecutorThreadpoolSize + 1);
				// +1 is to account for request message delivery delay
		};
		final var injector = Guice.createInjector(grpcModule, jpaModule);

		final var service = injector.getInstance(RecordStorageService.class);
		recordStorageServer = ServerBuilder.forPort(port)
			.directExecutor()
					// this is just to demonstrate that all slow JPA operations are
					// dispatched to jpaExecutor, so no gRPC thread is ever blocked
			.maxConnectionIdle(maxConnectionIdleSeconds, SECONDS)
			.maxConnectionAge(maxConnectionAgeSeconds, SECONDS)
			.maxConnectionAgeGrace(maxConnectionAgeGraceSeconds, SECONDS)
			.addService(ServerInterceptors.intercept(service, grpcModule.serverInterceptor))
			.build();
		recordStorageServer.start();
		log.info("server started on port " + port);

		Runtime.getRuntime().addShutdownHook(new Thread(
			() -> {
				try {
					log.info("shutting down");
					if ( !Awaitable.awaitMultiple(
						2000L,
						GrpcAwaitable.ofEnforcedTermination(recordStorageServer),
						exeuctorManager.toAwaitableOfEnforcedTermination()
					)) {
						log.warning("some stuff failed to shutdown cleanly :/");
					}
				} catch (InterruptedException ignored) {}
				entityManagerFactory.close();
				log.info("exiting, bye!");
				((JulManualResetLogManager) JulManualResetLogManager.getLogManager()).manualReset();
			}
		));
	}



	public static void main(String[] args) throws Exception {
		addOrReplaceLoggingConfigProperties(Map.of(
				ConsoleHandler.class.getName() + LEVEL_SUFFIX, "FINEST"));
		overrideLogLevelsWithSystemProperties("pl.morgwai", "org.hibernate", "com.mchange");
		new RecordStorageServer(
			getIntFromEnv(PORT_ENVVAR, DEFAULT_PORT),
			getIntFromEnv(MAX_CONNECTION_IDLE_ENVVAR, DEFAULT_MAX_CONNECTION_IDLE),
			getIntFromEnv(MAX_CONNECTION_AGE_ENVVAR, DEFAULT_MAX_CONNECTION_AGE),
			getIntFromEnv(MAX_CONNECTION_AGE_GRACE_ENVVAR, DEFAULT_MAX_CONNECTION_AGE_GRACE),
			getIntFromEnv(JPA_EXECUTOR_THREADPOOL_SIZE_ENVVAR, DEFAULT_JPA_EXECUTOR_THREADPOOL_SIZE)
		).recordStorageServer.awaitTermination();
	}

	static int getIntFromEnv(String envVarName, int defaultValue) {
		final var value = System.getenv(envVarName);
		if (value == null) {
			log.info(envVarName + " unset, using default " + defaultValue);
			return defaultValue;
		}
		return Integer.parseInt(value);
	}



	static {
		System.setProperty(
			JulManualResetLogManager.JUL_LOG_MANAGER_PROPERTY,
			JulManualResetLogManager.class.getName()
		);
	}

	static final Logger log = Logger.getLogger(RecordStorageServer.class.getName());
}
