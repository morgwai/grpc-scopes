# gRPC Guice Scopes

RPC and Listener event Guice Scopes for gRPC.<br/>
Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0<br/>
<br/>
**latest release: [15.0](https://search.maven.org/artifact/pl.morgwai.base/grpc-scopes/15.0/jar)**
([javadoc](https://javadoc.io/doc/pl.morgwai.base/grpc-scopes/15.0))<br/>
<br/>
See [CHANGES](CHANGES.md) for the summary of changes between releases. If the major version of a subsequent release remains unchanged, it is supposed to be backwards compatible in terms of API and behaviour with previous ones with the same major version (meaning that it should be safe to just blindly update in dependent projects and things should not break under normal circumstances).


## OVERVIEW

Provides `rpcScope` and `listenerEventScope` Guice `Scope`s for both client and server apps.<br/>
Oversimplifying, in case of streaming requests on servers and streaming responses on clients, `listenerEventScope` spans over the processing of a single message from the stream or over a single call to any registered handler (via `setOnReadyHandler(...)`, `setOnCancelHandler(...)` etc), while `rpcScope` spans over a whole given RPC.<br/>
Oversimplifying again, in case of unary inbound, these 2 Scopes have roughly similar span, although most registered callbacks will have a separate `listenerEventScope`.<br/>
See [this DZone article](https://dzone.com/articles/combining-grpc-with-guice) for extended high-level explanation.<br/>
<br/>
Technically:
* A `ServerCall.Listener` creation in `ServerCallHandler.startCall(...)`, a call to any of `ServerCall.Listener`'s methods, a call to any of `ClientCall.Listener`'s methods, each run within **a separate instance** of [ListenerEventContext](https://javadoc.io/doc/pl.morgwai.base/grpc-scopes/latest/pl/morgwai/base/grpc/scopes/ListenerEventContext.html).
  * For servers this means that:
    * all callbacks to request `StreamObserver`s returned by methods implementing RPC procedures,
    * methods implementing RPC procedures themselves,
    * all invocations of handlers registered via `ServerCallStreamObserver`s
    
    have separate `listenerEventScope`s, **EXCEPT** the first call to `onReady()` handler in case of unary requests as it's invoked in the same `Listener` event-handling method as the RPC method (see [the source of gRPC UnaryServerCallListener.onHalfClose()](https://github.com/grpc/grpc-java/blob/v1.60.1/stub/src/main/java/io/grpc/stub/ServerCalls.java#L182-L189) for details).
  * For clients this means that:
    * all callbacks to response `StreamObserver`s supplied as arguments to stub RPC methods,
    * all invocations of `onReady()` handlers registered via `ClientCallStreamObserver`s
    
    have separate `listenerEventScope`s.
* `ServerCallHandler.startCall(...)` and each call to any of the returned `ServerCall.Listener`'s methods run within **the same instance** of [ServerRpcContext](https://javadoc.io/doc/pl.morgwai.base/grpc-scopes/latest/pl/morgwai/base/grpc/scopes/ServerRpcContext.html). This means that:
  * a single given call to a method implementing RPC procedure,
  * all callbacks to the request `StreamObserver` returned by this given call,
  * all invocations of handlers registered via this call's `ServerCallStreamObserver`
  
  all share the same `rpcScope`.
* Each method call to a single given instance of `ClientCall.Listener` run within **the same instance** of [ClientRpcContext](https://javadoc.io/doc/pl.morgwai.base/grpc-scopes/latest/pl/morgwai/base/grpc/scopes/ClientRpcContext.html). This means that:
  * all callbacks to the response `StreamObserver` supplied as an argument to this given call of the stub gRPC method,
  * all invocations of `onReady()` handler registered via this call's `ClientCallStreamObserver`
  
  all share the same `rpcScope`.


## MAIN USER CLASSES

### [GrpcModule](https://javadoc.io/doc/pl.morgwai.base/grpc-scopes/latest/pl/morgwai/base/grpc/scopes/GrpcModule.html)
Contains the above `Scope`s and gRPC `Interceptor`s that start the above `Context`s.

### [ContextTrackingExecutor](https://javadoc.io/doc/pl.morgwai.base/guice-context-scopes/latest/pl/morgwai/base/guice/scopes/ContextTrackingExecutor.html)
Decorated `ExecutorService` that automatically transfers active `Context`s when dispatching task to its worker `Thread`s.

### [ContextBinder](https://javadoc.io/doc/pl.morgwai.base/guice-context-scopes/latest/pl/morgwai/base/guice/scopes/ContextBinder.html)
Binds tasks and callbacks (`Runnable`s, `Callable`s, `Consumer`s etc) to `Context`s that were active at the time of a given binding. This can be used to transfer `Context`s semi-automatically when switching `Thread`s, for example when passing callbacks to async functions.


## USAGE

1. Create a `GrpcModule` instance and pass its `listenerEventScope` and `rpcScope` to other `Module`s as `shortTermScope` and  `longTermScope` respectively (see [DEVELOPING PORTABLE MODULES](https://github.com/morgwai/guice-context-scopes#developing-portable-modules)).
1. Other `Module`s may use the passed `Scope`s in their bindings: `bind(MyComponent.class).to(MyComponentImpl.class).in(longTermScope);`
1. All gRPC `Service`s added to `Server`s must be intercepted with `GrpcModule.serverInterceptor` similarly to the following: `.addService(ServerInterceptors.intercept(myService, grpcModule.serverInterceptor /* more interceptors here... */))`
1. All client `Channel`s must be intercepted with `GrpcModule.clientInterceptor` or `GrpcModule.nestingClientInterceptor` similarly to the following: `ClientInterceptors.intercept(channel, grpcModule.clientInterceptor)`

### Server sample
```java
public class MyServer {

    final Server grpcServer;

    public MyServer(int port /* more params here... */) throws Exception {
        final var grpcModule = new GrpcModule();
        final var injector = Guice.createInjector(
            grpcModule,
            new ThirdPartyModule(grpcModule.listenerEventScope, grpcModule.rpcScope),
            (binder) -> {
                binder.bind(MyComponent.class)
                    .to(MyComponentImpl.class)
                    .in(grpcModule.rpcScope);
                // more bindings here
            }
            /* more modules here... */
        );

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
        final var managedChannel = ManagedChannelBuilder
            .forTarget(args[1])
            .usePlaintext()
            .build();
        EntityManagerFactory factoryToClose = null;
        try {
            final var entityManagerFactory = createEntityManagerFactory(args[0]);
            factoryToClose = entityManagerFactory;
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
            myResponseObserver.awaitCompletion(5, MINUTES);
        } finally {
            managedChannel.shutdown().awaitTermination(5, SECONDS);
            if ( !managedChannel.isTerminated()) {
                System.err.println("channel has NOT shutdown cleanly");
                managedChannel.shutdownNow();
            }
            if (factoryToClose != null) factoryToClose.close();
        }
    }

    // more code here...
}
```

### Transferring `Context`s to callbacks with `ContextBinder`
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
