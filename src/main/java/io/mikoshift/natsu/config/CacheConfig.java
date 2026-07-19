package io.mikoshift.natsu.config;

import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Shared Redis-backed dictionary caches. Invalidation is by cache key, not eviction: {@code
 * User.dictCacheVersion} is part of both keys and gets bumped on toggle, so old entries age out by TTL
 * instead of needing coordinated {@code @CacheEvict} calls.
 */
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig {

    public static final String DICT_LOOKUP_CACHE = "dictLookup";
    public static final String DICT_ENABLED_IDS_CACHE = "dictEnabledDictIds";

    private final NatsuProperties properties;

    @Bean
    CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisSerializer<Object> valueSerializer = RedisSerializer.json();
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer))
                .prefixCacheNameWith("natsu:");

        Map<String, RedisCacheConfiguration> perCache = Map.of(
                DICT_LOOKUP_CACHE,
                defaults.entryTtl(
                        Duration.ofMinutes(properties.dictionaryCache().lookup().expireAfterWriteMinutes())),
                DICT_ENABLED_IDS_CACHE,
                defaults.entryTtl(Duration.ofMinutes(
                        properties.dictionaryCache().enabledIds().expireAfterWriteMinutes())));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(perCache)
                .build();
    }
}
