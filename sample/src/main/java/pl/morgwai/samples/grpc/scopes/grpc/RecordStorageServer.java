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

import pl.morgwai.base.grpc.scopes.ContextInterceptor;
import pl.morgwai.base.grpc.scopes.GrpcModule;
import pl.morgwai.base.grpc.scopes.GrpcScopes;
import pl.morgwai.base.guice.scopes.ContextTrackingExecutor;
import pl.morgwai.samples.grpc.scopes.data_access.JpaRecordDao;
import pl.morgwai.samples.grpc.scopes.domain.RecordDao;



public class RecordStorageServer {



	public static final String PERSISTENCE_UNIT_NAME = "RecordDb";
	public static final int CONNECTION_POOL_SIZE = 5;  // corresponds to value in persistence.xml
	static EntityManagerFactory entityManagerFactory;
	static ContextTrackingExecutor jpaExecutor;

	static Injector INJECTOR;

	public static final int PORT = 6666;
	static Server recordStorageServer;



	public static void main(String args[]) throws IOException, InterruptedException {
		entityManagerFactory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		jpaExecutor = new ContextTrackingExecutor(
				CONNECTION_POOL_SIZE,
				PERSISTENCE_UNIT_NAME + "JpaExecutor",
				GrpcScopes.MESSAGE_CONTEXT_TRACKER);
		log.info("entity manager factory " + PERSISTENCE_UNIT_NAME
				+ " and its JPA executor created successfully");

		INJECTOR = Guice.createInjector(new GrpcModule(), jpaModule);

		RecordStorageService service = INJECTOR.getInstance(RecordStorageService.class);
		recordStorageServer = ServerBuilder.forPort(PORT).addService(
				ServerInterceptors.intercept(service, new ContextInterceptor())
		).build();

		Runtime.getRuntime().addShutdownHook(shutdownHook);
		recordStorageServer.start();
		log.info("server started");
		recordStorageServer.awaitTermination();
	}



	static Module jpaModule = (binder) -> {
		binder.bind(EntityManager.class)
			.toProvider(() -> entityManagerFactory.createEntityManager())
			.in(GrpcScopes.MESSAGE_SCOPE);
		binder.bind(EntityManagerFactory.class).toInstance(entityManagerFactory);
		binder.bind(ContextTrackingExecutor.class).toInstance(jpaExecutor);
		binder.bind(RecordDao.class).to(JpaRecordDao.class).in(Scopes.SINGLETON);
	};



	static Thread shutdownHook = new Thread(() -> {
		try {
			recordStorageServer.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {}
		System.out.println("recordStorageServer shutdown completed");

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



	static final Logger log = Logger.getLogger(RecordStorageServer.class.getName());
}
