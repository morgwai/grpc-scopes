# gRPC Guice Scopes

RPC and ListenerEvent Guice Scopes for gRPC server, that are automatically transferred when dispatching work to other threads.<br/>
<br/>
**latest release: [6.0](https://search.maven.org/artifact/pl.morgwai.base/grpc-scopes/6.0/jar)**
([javadoc](https://javadoc.io/doc/pl.morgwai.base/grpc-scopes/6.0))


## OVERVIEW

Provides `rpcScope` and `listenerEventScope` Guice scopes built using [guice-context-scopes lib](https://github.com/morgwai/guice-context-scopes) which automatically transfers them to a new thread when dispatching using `ContextTrackingExecutor` (see below).<br/>
<br/>
Oversimplifying, in case of a streaming client, `listenerEventScope` spans over processing of a single message from client's stream, while `rpcScope` spans over the whole RPC. Oversimplifying again, in case of a unary client, these 2 scopes have roughly the same span.<br/>
More specifically though:
* each call to any of `ServerCall.Listener`'s methods and listener creation in `ServerCallHandler.startCall(...)` run within a separate instance of [ListenerEventContext](src/main/java/pl/morgwai/base/grpc/scopes/ListenerEventContext.java) (hence the name).
* `ServerCallHandler.startCall(...)` and each call to any of the returned `ServerCall.Listener`'s methods run within the same instance of [RpcContext](src/main/java/pl/morgwai/base/grpc/scopes/RpcContext.java).


## MAIN USER CLASSES

### [GrpcModule](src/main/java/pl/morgwai/base/grpc/scopes/GrpcModule.java)
Contains the above `Scope`s, `ContextTracker`s, some helper methods and [gRPC interceptor](src/main/java/pl/morgwai/base/grpc/scopes/ContextInterceptor.java) that starts the above contexts.

### [ContextTrackingExecutor](src/main/java/pl/morgwai/base/grpc/scopes/ContextTrackingExecutor.java)
An `Executor` (backed by a fixed size `ThreadPoolExecutor` by default) that upon dispatching automatically updates which thread runs within which `RpcContext` and `ListenerEventContext`.<br/>
Instances should usually be created using helper methods from the above `GrpcModule` and configured for named instance injection in user modules.


## USAGE

```java
public class MyServer {

    public void startAndAwaitTermination(int port /* more params here... */)
            throws Exception {
        GrpcModule grpcModule = new GrpcModule();
        // more modules here that can now use grpcModule.rpcScope and grpcModule.listenerEventScope
        Injector injector = Guice.createInjector(grpcModule  /* more modules here... */);

        MyService myService = injector.getInstance(MyService.class);
        // more services here...

        myServer = ServerBuilder
            .forPort(port)
            .directExecutor()
            .addService(ServerInterceptors.intercept(
                myService, grpcModule.contextInterceptor /* more interceptors here... */))
            // more services here...
            .build();

        Runtime.getRuntime().addShutdownHook(shutdownHook);
        myServer.start().awaitTermination();
    }

    public static void main(String[] args) throws Exception {
        new MyServer().startAndAwaitTermination(PORT /* more params here... */);
    }

    Server myServer;
    Thread shutdownHook = new Thread(() -> {/* shutdown code here... */});
    public static final int PORT = 6666;

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
		someAsyncMethod(param, (callbackParam) ->
				ContextTrackingExecutor.executeWithinAll(activeCtxList, () -> {
							// callback code
						}
				));
	}
}
```

### Dependency management
Dependencies of this jar on [guice](https://search.maven.org/artifact/com.google.inject/guice), [slf4j-api](https://search.maven.org/artifact/org.slf4j/slf4j-api) and [grpc](https://search.maven.org/search?q=g:io.grpc) are declared as optional, so that apps can use any versions of these deps with compatible API.


## EXAMPLES

See [sample app](sample)
