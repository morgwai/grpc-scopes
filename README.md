# gRPC Guice Scopes

RPC and Listener event Guice Scopes for gRPC server.<br/>
<br/>
**latest release: [7.0](https://search.maven.org/artifact/pl.morgwai.base/grpc-scopes/7.0/jar)**
([javadoc](https://javadoc.io/doc/pl.morgwai.base/grpc-scopes/7.0))


## OVERVIEW

Provides `rpcScope` and `listenerEventScope` Guice ScopesProvides `rpcScope` and `listenerEventScope` Guice Scopes built using [guice-context-scopes lib](https://github.com/morgwai/guice-context-scopes) which automatically transfers them to a new thread when dispatching using `ContextTrackingExecutor` (see below).<br/>
Oversimplifying, in case of streaming requests, `listenerEventScope` spans over processing of a single message from the request stream, while `rpcScope` spans over the whole RPC. Oversimplifying again, in case of unary requests, these 2 Scopes have roughly the same span.<br/>
More specifically though:
* each call to any of `ServerCall.Listener`'s methods and listener creation in `ServerCallHandler.startCall(...)` run within **a separate instance** of [ListenerEventContext](src/main/java/pl/morgwai/base/grpc/scopes/ListenerEventContext.java) (hence the name). This means that all callbacks to request `StreamObserver` returned by methods implementing RPC procedures, methods implementing RPC procedures themselves and all invocations of callbacks registered via `ServerCallStreamObserver`, run within "separate `listenerEventScope`". 
* `ServerCallHandler.startCall(...)` and each call to any of the returned `ServerCall.Listener`'s methods run within **the same instance** of [RpcContext](src/main/java/pl/morgwai/base/grpc/scopes/RpcContext.java). This means that a single call to a method implementing RPC procedure, all callbacks to request `StreamObserver` returned by this given call to the RPC method and all callbacks registered via `ServerCallStreamObserver` argument of this given call to the RPC method all run within "the same `rpcScope`".


## MAIN USER CLASSES

### [GrpcModule](src/main/java/pl/morgwai/base/grpc/scopes/GrpcModule.java)
Contains the above `Scope`s, `ContextTracker`s, some helper methods and [gRPC interceptor](src/main/java/pl/morgwai/base/grpc/scopes/ContextInterceptor.java) that starts the above contexts.

### [ContextTrackingExecutor](src/main/java/pl/morgwai/base/grpc/scopes/ContextTrackingExecutor.java)
An `Executor` (backed by a fixed size `ThreadPoolExecutor` by default) that upon dispatching automatically updates which thread runs within which `RpcContext` and `ListenerEventContext`.<br/>
Instances should usually be created using helper methods from the above `GrpcModule` and configured for named instance injection in user modules.


## USAGE

1. Create an instance of `GrpcModule` and pass it to other modules.
1. Other modules can use `GrpcModule.rpcScope` and `GrpcModule.listenerEventScope` to scope their bindings: `bind(MyComponent.class).to(MyComponentImpl.class).in(grpcModule.rpcScope);`
1. All gRPC service instances added to server must be intercepted by `GrpcModule.contextInterceptor` like following: `.addService(ServerInterceptors.intercept(myService, grpcModule.contextInterceptor /* more interceptors here... */))` 

Example:
```java
public class MyServer {

    final Server grpcServer;
    final Thread shutdownHook = new Thread(() -> {/* shutdown code here... */});

    public MyServer(int port /* more params here... */) throws Exception {
        final var grpcModule = new GrpcModule();
        final var myModule = (binder) -> {
            binder.bind(MyComponent.class)
                    .to(MyComponentImpl.class)
                    .in(grpcModule.rpcScope);
            // more bindings here
        };
        // more modules here

        final var injector = Guice.createInjector(grpcModule, myModule /* more modules here... */);

        final var myService = injector.getInstance(MyService.class);
        // more services here...

        grpcServer = ServerBuilder
            .forPort(port)
            .directExecutor()
            .addService(ServerInterceptors.intercept(
                myService, grpcModule.contextInterceptor /* more interceptors here... */))
            // more services here...
            .build();

        Runtime.getRuntime().addShutdownHook(shutdownHook);
        grpcServer.start();
    }

    public static void main(String[] args) throws Exception {
        new MyServer(6666 /* more params here... */).grpcServer.awaitTermination();
    }

    // more code here...
}
```

In cases when it's not possible to avoid thread switching without the use of `ContextTrackingExecutor` (for example when passing callbacks to some async calls), static helper methods `getActiveContexts(List<ContextTracker<?>>)` and `executeWithinAll(List<TrackableContext>, Runnable)` defined in `ContextTrackingExecutor` can be used to transfer context manually:

```java
class MyClass {

    @Inject List<ContextTracker<?>> allTrackers;

    void myMethod(Object param) {
        // myMethod code
        var activeCtxList = ContextTrackingExecutor.getActiveContexts(allTrackers);
        someAsyncMethod(
            param,
            (callbackParam) -> ContextTrackingExecutor.executeWithinAll(activeCtxList, () -> {
                // callback code
            })
        );
    }
}
```

### Dependency management
Dependencies of this jar on [guice](https://search.maven.org/artifact/com.google.inject/guice), [slf4j-api](https://search.maven.org/artifact/org.slf4j/slf4j-api) and [grpc](https://search.maven.org/search?q=g:io.grpc) are declared as optional, so that apps can use any versions of these deps with compatible API.


## EXAMPLES

See [sample app](sample)
