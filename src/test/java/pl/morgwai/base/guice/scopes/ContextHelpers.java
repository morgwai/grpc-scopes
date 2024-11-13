// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.guice.scopes;

import com.google.inject.Key;
import com.google.inject.Provider;



public interface ContextHelpers {

	static <T> T produceIfAbsent(InjectionContext ctx, Key<T> key, Provider<T> producer) {
		return ctx.produceIfAbsent(key, producer);
	}
}
