// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.grpc.scopes.grpc;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.persistence.*;

import com.google.inject.Module;
import com.google.inject.*;
import com.google.inject.name.Names;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import pl.morgwai.base.grpc.scopes.GrpcContextTrackingExecutor;
import pl.morgwai.base.grpc.scopes.GrpcModule;
import pl.morgwai.base.grpc.utils.GrpcAwaitable;
import pl.morgwai.base.jul.JulManualResetLogManager;
import pl.morgwai.base.util.concurrent.Awaitable;
import pl.morgwai.samples.grpc.scopes.data_access.JpaRecordDao;
import pl.morgwai.samples.grpc.scopes.domain.RecordDao;

import static pl.morgwai.samples.grpc.scopes.grpc.RecordStorageService.CONCURRENCY_LEVEL;
import static pl.morgwai.samples.grpc.scopes.grpc.RecordStorageService.JPA_EXECUTOR_NAME;



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
	final GrpcContextTrackingExecutor jpaExecutor;
	static final String PERSISTENCE_UNIT_NAME = "RecordDb";  // same as in persistence.xml
	static final int JPA_EXECUTOR_THREADPOOL_SIZE = 3;// corresponding to hibernate.c3p0.maxPoolSize
			// in persistence.xml. Here exactly the same, as non of the operations is likely to
			// benefit from cache usage.



	RecordStorageServer(
		int port,
		int maxConnectionIdleSeconds,
		int maxConnectionAgeSeconds,
		int maxConnectionAgeGraceSeconds
	) throws Exception {
		final var grpcModule = new GrpcModule();

		entityManagerFactory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		jpaExecutor = grpcModule.newContextTrackingExecutor(
			PERSISTENCE_UNIT_NAME + JPA_EXECUTOR_NAME,
			JPA_EXECUTOR_THREADPOOL_SIZE
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
				.toInstance(JPA_EXECUTOR_THREADPOOL_SIZE + 1);
				// +1 is to account for request message delivery delay
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

		Runtime.getRuntime().addShutdownHook(new Thread(
			() -> {
				try {
					log.info("shutting down");
					Awaitable.awaitMultiple(
						5000L,
						GrpcAwaitable.ofEnforcedTermination(recordStorageServer),
						grpcModule.toAwaitableOfEnforcedTerminationOfAllExecutors()
					);
				} catch (InterruptedException ignored) {}
				entityManagerFactory.close();
				log.info("exiting, bye!");
				((JulManualResetLogManager) JulManualResetLogManager.getLogManager()).manualReset();
			}
		));
	}



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
