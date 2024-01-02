// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes.tests;

import java.io.IOException;
import java.util.Map;
import java.util.logging.ConsoleHandler;

import com.google.inject.Guice;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import pl.morgwai.base.grpc.scopes.GrpcModule;
import pl.morgwai.base.grpc.scopes.tests.grpc.BackendGrpc;
import pl.morgwai.base.grpc.scopes.tests.grpc.BackendGrpc.BackendStub;
import pl.morgwai.base.grpc.utils.GrpcAwaitable;
import pl.morgwai.base.utils.concurrent.Awaitable;

import static java.util.logging.Level.FINER;

import static pl.morgwai.base.jul.JulConfigurator.*;



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
		return grpcServer.getPort();
	}



	public boolean shutdownAndEnforceTermination(long timeoutMillis) throws InterruptedException {
		return Awaitable.awaitMultiple(
			timeoutMillis,
			GrpcAwaitable.ofEnforcedTermination(grpcServer),
			grpcModule.toAwaitableOfEnforcedTerminationOfAllExecutors(),
			GrpcAwaitable.ofEnforcedTermination(backendChannel)
		);
	}



	public static void main(String[] args) throws Exception {
		addOrReplaceLoggingConfigProperties(Map.of(
			"pl.morgwai" + LEVEL_SUFFIX , FINER.toString(),
			ConsoleHandler.class.getName(), FINER.toString()
		));
		overrideLogLevelsWithSystemProperties();
		final var server = new ScopedObjectHashServer(
				Integer.parseInt(args[0]), "localhost:" + Integer.parseInt(args[1]));
		Runtime.getRuntime().addShutdownHook(new Thread(
			() -> {
				try {
					server.shutdownAndEnforceTermination(500L);
				} catch (InterruptedException ignored) {}
			}
		));
		server.grpcServer.awaitTermination();
	}
}
