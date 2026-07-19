package io.mikoshift.natsu.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Bucket4j distributed rate limiting via Redis. Uses a dedicated Lettuce client so bucket state is
 * shared across app replicas; idle bucket keys expire based on {@link NatsuProperties.RateLimit#bucketCache()}.
 */
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private final NatsuProperties properties;

    @Bean(destroyMethod = "shutdown")
    RedisClient rateLimitRedisClient(RedisConnectionFactory connectionFactory) {
        if (!(connectionFactory instanceof LettuceConnectionFactory lettuce)) {
            throw new IllegalStateException("Redis rate limiting requires LettuceConnectionFactory");
        }
        RedisStandaloneConfiguration standalone = lettuce.getStandaloneConfiguration();
        RedisURI.Builder builder = RedisURI.Builder.redis(standalone.getHostName(), standalone.getPort())
                .withDatabase(standalone.getDatabase());
        RedisPassword password = standalone.getPassword();
        if (password != null && password.isPresent()) {
            builder.withPassword(password.get());
        }
        return RedisClient.create(builder.build());
    }

    @Bean(destroyMethod = "close")
    StatefulRedisConnection<String, byte[]> rateLimitRedisConnection(RedisClient rateLimitRedisClient) {
        return rateLimitRedisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    @Bean
    ProxyManager<String> rateLimitProxyManager(StatefulRedisConnection<String, byte[]> rateLimitRedisConnection) {
        Duration expiration =
                Duration.ofMinutes(properties.rateLimit().bucketCache().expireAfterAccessMinutes());
        return LettuceBasedProxyManager.builderFor(rateLimitRedisConnection)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(expiration))
                .build();
    }
}
