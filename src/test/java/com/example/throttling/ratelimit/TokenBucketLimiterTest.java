package com.example.throttling.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.example.throttling.store.InMemoryStore;

/**
 * Unit tests for {@link TokenBucketLimiter}, using the in-memory store and a controllable clock.
 *
 * <p>The limit is 3 per 6 seconds, which means one new token every 2 seconds.
 */
class TokenBucketLimiterTest {

    private static final int LIMIT = 3;
    private static final Duration WINDOW = Duration.ofSeconds(6);

    private final TestClock clock = new TestClock();
    private final TokenBucketLimiter limiter =
            new TokenBucketLimiter("foo", new InMemoryStore(), clock);

    private RateLimitResult call(String clientId) {
        return limiter.check(clientId, LIMIT, WINDOW);
    }

    @Test
    void startsFullAllowsBurstThenRejects() {
        for (int i = 0; i < LIMIT; i++) {
            assertTrue(call("client-1").allowed());
        }
        RateLimitResult rejected = call("client-1");
        assertFalse(rejected.allowed());
        assertEquals(2_000, rejected.retryAfterMillis());
    }

    @Test
    void refillsOneTokenEveryTwoSeconds() {
        clock.set(0);
        for (int i = 0; i < LIMIT; i++) {
            call("client-1");
        }
        assertFalse(call("client-1").allowed());

        clock.set(2_000);
        assertTrue(call("client-1").allowed());
        assertFalse(call("client-1").allowed());

        clock.set(3_000);
        RateLimitResult rejected = call("client-1");
        assertFalse(rejected.allowed());
        assertEquals(1_000, rejected.retryAfterMillis());

        clock.set(4_000);
        assertTrue(call("client-1").allowed());
    }

    @Test
    void neverHoldsMoreTokensThanTheCapacity() {
        clock.set(0);
        for (int i = 0; i < LIMIT; i++) {
            call("client-1");
        }

        clock.set(100_000);                 // mult timp: găleata se umple, dar nu depășește capacitatea
        for (int i = 0; i < LIMIT; i++) {
            assertTrue(call("client-1").allowed());
        }
        assertFalse(call("client-1").allowed());
    }

    @Test
    void clientsHaveIndependentBuckets() {
        for (int i = 0; i < LIMIT; i++) {
            call("client-1");
        }
        assertFalse(call("client-1").allowed());
        assertTrue(call("client-2").allowed());
    }
}