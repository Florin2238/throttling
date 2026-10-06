package com.example.throttling.store;

/**
 * The result of a {@link Store#update(String, java.util.function.Function) store update}: the
 * state to persist, together with a value to hand back to the caller.
 *
 * <p>A limiter builds one of these inside its update function. For example, when it accepts a
 * request it returns the new counter state as {@code newState} and the decision as
 * {@code result}.
 *
 * @param newState the serialized state the store must save for the key; the store never
 *                 interprets it
 * @param result   the value that {@code Store.update} returns to its caller
 * @param <T>      the type of the returned value
 */
public record Outcome<T>(String newState, T result) {
}