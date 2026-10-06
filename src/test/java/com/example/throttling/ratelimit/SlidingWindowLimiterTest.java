package com.example.throttling.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.example.throttling.store.InMemoryStore;

/**
 * Unit tests for {@link SlidingWindowLimiter}, using the in-memory store and a controllable clock.
 */
class SlidingWindowLimiterTest {

    private static final Duration WINDOW = Duration.ofSeconds(10);

    private final TestClock clock = new TestClock();
    private final SlidingWindowLimiter limiter =
            new SlidingWindowLimiter("bar", new InMemoryStore(), clock);

    @Test
    void allowsRequestsUpToTheLimitThenRejects() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.check("client-1", 3, WINDOW).allowed());
        }
        assertFalse(limiter.check("client-1", 3, WINDOW).allowed());
    }

    @Test
    void allowsAgainWhenOldRequestsLeaveTheWindow() {
        clock.set(0);
        assertTrue(limiter.check("client-1", 3, WINDOW).allowed());
        clock.set(2_000);
        assertTrue(limiter.check("client-1", 3, WINDOW).allowed());
        clock.set(4_000);
        assertTrue(limiter.check("client-1", 3, WINDOW).allowed());

        clock.set(5_000);
        RateLimitResult rejected = limiter.check("client-1", 3, WINDOW);
        assertFalse(rejected.allowed());
        assertEquals(5_000, rejected.retryAfterMillis());

        clock.set(10_500);
        assertTrue(limiter.check("client-1", 3, WINDOW).allowed());
    }

    @Test
    void clientsHaveIndependentCounters() {
        for (int i = 0; i < 3; i++) {
            limiter.check("client-1", 3, WINDOW);
        }
        assertFalse(limiter.check("client-1", 3, WINDOW).allowed());
        assertTrue(limiter.check("client-2", 3, WINDOW).allowed());
    }
}