# gRPC Guice Scopes

RPC and ListenerCall Guice Scopes for gRPC server



## MAIN USER CLASSES

### [GrpcModule](src/main/java/pl/morgwai/base/grpc/scopes/GrpcModule.java)

gRPC Guice `Scope`s, `ContextTracker`s and some helper methods.


### [ContextTrackingExecutor](https://github.com/morgwai/guice-context-scopes/blob/master/src/main/java/pl/morgwai/base/guice/scopes/ContextTrackingExecutor.java)

A `ThreadPoolExecutor` that upon dispatching automatically updates which thread handles which `RpcContext` and `ListenerCallContext`. Instances should usually be obtained using helper methods
from the above `GrpcModule`.<br/>
(this class actually comes from [guice-context-scopes lib](https://github.com/morgwai/guice-context-scopes) on top of which this one is built)



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
            .addService(ServerInterceptors.intercept(myService, grpcModule.contextInterceptor /* more interceptors */))
            // more services here...
            .build();

        Runtime.getRuntime().addShutdownHook(shutdownHook);
        myServer.start().awaitTermination();
    }

    public static void main(String args[]) throws Exception {
        new MyServer().startAndAwaitTermination(PORT /* more params here... */);
    }

    Server myServer;
    Thread shutdownHook = new Thread(() -> {/* ... */});
    public static final int PORT = 6666;

    // more code here...
}
```



## EXAMPLES

See [sample app](sample)
