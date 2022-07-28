# gRPC Guice Scopes

RPC and Listener event Guice Scopes for gRPC.<br/>
<br/>
**latest release: [8.0](https://search.maven.org/artifact/pl.morgwai.base/grpc-scopes/8.0/jar)**
([javadoc](https://javadoc.io/doc/pl.morgwai.base/grpc-scopes/8.0))


## OVERVIEW

Provides `rpcScope` and `listenerEventScope` Guice Scopes for both client and server apps. Scopes are built using [guice-context-scopes lib](https://github.com/morgwai/guice-context-scopes) which automatically transfers them to a new thread when dispatching using `ContextTrackingExecutor` (see below).<br/>
Oversimplifying, in case of streaming requests on servers and streaming responses on clients, `listenerEventScope` spans over processing of a single message from the stream, while `rpcScope` spans over the whole RPC. Oversimplifying again, in case of unary requests, these 2 Scopes have roughly the same span.<br/>
See [DZone article](https://dzone.com/articles/combining-grpc-with-guice) for extended high-level explenation.<br/>
<br/>
Technically:
* `ServerCall.Listener` creation in `ServerCallHandler.startCall(...)`, each call to any of `ServerCall.Listener`'s or `ClientCall.Listener`'s methods runs within **a separate instance** of [ListenerEventContext](src/main/java/pl/morgwai/base/grpc/scopes/ListenerEventContext.java).
  * For servers this means that:
    * all callbacks to request `StreamObserver`s returned by methods implementing RPC procedures
    * methods implementing RPC procedures themselves
    * all invocations of callbacks registered via `ServerCallStreamObserver`s
    
    have "separate `listenerEventScope`s".
  * For clients this means that:
    * all callbacks to response `StreamObserver`s supplied as arguments to stub RPC methods
    * all invocations of callbacks registered via `ClientCallStreamObserver`
    
    have "separate `listenerEventScope`s".
* `ServerCallHandler.startCall(...)` and each call to any of the returned `ServerCall.Listener`'s methods run within **the same instance** of [ServerRpcContext](src/main/java/pl/morgwai/base/grpc/scopes/ServerRpcContext.java). This means that:
  * a single given call to a method implementing RPC procedure
  * all callbacks to the request `StreamObserver` returned by this given call
  * all callbacks to handlers registered via this call's `ServerCallStreamObserver`
  
  all share "the same `rpcScope`".
* Each method call to a single given instance of `ClientCall.Listener` run within **the same instance** of [ClientRpcContext](src/main/java/pl/morgwai/base/grpc/scopes/ClientRpcContext.java). This means that:
  * all callbacks to the response `StreamObserver` supplied as an argument to this given call of the stub sRPC method
  * all callbacks to handlers registered via this call's `ClientCallStreamObserver`
  
  all share "the same `rpcScope`".


## MAIN USER CLASSES

### [GrpcModule](src/main/java/pl/morgwai/base/grpc/scopes/GrpcModule.java)
Contains the above `Scope`s, `ContextTracker`s, some helper methods and gRPC interceptors that start the above contexts.

### [ContextTrackingExecutor](src/main/java/pl/morgwai/base/grpc/scopes/ContextTrackingExecutor.java)
An `Executor` (backed by a fixed size `ThreadPoolExecutor` by default) that upon dispatching automatically updates which thread runs within which `RpcContext` and `ListenerEventContext`.<br/>
Instances should usually be created using helper methods from the above `GrpcModule` and configured for named instance injection in user modules.


## USAGE

1. Create an instance of `GrpcModule` and pass it to other modules.
1. Other modules can use `GrpcModule.rpcScope` and `GrpcModule.listenerEventScope` to scope their bindings: `bind(MyComponent.class).to(MyComponentImpl.class).in(grpcModule.rpcScope);`
1. All gRPC service instances added to server must be intercepted with `GrpcModule.serverInterceptor` like the following: `.addService(ServerInterceptors.intercept(myService, grpcModule.contextInterceptor /* more interceptors here... */))`
1. All client `Channel`s must be intercepted with `GrpcModule.clientInterceptor` like the following: `ClientInterceptors.intercept(channel, grpcModule.clientInterceptor)`


Server sample:
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
                myService, grpcModule.serverInterceptor /* more interceptors here... */))
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

Client sample:
```java
public class MyClient {

    public static void main(String[] args) throws Exception {
        final var grpcModule = new GrpcModule();
        final var managedChannel = ManagedChannelBuilder
            .forTarget(TARGET)
            .usePlaintext()
            .build();
        final var channel = ClientInterceptors.intercept(
                managedChannel, grpcModule.clientInterceptor);
        final var stub = MyServiceGrpc.newStub(channel);

        makeAnRpcCall(stub, args);

        shutdown(managedChannel);
    }

    // ...
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
