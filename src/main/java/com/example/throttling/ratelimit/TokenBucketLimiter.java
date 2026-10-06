package com.example.throttling.ratelimit;

import java.time.Clock;
import java.time.Duration;

import com.example.throttling.store.Outcome;
import com.example.throttling.store.Store;

/**
 * Token bucket: allows bursts up to {@code limit} requests, then a steady rate of
 * {@code limit} requests per {@code window}.
 *
 * <p>Each client has a bucket with a capacity of {@code limit} tokens that starts full. Every
 * request spends one token; when the bucket is empty the request is rejected. Tokens are added
 * continuously at {@code limit / window} per unit of time. The limiter does not run a timer:
 * on each request it computes the tokens earned since the previous one.
 *
 * <p>The state saved in the {@link Store} is {@code "tokens:lastUpdateMillis"}, for example
 * {@code "2.5:1728130000000"}. Tokens are fractional because less than a whole token can accumulate
 * between two requests. The read-decide-write sequence runs inside {@link Store#update}, so it is
 * atomic.
 */
public class TokenBucketLimiter implements RateLimiter {

    private final String name;
    private final Store store;
    private final Clock clock;

    public TokenBucketLimiter(String name, Store store, Clock clock) {
        this.name = name;
        this.store = store;
        this.clock = clock;
    }

    @Override
    public RateLimitResult check(String clientId, int limit, Duration window) {
        if (limit <= 0) {
            return RateLimitResult.deny(window.toMillis());
        }

        String key = name + ":" + clientId;

        return store.update(key, current -> {
            long now = clock.millis();
            long windowMillis = window.toMillis();

            double tokens = limit;
            if (current != null) {
                String[] parts = current.split(":");
                double savedTokens = Double.parseDouble(parts[0]);
                long lastUpdate = Long.parseLong(parts[1]);

                long elapsed = Math.max(0, now - lastUpdate);
                double refilled = (double) elapsed * limit / windowMillis;
                tokens = Math.min(limit, savedTokens + refilled);
            }

            if (tokens >= 1) {
                return new Outcome<>(serialize(tokens - 1, now), RateLimitResult.allow());
            }

            long retryAfter = (long) Math.ceil((1 - tokens) * windowMillis / limit);
            return new Outcome<>(serialize(tokens, now), RateLimitResult.deny(retryAfter));
        });
    }

    private String serialize(double tokens, long timestamp) {
        return tokens + ":" + timestamp;
    }
}