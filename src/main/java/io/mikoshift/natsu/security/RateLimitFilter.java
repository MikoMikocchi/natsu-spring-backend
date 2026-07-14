package io.mikoshift.natsu.security;

import io.mikoshift.natsu.config.NatsuProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Per-IP throttling on the auth endpoints worth protecting from brute-forcing/spam: login,
 * registration, password reset, and token refresh. Not general per-route/per-IP throttling across
 * the whole API -- that's explicitly out of scope for v1.
 *
 * <p>Per-email (login) and per-refresh-token (refresh) throttles need a value from the parsed
 * request body, which isn't available at this raw servlet-filter layer without wrapping the
 * request; those are enforced downstream in the controllers instead, via the same {@link
 * RateLimiter} bean, so all throttling shares one bucket store and one 429 response shape.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Map<String, String> LIMITED_PATHS = Map.of(
            "/v1/auth/register", "register",
            "/v1/auth/password/forgot", "password-reset",
            "/v1/auth/password/reset", "password-reset");

    private final ObjectMapper objectMapper;
    private final NatsuProperties properties;
    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String category = resolveCategory(request);
        if (category != null) {
            NatsuProperties.RateLimit.Bucket config = bucketConfig(category);
            if (!rateLimiter.tryConsume(category + "-ip", clientIpResolver.resolve(request), config)) {
                writeTooManyRequests(response, config.windowSeconds());
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static String resolveCategory(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        if ("/oauth2/token".equals(request.getRequestURI())) {
            String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
            if (OAuth2ParameterNames.REFRESH_TOKEN.equals(grantType)) {
                return "refresh";
            }
            return "login";
        }
        return LIMITED_PATHS.get(request.getRequestURI());
    }

    private NatsuProperties.RateLimit.Bucket bucketConfig(String category) {
        NatsuProperties.RateLimit rateLimit = properties.rateLimit();
        return switch (category) {
            case "login" -> rateLimit.login();
            case "register" -> rateLimit.register();
            case "password-reset" -> rateLimit.passwordReset();
            case "refresh" -> rateLimit.refresh();
            default -> throw new IllegalStateException("Unknown rate limit category: " + category);
        };
    }

    private void writeTooManyRequests(HttpServletResponse response, int retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        objectMapper.writeValue(
                response.getWriter(), Map.of("errors", Map.of("base", List.of("Too many requests, try again later"))));
    }
}
