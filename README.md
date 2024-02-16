# gRPC Guice Scopes

RPC and Listener event Guice Scopes for gRPC.<br/>
Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0<br/>
<br/>
**latest release: [12.1](https://search.maven.org/artifact/pl.morgwai.base/grpc-scopes/12.1/jar)**
([javadoc](https://javadoc.io/doc/pl.morgwai.base/grpc-scopes/12.1))


## OVERVIEW

Provides `rpcScope` and `listenerEventScope` Guice Scopes for both client and server apps.<br/>
Oversimplifying, in case of streaming requests on servers and streaming responses on clients, `listenerEventScope` spans over the processing of a single message from the stream or over a single call to any registered handler (via `setOnReadyHandler(...)`, `setOnCancelHandler(...)` etc), while `rpcScope` spans over a whole given RPC.<br/>
Oversimplifying again, in case of unary inbound, these 2 Scopes have roughly similar span, although most registered callbacks will have a separate `listenerEventScope`.<br/>
See [this DZone article](https://dzone.com/articles/combining-grpc-with-guice) for extended high-level explanation.<br/>
<br/>
Technically:
* A `ServerCall.Listener` creation in `ServerCallHandler.startCall(...)`, a call to any of `ServerCall.Listener`'s methods, a call to any of `ClientCall.Listener`'s methods, each run within **a separate instance** of [ListenerEventContext](https://javadoc.io/doc/pl.morgwai.base/grpc-scopes/latest/pl/morgwai/base/grpc/scopes/ListenerEventContext.html).
  * For servers this means that:
    * all callbacks to request `StreamObserver`s returned by methods implementing RPC procedures
    * methods implementing RPC procedures themselves
    * all invocations of callbacks registered via `ServerCallStreamObserver`s
    
    have "separate `listenerEventScope`s", **EXCEPT** the first call to `onReady()` handler in case of unary requests as it's invoked in the same `Listener` event-handling method as the RPC method (see [the source of gRPC UnaryServerCallListener.onHalfClose()](https://github.com/grpc/grpc-java/blob/v1.60.1/stub/src/main/java/io/grpc/stub/ServerCalls.java#L182-L189) for details).
  * For clients this means that:
    * all callbacks to response `StreamObserver`s supplied as arguments to stub RPC methods
    * all invocations of callbacks registered via `ClientCallStreamObserver`
    
    have "separate `listenerEventScope`s".
* `ServerCallHandler.startCall(...)` and each call to any of the returned `ServerCall.Listener`'s methods run within **the same instance** of [ServerRpcContext](https://javadoc.io/doc/pl.morgwai.base/grpc-scopes/latest/pl/morgwai/base/grpc/scopes/ServerRpcContext.html). This means that:
  * a single given call to a method implementing RPC procedure
  * all callbacks to the request `StreamObserver` returned by this given call
  * all callbacks to handlers registered via this call's `ServerCallStreamObserver`
  
  all share "the same `rpcScope`".
* Each method call to a single given instance of `ClientCall.Listener` run within **the same instance** of [ClientRpcContext](https://javadoc.io/doc/pl.morgwai.base/grpc-scopes/latest/pl/morgwai/base/grpc/scopes/ClientRpcContext.html). This means that:
  * all callbacks to the response `StreamObserver` supplied as an argument to this given call of the stub sRPC method
  * all callbacks to handlers registered via this call's `ClientCallStreamObserver`
  
  all share "the same `rpcScope`".


## MAIN USER CLASSES

### [GrpcModule](https://javadoc.io/doc/pl.morgwai.base/grpc-scopes/latest/pl/morgwai/base/grpc/scopes/GrpcModule.html)
Contains the above `Scope`s, `ContextTracker`s, some helper methods and gRPC interceptors that start the above contexts.

### [GrpcContextTrackingExecutor](https://javadoc.io/doc/pl.morgwai.base/grpc-scopes/latest/pl/morgwai/base/grpc/scopes/GrpcContextTrackingExecutor.html)
A `ThreadPoolExecutor` that upon dispatching a task, automatically transfers the current `RpcContext` and `ListenerEventContext` to the worker thread.<br/>
Instances should usually be created using helper methods from the above `GrpcModule` and configured for named instance injection in user modules.

### [ContextBinder](https://javadoc.io/doc/pl.morgwai.base/guice-context-scopes/latest/pl/morgwai/base/guice/scopes/ContextBinder.html)
Binds tasks and callbacks (`Runnable`s, `Consumer`s and `BiConsumer`s) to contexts that were active at the time of binding. This can be used to transfer `Context`s **almost** fully automatically when it's not possible to use `GrpcContextTrackingExecutor` when switching threads (for example when providing callbacks as arguments to async functions). See a usage sample below.


## USAGE

1. Create an instance of `GrpcModule` and pass it to other modules.
1. Other modules can use `GrpcModule.rpcScope` and `GrpcModule.listenerEventScope` to scope their bindings: `bind(MyComponent.class).to(MyComponentImpl.class).in(grpcModule.rpcScope);`
1. All gRPC service instances added to server must be intercepted with `GrpcModule.serverInterceptor` like the following: `.addService(ServerInterceptors.intercept(myService, grpcModule.contextInterceptor /* more interceptors here... */))`
1. All client `Channel`s must be intercepted with `GrpcModule.clientInterceptor` or `GrpcModule.nestingClientInterceptor` like the following: `ClientInterceptors.intercept(channel, grpcModule.clientInterceptor)`

### Server sample
```java
public class MyServer {

    final Server grpcServer;

    public MyServer(int port /* more params here... */) throws Exception {
        final var grpcModule = new GrpcModule();
        final Module myModule = (binder) -> {
            binder.bind(MyComponent.class)
                .to(MyComponentImpl.class)
                .in(grpcModule.rpcScope);
            // more bindings here
        };
        // more modules here

        final var injector = Guice.createInjector(grpcModule, myModule /* more modules here... */);
        final var myService = injector.getInstance(MyService.class);
                // myService will get Provider<MyComponent> injected
        // more services here...

        grpcServer = ServerBuilder
            .forPort(port)
            .addService(ServerInterceptors.intercept(
                    myService, grpcModule.serverInterceptor /* more interceptors here... */))
            // more services here...
            .build();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {/* shutdown code here... */}));
        grpcServer.start();
    }

    public static void main(String[] args) throws Exception {
        new MyServer(6666 /* more params here... */).grpcServer.awaitTermination();
    }

    // more code here...
}
```

### Client sample
```java
public class MyClient {

    public static void main(String[] args) throws Exception {
        final var entityManagerFactory = createEntityManagerFactory(args[0]);
        final ManagedChannel managedChannel;
        try {
            managedChannel = ManagedChannelBuilder
                .forTarget(args[1])
                .usePlaintext()
                .build();
        } catch (Throwable t) {
            entityManagerFactory.close();
            throw t;
        }
        try {
            final var grpcModule = new GrpcModule();
            final var channel = ClientInterceptors.intercept(
                    managedChannel, grpcModule.nestingClientInterceptor);
            final var stub = MyServiceGrpc.newStub(channel);
    
            final Module myModule = (binder) -> {
                binder.bind(EntityManagerFactory.class)
                    .toInstance(entityManagerFactory);
                binder.bind(EntityManager.class)
                    .toProvider(entityManagerFactory::createEntityManager)
                    .in(grpcModule.listenerEventScope);
                binder.bind(MyDao.class)
                    .to(MyJpaDao.class)
                    .in(Scopes.SINGLETON);
                // more bindings here
            };
            // more modules here

            final var injector = Guice.createInjector(
                    grpcModule, myModule /* more modules here... */);
            final var myResponseObserver = injector.getInstance(MyResponseObserver.class);
                    // myResponseObserver will get MyDao injected

            stub.myUnaryRequestStreamingResponseProcedure(args[2], myResponseObserver);
            myResponseObserver.awaitCompletion(30, TimeUnit.MINUTES);
        } finally {
            entityManagerFactory.close();
            managedChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            if ( !managedChannel.isTerminated()) {
                System.err.println("channel has NOT shutdown cleanly");
                managedChannel.shutdownNow();
            }
        }
    }

    // more code here...
}
```

### Transferring contexts to callbacks with `ContextBinder`
```java
class MyComponent {

    @Inject ContextBinder ctxBinder;

    void methodThatCallsSomeAsyncMethod(/* ... */) {
        // other code here...
        someAsyncMethod(arg1, /* ... */ argN, ctxBinder.bindToContext((callbackParam) -> {
            // callback code here...
        }));
    }
}
```

### Dependency management
Dependencies of this jar on [guice](https://search.maven.org/artifact/com.google.inject/guice) and [grpc](https://search.maven.org/search?q=g:io.grpc) are declared as optional, so that apps can use any versions of these deps with a compatible API.


## EXAMPLES

See [sample app](sample)
