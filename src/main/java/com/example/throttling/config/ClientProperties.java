package com.example.throttling.config;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The configured clients and their rate limits, bound from the {@code throttling.clients}
 * section of {@code application.yaml}.
 *
 * <p>Each client has its own limit, so adding or changing a client needs no code change.
 *
 * @param clients the limit of every known client, keyed by client id
 */
@ConfigurationProperties(prefix = "throttling")
public record ClientProperties(Map<String, ClientLimit> clients) {

    public record ClientLimit(int limit, int windowSeconds) {

        public Duration window() {
            return Duration.ofSeconds(windowSeconds);
        }
    }

    public boolean isKnown(String clientId) {
        return clients != null && clients.containsKey(clientId);
    }

    public ClientLimit limitFor(String clientId) {
        return clients.get(clientId);
    }
}