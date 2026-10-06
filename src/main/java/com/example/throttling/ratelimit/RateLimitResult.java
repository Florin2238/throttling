package com.example.throttling.ratelimit;

/**
 * The decision of a {@link RateLimiter} for one request.
 *
 * @param allowed          {@code true} if the request may proceed, {@code false} if it must be
 *                         rejected with {@code 429}
 * @param retryAfterMillis how long the client should wait before retrying, in milliseconds;
 *                         {@code 0} when the request was allowed
 */
public record RateLimitResult(boolean allowed, long retryAfterMillis) {

    public static RateLimitResult allow() {
        return new RateLimitResult(true, 0);
    }

    public static RateLimitResult deny(long retryAfterMillis) {
        return new RateLimitResult(false, retryAfterMillis);
    }
}