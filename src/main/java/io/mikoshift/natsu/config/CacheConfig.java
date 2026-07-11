package io.mikoshift.natsu.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Two in-process Caffeine caches (no Redis -- a single-instance deployment has no need for a shared
 * cache). Invalidation is by cache key, not eviction: {@code User.dictCacheVersion} is part of both
 * keys and gets bumped on toggle, so old entries just age out by TTL instead of needing coordinated
 * {@code @CacheEvict} calls.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String DICT_LOOKUP_CACHE = "dictLookup";
    public static final String DICT_ENABLED_IDS_CACHE = "dictEnabledDictIds";

    @Bean
    CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                new CaffeineCache(
                        DICT_LOOKUP_CACHE,
                        Caffeine.newBuilder()
                                .expireAfterWrite(Duration.ofMinutes(5))
                                .maximumSize(10_000)
                                .build()),
                new CaffeineCache(
                        DICT_ENABLED_IDS_CACHE,
                        Caffeine.newBuilder()
                                .expireAfterWrite(Duration.ofMinutes(30))
                                .maximumSize(10_000)
                                .build())));
        return manager;
    }
}
