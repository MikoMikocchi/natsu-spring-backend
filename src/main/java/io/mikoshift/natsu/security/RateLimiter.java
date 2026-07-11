package io.mikoshift.natsu.security;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.mikoshift.natsu.config.NatsuProperties;
import java.time.Duration;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Shared Bucket4j/Caffeine throttle used by both {@link RateLimitFilter} (per-IP checks that don't
 * need the request body) and controllers that need to throttle on a value from the parsed request
 * body (e.g. email, refresh token). Each caller picks a {@link NatsuProperties.RateLimit.Bucket}
 * config and a key; buckets are cached per (category, key) pair.
 */
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private final ConcurrentMap<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(10_000)
            .<String, Bucket>build()
            .asMap();

    /**
     * Returns true if the call is allowed, false if the bucket for this category/key is exhausted.
     */
    public boolean tryConsume(String category, String key, NatsuProperties.RateLimit.Bucket config) {
        Bucket bucket = buckets.computeIfAbsent(category + ':' + key, ignored -> newBucket(config));
        return bucket.tryConsume(1);
    }

    private static Bucket newBucket(NatsuProperties.RateLimit.Bucket config) {
        Duration window = Duration.ofSeconds(config.windowSeconds());
        return Bucket.builder()
                .addLimit(Bandwidth.classic(config.capacity(), Refill.greedy(config.capacity(), window)))
                .build();
    }
}
