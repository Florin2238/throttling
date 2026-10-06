package com.example.throttling.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two public endpoints of the service.
 *
 * <p>Both return a fixed success body. Authentication and rate limiting happen before a request
 * reaches this class, in {@link com.example.throttling.auth.AuthInterceptor} and
 * {@link com.example.throttling.ratelimit.RateLimitInterceptor}; a request rejected there never
 * gets here.
 */
@RestController
public class ApiController {

    @GetMapping("/foo")
    public Map<String, Boolean> foo() {
        return Map.of("success", true);
    }

    @GetMapping("/bar")
    public Map<String, Boolean> bar() {
        return Map.of("success", true);
    }
}