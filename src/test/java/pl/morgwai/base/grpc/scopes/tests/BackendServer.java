// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes.tests;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import pl.morgwai.base.grpc.scopes.tests.grpc.BackendGrpc.BackendImplBase;
import pl.morgwai.base.grpc.scopes.tests.grpc.BackendRequest;
import pl.morgwai.base.grpc.scopes.tests.grpc.Empty;



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
		return ((InetSocketAddress) grpcServer.getListenSockets().get(0)).getPort();
	}



	public boolean shutdownAndEnforceTermination(long timeoutMillis) throws InterruptedException {
		grpcServer.shutdown();
		try {
			return grpcServer.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS);
		} finally {
			if ( ! grpcServer.isTerminated()) grpcServer.shutdownNow();
		}
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
		Runtime.getRuntime().addShutdownHook(new Thread(server.grpcServer::shutdown));
		server.grpcServer.awaitTermination();
	}
}
