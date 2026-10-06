package com.example.throttling.ratelimit;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.throttling.auth.AuthInterceptor;
import com.example.throttling.config.ClientProperties;
import com.example.throttling.config.ClientProperties.ClientLimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Applies a {@link RateLimiter} to the requests of one endpoint.
 *
 * <p>It runs after {@link AuthInterceptor}, reads the authenticated client id, looks up that
 * client's limit and asks the limiter whether the request may proceed. Rejected requests get
 * {@code 429} with a {@code Retry-After} header. The interceptor works only with the
 * {@link RateLimiter} interface, so one class serves every endpoint whatever algorithm it uses.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter limiter;
    private final ClientProperties clients;

    public RateLimitInterceptor(RateLimiter limiter, ClientProperties clients) {
        this.limiter = limiter;
        this.clients = clients;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String clientId = (String) request.getAttribute(AuthInterceptor.CLIENT_ID_ATTRIBUTE);
        ClientLimit clientLimit = clients.limitFor(clientId);

        RateLimitResult result = limiter.check(clientId, clientLimit.limit(), clientLimit.window());
        if (result.allowed()) {
            return true;
        }

        long retryAfterSeconds = (result.retryAfterMillis() + 999) / 1000;
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.getWriter().write("{\"error\":\"rate limit exceeded\"}");
        return false;
    }
}