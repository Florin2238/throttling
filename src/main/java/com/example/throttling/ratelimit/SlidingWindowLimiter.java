package com.example.throttling.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.example.throttling.store.Outcome;
import com.example.throttling.store.Store;

/**
 * Sliding window log: enforces "at most {@code limit} requests in any {@code window}".
 *
 * <p>The limiter remembers the timestamps of the accepted requests. On each request it drops the
 * timestamps older than the window, counts the rest, and accepts the request only if fewer than
 * {@code limit} remain. Because the window moves with the clock there are no boundary effects,
 * unlike a fixed window counter. The price is memory: at most {@code limit} timestamps are kept
 * per client.
 *
 * <p>The state saved in the {@link Store} is a comma separated list of epoch milliseconds, for
 * example {@code "1000,2500,4000"}. The read-decide-write sequence runs inside
 * {@link Store#update}, so it is atomic.
 */
public class SlidingWindowLimiter implements RateLimiter {

    private final String name;
    private final Store store;
    private final Clock clock;

    public SlidingWindowLimiter(String name, Store store, Clock clock) {
        this.name = name;
        this.store = store;
        this.clock = clock;
    }

    @Override
    public RateLimitResult check(String clientId, int limit, Duration window) {
        String key = name + ":" + clientId;

        return store.update(key, current -> {
            long now = clock.millis();
            long windowMillis = window.toMillis();

            List<Long> timestamps = parseRecent(current, now - windowMillis);

            if (timestamps.size() < limit) {
                timestamps.add(now);
                return new Outcome<>(serialize(timestamps), RateLimitResult.allow());
            }

            long oldest = timestamps.isEmpty() ? now : timestamps.get(0);
            long retryAfter = oldest + windowMillis - now;
            return new Outcome<>(serialize(timestamps), RateLimitResult.deny(retryAfter));
        });
    }

    private List<Long> parseRecent(String state, long windowStart) {
        List<Long> result = new ArrayList<>();
        if (state == null || state.isEmpty()) {
            return result;
        }
        for (String part : state.split(",")) {
            long timestamp = Long.parseLong(part);
            if (timestamp > windowStart) {
                result.add(timestamp);
            }
        }
        return result;
    }

    private String serialize(List<Long> timestamps) {
        return timestamps.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}