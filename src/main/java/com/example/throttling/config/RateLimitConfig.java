package com.example.throttling.config;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.throttling.ratelimit.RateLimiter;
import com.example.throttling.ratelimit.SlidingWindowLimiter;
import com.example.throttling.ratelimit.TokenBucketLimiter;
import com.example.throttling.store.InMemoryStore;
import com.example.throttling.store.PostgresStore;
import com.example.throttling.store.Store;

/**
 * Creates the core beans and decides how they are wired together.
 *
 * <p>Two decisions are made here: which {@link Store} holds the counters (chosen with the
 * {@code throttling.store.type} property) and which algorithm protects which endpoint
 * ({@code /foo} uses a token bucket, {@code /bar} a sliding window). Both share one store;
 * their keys differ, so their counters never mix.
 */
@Configuration
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public Store store(@Value("${throttling.store.type:memory}") String type,
                       JdbcTemplate jdbcTemplate,
                       PlatformTransactionManager transactionManager) {
        return switch (type) {
            case "memory" -> {
                log.info("Rate limit store: IN MEMORY (counters are lost on restart)");
                yield new InMemoryStore();
            }
            case "postgres" -> {
                log.info("Rate limit store: POSTGRES (counters survive restarts)");
                yield new PostgresStore(jdbcTemplate, transactionManager);
            }
            default -> throw new IllegalArgumentException(
                    "throttling.store.type must be 'memory' or 'postgres', but was: " + type);
        };
    }

    @Bean
    public RateLimiter fooLimiter(Store store, Clock clock) {
        return new TokenBucketLimiter("foo", store, clock);
    }

    @Bean
    public RateLimiter barLimiter(Store store, Clock clock) {
        return new SlidingWindowLimiter("bar", store, clock);
    }
}