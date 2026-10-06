package com.example.throttling.store;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * A {@link Store} that keeps its state in a {@link ConcurrentHashMap} inside the application
 * process.
 *
 * <p>It is fast and has no dependencies, but the state is lost when the application stops and is
 * not shared between instances. Atomicity comes from {@link ConcurrentHashMap#compute}, which
 * runs the update function while holding a lock on the key.
 *
 * <p>The map cannot grow without bound: keys combine an endpoint and a client id, and unknown
 * clients are rejected before they reach a limiter.
 */
public class InMemoryStore implements Store {

    private final ConcurrentHashMap<String, String> states = new ConcurrentHashMap<>();

    @Override
    public <T> T update(String key, Function<String, Outcome<T>> updater) {
        AtomicReference<T> result = new AtomicReference<>();

        states.compute(key, (k, current) -> {
            Outcome<T> outcome = updater.apply(current);
            result.set(outcome.result());
            return outcome.newState();
        });

        return result.get();
    }
}