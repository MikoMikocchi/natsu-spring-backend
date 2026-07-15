package io.mikoshift.natsu.security;

import io.mikoshift.natsu.config.NatsuProperties;
import io.mikoshift.natsu.security.oauth2.NatsuOAuth2ParameterNames;
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
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Throttles auth endpoints: per-IP on all protected routes, plus per-email (login) and
 * per-refresh-token (refresh) on {@code /oauth2/token} where those keys live in the form body.
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

        if ("/oauth2/token".equals(request.getRequestURI()) && "POST".equalsIgnoreCase(request.getMethod())) {
            if (rejectOAuth2TokenRequest(request, response)) {
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean rejectOAuth2TokenRequest(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        if (NatsuOAuth2ParameterNames.isPasswordGrant(grantType)) {
            String username = request.getParameter(NatsuOAuth2ParameterNames.USERNAME);
            if (StringUtils.hasText(username)
                    && !rateLimiter.tryConsume(
                            "login-email",
                            username.trim().toLowerCase(),
                            properties.rateLimit().loginEmail())) {
                writeTooManyRequests(
                        response, properties.rateLimit().loginEmail().windowSeconds());
                return true;
            }
        } else if (NatsuOAuth2ParameterNames.isRefreshGrant(grantType)) {
            String refreshToken = request.getParameter(OAuth2ParameterNames.REFRESH_TOKEN);
            if (StringUtils.hasText(refreshToken)
                    && !rateLimiter.tryConsume(
                            "refresh-token",
                            refreshToken,
                            properties.rateLimit().refreshToken())) {
                writeTooManyRequests(
                        response, properties.rateLimit().refreshToken().windowSeconds());
                return true;
            }
        }
        return false;
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
