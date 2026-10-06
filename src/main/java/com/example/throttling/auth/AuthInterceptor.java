package com.example.throttling.auth;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.throttling.config.ClientProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates each request from its {@code Authorization: Bearer <client-id>} header.
 *
 * <p>Requests without a well-formed header, or with a client id that is not configured in
 * {@link ClientProperties}, are rejected with {@code 401}. For known clients the id is stored as
 * a request attribute ({@link #CLIENT_ID_ATTRIBUTE}) so that later interceptors can read it.
 *
 * <p>This interceptor must be registered before any rate limiting interceptor.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String CLIENT_ID_ATTRIBUTE = "clientId";
    private static final String PREFIX = "Bearer ";

    private final ClientProperties clients;

    public AuthInterceptor(ClientProperties clients) {
        this.clients = clients;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String header = request.getHeader("Authorization");

        if (header == null || !header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return reject(response);
        }

        String clientId = header.substring(PREFIX.length()).trim();
        if (!clients.isKnown(clientId)) {
            return reject(response);
        }

        request.setAttribute(CLIENT_ID_ATTRIBUTE, clientId);
        return true;
    }

    private boolean reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"unauthorized\"}");
        return false;
    }
}