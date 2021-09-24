# Sample app for grpc-scopes library

A simple gRPC service that stores and retrieves records from a DB.


## BUILDING & RUNNING

build: `mvn package`

start server: `java -jar target/grpc-scopes-sample-1.0-SNAPSHOT-jar-with-dependencies.jar`

run client in another terminal: `java -cp target/grpc-scopes-sample-1.0-SNAPSHOT-jar-with-dependencies.jar pl.morgwai.samples.grpc.scopes.grpc.RecordStorageClient`

to stop the server press `^C` on its console


## MAIN FILES

### [recordStorage proto](src/main/proto/recordStorage.proto)

### [RecordStorageService](src/main/java/pl/morgwai/samples/grpc/scopes/grpc/RecordStorageService.java)

### [RecordStorageClient](src/main/java/pl/morgwai/samples/grpc/scopes/grpc/RecordStorageClient.java)
