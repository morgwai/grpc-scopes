// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes.tests;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.function.Consumer;

import com.google.inject.Guice;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;

import pl.morgwai.base.concurrent.Awaitable;
import pl.morgwai.base.grpc.scopes.GrpcModule;

import static pl.morgwai.base.grpc.scopes.tests.ScopedObjectHashService.*;



public class ScopedObjectHashServer {



	final Server grpcServer;
	final GrpcModule grpcModule;



	public ScopedObjectHashServer(int port, Consumer<String> errorReporter) throws IOException {
		grpcModule = new GrpcModule();
		final var injector = Guice.createInjector(grpcModule, (binder) -> {
			TypeLiteral<Consumer<String>> errorReporterType = new TypeLiteral<>() {};
			binder.bind(errorReporterType).toInstance(errorReporter);
			binder
				.bind(Service.class).annotatedWith(Names.named(RPC_SCOPE))
				.to(Service.class).in(grpcModule.rpcScope);
			binder
				.bind(Service.class).annotatedWith(Names.named(EVENT_SCOPE))
				.to(Service.class).in(grpcModule.listenerEventScope);
		});
		final var service = injector.getInstance(ScopedObjectHashService.class);
		grpcServer = NettyServerBuilder
			.forPort(port)
			.addService(ServerInterceptors.intercept(service, grpcModule.contextInterceptor))
			.build();
		grpcServer.start();
	}



	public int getPort() {
		return ((InetSocketAddress) grpcServer.getListenSockets().get(0)).getPort();
	}



	public boolean shutdownAndforceTermination(long timeoutMillis) throws InterruptedException {
		return Awaitable.awaitMultiple(
			timeoutMillis,
			(timeout, unit) -> {
				grpcServer.shutdown();
				if (grpcServer.awaitTermination(timeout, unit)) {
					grpcModule.shutdownAllExecutors();
					return true;
				} else {
					grpcModule.shutdownAllExecutors();
					grpcServer.shutdownNow();
					return false;
				}
			},
			(timeout, unit) -> grpcModule.enforceTerminationOfAllExecutors(timeout, unit).isEmpty()
		);
	}
}
