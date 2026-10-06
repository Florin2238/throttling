package com.example.throttling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the API throttling service.
 *
 * <p>The service exposes two endpoints, {@code /foo} and {@code /bar}, each protected by a
 * different rate limiting algorithm. {@link ConfigurationPropertiesScan} registers
 * {@link com.example.throttling.config.ClientProperties}, which holds the per-client limits.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ThrottlingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThrottlingApplication.class, args);
    }
}