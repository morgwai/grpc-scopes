// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes.tests;

import java.util.concurrent.atomic.AtomicInteger;



public class EventScopedService {

	static final AtomicInteger idSequence = new AtomicInteger(0);
	final int id = idSequence.incrementAndGet();

	@Override
	public int hashCode() {
		return id;
	}
}
