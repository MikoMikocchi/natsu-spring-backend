package io.mikoshift.natsu.backend.security;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.mikoshift.natsu.backend.config.NatsuProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * A minimal per-IP limiter on the two endpoints most worth protecting from brute-forcing/spam:
 * login and registration. Not general per-route/per-IP throttling across the whole API -- that's
 * explicitly out of scope for v1.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of("/v1/auth/login", "/v1/auth/register");

    private final ObjectMapper objectMapper;
    private final NatsuProperties properties;

    private final ConcurrentMap<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .<String, Bucket>build()
            .asMap();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod()) && LIMITED_PATHS.contains(request.getRequestURI())) {
            Bucket bucket = buckets.computeIfAbsent(bucketKey(request), key -> newBucket());
            if (!bucket.tryConsume(1)) {
                response.setStatus(429);
                response.setContentType("application/json");
                objectMapper.writeValue(
                        response.getWriter(),
                        Map.of("errors", Map.of("base", List.of("Too many requests, try again later"))));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private Bucket newBucket() {
        int capacity = properties.rateLimitCapacity();
        Duration window = Duration.ofSeconds(properties.rateLimitWindowSeconds());
        return Bucket.builder().addLimit(Bandwidth.classic(capacity, Refill.greedy(capacity, window))).build();
    }

    private static String bucketKey(HttpServletRequest request) {
        return request.getRequestURI() + ':' + request.getRemoteAddr();
    }
}
