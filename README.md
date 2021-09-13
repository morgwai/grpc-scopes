# gRPC Guice Scopes

RPC and ListenerCall Guice Scopes for gRPC server, that are automatically transferred when dispatching work to other threads.<br/>
<br/>
**latest release: [1.0-alpha6](https://search.maven.org/artifact/pl.morgwai.base/grpc-scopes/1.0-alpha6/jar)**



## OVERVIEW

Provides `rpcScope` and `listenerCallScope` Guice scopes built using [guice-context-scopes lib](https://github.com/morgwai/guice-context-scopes) which automatically transfers them to a new thread when dispatching using [ContextTrackingExecutor](https://github.com/morgwai/guice-context-scopes/blob/master/src/main/java/pl/morgwai/base/guice/scopes/ContextTrackingExecutor.java).<br/>
<br/>
Oversimplifying, in case of a streaming client, `listenerCallScope` spans over processing of a single message from client's stream, while `rpcScope` spans over a whole given RPC. Oversimplifying again, in case of a unary client, these 2 scopes have roughly the same span.<br/>
More specifically though:
* each call to any of `ServerCall.Listener`'s methods runs within a separate instance of [ListenerCallContext](src/main/java/pl/morgwai/base/grpc/scopes/ListenerCallContext.java) (hence the name)
* `ServerCallHandler.startCall(...)` and each call to any of `ServerCall.Listener`'s methods from the same RPC run within the same instance of [RpcContext](src/main/java/pl/morgwai/base/grpc/scopes/RpcContext.java)



## MAIN USER CLASSES

### [GrpcModule](src/main/java/pl/morgwai/base/grpc/scopes/GrpcModule.java)

Contains the above `Scope`s, `ContextTracker`s, some helper methods and [gRPC interceptor](src/main/java/pl/morgwai/base/grpc/scopes/ContextInterceptor.java) that starts the above contexts.


### [ContextTrackingExecutor](https://github.com/morgwai/guice-context-scopes/blob/master/src/main/java/pl/morgwai/base/guice/scopes/ContextTrackingExecutor.java)

A `ThreadPoolExecutor` that upon dispatching automatically updates which thread handles which `RpcContext` and `ListenerCallContext`. Instances should usually be obtained using helper methods
from the above `GrpcModule`.<br/>
(this class actually comes from [guice-context-scopes lib](https://github.com/morgwai/guice-context-scopes))



## USAGE

```java
public class MyServer {

    public void startAndAwaitTermination(int port  /* more params here... */)
            throws IOException, InterruptedException {
        GrpcModule grpcModule = new GrpcModule();
        // more modules here that can now use grpcModule.rpcScope and grpcModule.listenerCallScope
        Injector injector = Guice.createInjector(grpcModule  /* more modules here... */);

        MyService myService = injector.getInstance(MyService.class);
        // more services here...

        myServer = ServerBuilder
            .forPort(port)
            .directExecutor()
            .addService(ServerInterceptors.intercept(
                myService, grpcModule.contextInterceptor /* more interceptors */))
            // more services here...
            .build();

        Runtime.getRuntime().addShutdownHook(shutdownHook);
        myServer.start().awaitTermination();
    }

    public static void main(String args[]) throws Exception {
        new MyServer().startAndAwaitTermination(PORT /* more params here... */);
    }

    Server myServer;
    Thread shutdownHook = new Thread(() -> {/* shutdown code here... */});
    public static final int PORT = 6666;

    // more code here...
}
```



## EXAMPLES

See [sample app](sample)
