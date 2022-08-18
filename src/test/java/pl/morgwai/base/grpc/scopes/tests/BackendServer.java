// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes.tests;

import java.io.IOException;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import pl.morgwai.base.concurrent.Awaitable;
import pl.morgwai.base.grpc.scopes.tests.grpc.BackendGrpc.BackendImplBase;
import pl.morgwai.base.grpc.scopes.tests.grpc.BackendRequest;
import pl.morgwai.base.grpc.scopes.tests.grpc.Empty;
import pl.morgwai.base.grpc.utils.GrpcAwaitable;



public class BackendServer {



	final Server grpcServer;



	public BackendServer(int port) throws IOException {
		grpcServer = NettyServerBuilder
			.forPort(port)
			.addService(new BackendService())
			.build();
		grpcServer.start();
	}



	public int getPort() {
		return grpcServer.getPort();
	}



	public Awaitable.WithUnit toAwaitableOfEnforceTermination() {
		return GrpcAwaitable.ofEnforcedTermination(grpcServer);
	}



	static class BackendService extends BackendImplBase {

		@Override
		public void unary(BackendRequest request, StreamObserver<Empty> responseObserver) {
			responseObserver.onNext(Empty.newBuilder().build());
			responseObserver.onCompleted();
		}
	}



	public static void main(String[] args) throws Exception {
		final var server = new BackendServer(Integer.parseInt(args[0]));
		Runtime.getRuntime().addShutdownHook(new Thread(
			() -> {
				try {
					server.toAwaitableOfEnforceTermination().await(500L);
				} catch (InterruptedException ignored) {}
			}
		));
		server.grpcServer.awaitTermination();
	}
}
