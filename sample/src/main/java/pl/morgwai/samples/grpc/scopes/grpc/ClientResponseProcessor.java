// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.grpc.scopes.grpc;

import java.util.function.Consumer;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;



/**
 * Prints {@link StoreRecordResponse} messages from {@code storeMultiple} remote call together with
 * RPC- and listenerEvent- scoped counters.
 */
public class ClientResponseProcessor implements Consumer<StoreRecordResponse> {



	static final String RPC_COUNTER = "rpcCounter";
	final Provider<Integer> rpcCounter;

	static final String RESPONSE_COUNTER = "responseCounter";
	final Provider<Integer> responseCounter;



	public void accept(StoreRecordResponse response) {
		System.out.printf(
			"RPC: %4d, response: %4d, requestId: %4d, recordId: %4d%n",
			rpcCounter.get(),
			responseCounter.get(),
			response.getRequestId(),
			response.getRecordId()
		);
	}



	@Inject
	public ClientResponseProcessor(
		@Named(RPC_COUNTER) Provider<Integer> rpcCounter,
		@Named(RESPONSE_COUNTER) Provider<Integer> responseCounter
	) {
		this.rpcCounter = rpcCounter;
		this.responseCounter = responseCounter;
	}
}
