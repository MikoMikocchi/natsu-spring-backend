package io.mikoshift.natsu.security;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.mikoshift.natsu.config.NatsuProperties;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Shared Bucket4j/Redis throttle used by both {@link RateLimitFilter} (per-IP checks that don't
 * need the request body) and controllers that need to throttle on a value from the parsed request
 * body (e.g. email, refresh token). Each caller picks a {@link NatsuProperties.RateLimit.Bucket}
 * config and a key; bucket state is stored in Redis so limits apply across replicas.
 */
@Component
public class RateLimiter {

    private final ProxyManager<String> proxyManager;

    public RateLimiter(ProxyManager<String> rateLimitProxyManager) {
        this.proxyManager = rateLimitProxyManager;
    }

    /**
     * Returns true if the call is allowed, false if the bucket for this category/key is exhausted.
     */
    public boolean tryConsume(String category, String key, NatsuProperties.RateLimit.Bucket config) {
        String bucketKey = "rate-limit:" + category + ':' + key;
        Bucket bucket = proxyManager.builder().build(bucketKey, () -> bucketConfiguration(config));
        return bucket.tryConsume(1);
    }

    private static BucketConfiguration bucketConfiguration(NatsuProperties.RateLimit.Bucket config) {
        Duration window = Duration.ofSeconds(config.windowSeconds());
        return BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(config.capacity()).refillGreedy(config.capacity(), window))
                .build();
    }
}
