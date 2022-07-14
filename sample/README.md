# Sample app for grpc-scopes library

A simple gRPC service that stores and retrieves records from a DB.


## BUILDING & RUNNING

build: `./mvnw package`

start the server: `java -jar target/grpc-scopes-sample-1.0-SNAPSHOT-executable.jar`

run the client in another terminal: `java -cp target/grpc-scopes-sample-1.0-SNAPSHOT-executable.jar pl.morgwai.samples.grpc.scopes.grpc.RecordStorageClient`

to stop the server press `CTRL`+`C` on its console


## MAIN FILES

### [recordStorage proto](src/main/proto/recordStorage.proto)

### [RecordStorageServer](src/main/java/pl/morgwai/samples/grpc/scopes/grpc/RecordStorageServer.java)

### [RecordStorageService](src/main/java/pl/morgwai/samples/grpc/scopes/grpc/RecordStorageService.java)

### [RecordStorageClient](src/main/java/pl/morgwai/samples/grpc/scopes/grpc/RecordStorageClient.java)
