package com.example.throttling.store;

import java.util.function.Function;

/**
 * Key-value storage for rate limit state, with a single atomic operation.
 *
 * <p>A store knows nothing about rate limiting algorithms: a state is an opaque string owned by
 * whichever limiter wrote it. Implementations only guarantee that
 * {@link #update(String, Function)} is atomic per key, so two concurrent requests can never read
 * the same state and overwrite each other's change.
 *
 * <p>Implementations: {@link InMemoryStore} (state is lost on restart) and
 * {@link PostgresStore} (state is persistent).
 */
public interface Store {

    <T> T update(String key, Function<String, Outcome<T>> updater);
}