// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes.tests;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.inject.Guice;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import pl.morgwai.base.concurrent.Awaitable;
import pl.morgwai.base.grpc.scopes.GrpcModule;
import pl.morgwai.base.grpc.scopes.tests.grpc.BackendGrpc;
import pl.morgwai.base.grpc.scopes.tests.grpc.BackendGrpc.BackendStub;



public class ScopedObjectHashServer {



	final Server grpcServer;

	final GrpcModule grpcModule;

	final ScopedObjectHashService service;
	public ScopedObjectHashService getService() { return service; }

	final ManagedChannel backendChannel;



	public ScopedObjectHashServer(int port, String backendTarget) throws IOException {
		grpcModule = new GrpcModule();
		backendChannel = ManagedChannelBuilder
			.forTarget(backendTarget)
			.usePlaintext()
			.build();
		final var backendConnector = BackendGrpc.newStub(ClientInterceptors.intercept(
				backendChannel, grpcModule.nestingClientInterceptor));
		final var injector = Guice.createInjector(grpcModule, (binder) -> {
			binder
				.bind(BackendStub.class)
				.toInstance(backendConnector);
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
				final var terminated = grpcServer.awaitTermination(timeout, unit);
				if ( ! terminated) grpcServer.shutdownNow();
				grpcModule.shutdownAllExecutors();
				return terminated;
			},
			(timeout, unit) -> {
				final var terminated =
						grpcModule.enforceTerminationOfAllExecutors(timeout, unit).isEmpty();
				backendChannel.shutdown();
				return terminated;
			},
			(timeout, unit) -> {
				if ( ! backendChannel.awaitTermination(timeout, unit)) {
					backendChannel.shutdownNow();
					return  false;
				}
				return true;
			}
		);
	}



	public static void main(String[] args) throws Exception {
		ScopedObjectHashService.setLogLevel(Level.FINER);
		for (final var handler: Logger.getLogger("").getHandlers()) handler.setLevel(Level.FINER);
		final var server = new ScopedObjectHashServer(
				Integer.parseInt(args[0]), "localhost:" + Integer.parseInt(args[1]));
		Runtime.getRuntime().addShutdownHook(new Thread(server.grpcServer::shutdown));
		server.grpcServer.awaitTermination();
		server.shutdownAndEnforceTermination(500L);
	}
}
