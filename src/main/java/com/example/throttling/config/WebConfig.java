package com.example.throttling.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.throttling.auth.AuthInterceptor;
import com.example.throttling.ratelimit.RateLimitInterceptor;
import com.example.throttling.ratelimit.RateLimiter;

/**
 * Registers the interceptors that run before the controller.
 *
 * <p>Interceptors run in registration order, so authentication always comes first and each
 * endpoint is then guarded by its own rate limiter.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final ClientProperties clients;
    private final RateLimiter fooLimiter;
    private final RateLimiter barLimiter;

    public WebConfig(AuthInterceptor authInterceptor,
                     ClientProperties clients,
                     @Qualifier("fooLimiter") RateLimiter fooLimiter,
                     @Qualifier("barLimiter") RateLimiter barLimiter) {
        this.authInterceptor = authInterceptor;
        this.clients = clients;
        this.fooLimiter = fooLimiter;
        this.barLimiter = barLimiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/foo", "/bar");
        registry.addInterceptor(new RateLimitInterceptor(fooLimiter, clients)).addPathPatterns("/foo");
        registry.addInterceptor(new RateLimitInterceptor(barLimiter, clients)).addPathPatterns("/bar");
    }
}