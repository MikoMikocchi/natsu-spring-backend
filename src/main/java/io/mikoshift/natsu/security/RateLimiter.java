package io.mikoshift.natsu.security;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.mikoshift.natsu.config.NatsuProperties;
import java.time.Duration;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Shared Bucket4j/Caffeine throttle used by both {@link RateLimitFilter} (per-IP checks that don't
 * need the request body) and controllers that need to throttle on a value from the parsed request
 * body (e.g. email, refresh token). Each caller picks a {@link NatsuProperties.RateLimit.Bucket}
 * config and a key; buckets are cached per (category, key) pair.
 */
@Component
public class RateLimiter {

    private final ConcurrentMap<String, Bucket> buckets;

    public RateLimiter(NatsuProperties properties) {
        NatsuProperties.RateLimit.BucketCache cache = properties.rateLimit().bucketCache();
        buckets = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(cache.expireAfterAccessMinutes()))
                .maximumSize(cache.maximumSize())
                .<String, Bucket>build()
                .asMap();
    }

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
                .addLimit(limit -> limit.capacity(config.capacity()).refillGreedy(config.capacity(), window))
                .build();
    }
}
