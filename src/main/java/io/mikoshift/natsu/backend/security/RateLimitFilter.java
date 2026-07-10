package io.mikoshift.natsu.backend.security;

import io.mikoshift.natsu.backend.config.NatsuProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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

  private static final Map<String, String> LIMITED_PATHS =
      Map.of(
          "/v1/auth/login", "login",
          "/v1/auth/register", "register",
          "/v1/auth/password/forgot", "password-reset",
          "/v1/auth/password/reset", "password-reset",
          "/v1/auth/refresh", "refresh");

  private final ObjectMapper objectMapper;
  private final NatsuProperties properties;
  private final RateLimiter rateLimiter;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String category =
        "POST".equalsIgnoreCase(request.getMethod())
            ? LIMITED_PATHS.get(request.getRequestURI())
            : null;
    if (category != null) {
      NatsuProperties.RateLimit.Bucket config = bucketConfig(category);
      if (!rateLimiter.tryConsume(category + "-ip", request.getRemoteAddr(), config)) {
        writeTooManyRequests(response, config.windowSeconds());
        return;
      }
    }
    filterChain.doFilter(request, response);
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

  private void writeTooManyRequests(HttpServletResponse response, int retryAfterSeconds)
      throws IOException {
    response.setStatus(429);
    response.setContentType("application/json");
    response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
    objectMapper.writeValue(
        response.getWriter(),
        Map.of("errors", Map.of("base", List.of("Too many requests, try again later"))));
  }
}
