package io.mikoshift.natsu.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the client IP for throttling and similar per-client checks.
 *
 * <p>When the app sits behind a reverse proxy or load balancer, {@link
 * HttpServletRequest#getRemoteAddr()} returns the proxy address unless Spring's forwarded-header
 * handling is enabled ({@code server.forward-headers-strategy=framework} in {@code
 * application.yml}). With that setting, {@code ForwardedHeaderFilter} wraps each request so
 * {@code getRemoteAddr()} reflects the original client from {@code X-Forwarded-For} / {@code
 * Forwarded} headers set by the trusted proxy.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {}

    public static String resolve(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
