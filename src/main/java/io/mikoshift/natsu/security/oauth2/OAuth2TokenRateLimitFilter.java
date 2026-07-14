package io.mikoshift.natsu.security.oauth2;

import io.mikoshift.natsu.config.NatsuProperties;
import io.mikoshift.natsu.security.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Per-username and per-refresh-token throttling on {@code /oauth2/token}, where the grant type and
 * credentials live in the form body rather than JSON.
 */
@Component
public class OAuth2TokenRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final NatsuProperties properties;
    private final ObjectMapper objectMapper;

    public OAuth2TokenRateLimitFilter(
            RateLimiter rateLimiter, NatsuProperties properties, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!"/oauth2/token".equals(request.getRequestURI()) || !"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        if (NatsuOAuth2ParameterNames.isPasswordGrant(grantType)) {
            String username = request.getParameter(NatsuOAuth2ParameterNames.USERNAME);
            if (StringUtils.hasText(username)
                    && !rateLimiter.tryConsume(
                            "login-email",
                            username.trim().toLowerCase(),
                            properties.rateLimit().loginEmail())) {
                writeTooManyRequests(response, properties.rateLimit().loginEmail().windowSeconds());
                return;
            }
        } else if (NatsuOAuth2ParameterNames.isRefreshGrant(grantType)) {
            String refreshToken = request.getParameter(OAuth2ParameterNames.REFRESH_TOKEN);
            if (StringUtils.hasText(refreshToken)
                    && !rateLimiter.tryConsume(
                            "refresh-token", refreshToken, properties.rateLimit().refreshToken())) {
                writeTooManyRequests(response, properties.rateLimit().refreshToken().windowSeconds());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void writeTooManyRequests(HttpServletResponse response, int retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        objectMapper.writeValue(
                response.getWriter(), Map.of("errors", Map.of("base", List.of("Too many requests, try again later"))));
    }
}
