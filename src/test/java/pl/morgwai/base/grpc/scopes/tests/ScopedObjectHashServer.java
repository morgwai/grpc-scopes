// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes.tests;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.google.inject.Guice;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import pl.morgwai.base.concurrent.Awaitable;
import pl.morgwai.base.grpc.scopes.GrpcModule;



public class ScopedObjectHashServer {



	final Server grpcServer;

	final GrpcModule grpcModule;

	final ScopedObjectHashService service;
	public ScopedObjectHashService getService() { return service; }



	public ScopedObjectHashServer(int port) throws IOException {
		grpcModule = new GrpcModule();
		final var injector = Guice.createInjector(grpcModule, (binder) -> {
			binder
				.bind(RpcScopedService.class)
				.in(grpcModule.rpcScope);
			binder
				.bind(EventScopedService.class)
				.in(grpcModule.listenerEventScope);
		});
		service = injector.getInstance(ScopedObjectHashService.class);
		grpcServer = NettyServerBuilder
			.forPort(port)
			.addService(ServerInterceptors.intercept(service, grpcModule.serverInterceptor))
			.build();
		grpcServer.start();
	}



	public int getPort() {
		return ((InetSocketAddress) grpcServer.getListenSockets().get(0)).getPort();
	}



	public boolean shutdownAndEnforceTermination(long timeoutMillis) throws InterruptedException {
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
