package com.example.throttling.ratelimit;

import java.time.Duration;

/**
 * A rate limiting algorithm.
 *
 * <p>An implementation decides, for one client, whether a request may proceed. It keeps its
 * per-client state in a {@link com.example.throttling.store.Store}, so it works with any store,
 * and it knows nothing about HTTP. See {@link TokenBucketLimiter} and
 * {@link SlidingWindowLimiter}.
 */
public interface RateLimiter {

    RateLimitResult check(String clientId, int limit, Duration window);
}