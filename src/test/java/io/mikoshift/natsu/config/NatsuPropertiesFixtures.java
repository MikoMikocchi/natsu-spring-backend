package io.mikoshift.natsu.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/** Shared defaults for unit tests that construct {@link NatsuProperties} manually. */
public final class NatsuPropertiesFixtures {

    private static final NatsuProperties.RateLimit.Bucket BUCKET = new NatsuProperties.RateLimit.Bucket(5, 60);
    private static final NatsuProperties.RateLimit.BucketCache BUCKET_CACHE =
            new NatsuProperties.RateLimit.BucketCache(10, 10_000);
    public static final NatsuProperties.RateLimit RATE_LIMIT =
            new NatsuProperties.RateLimit(BUCKET, BUCKET, BUCKET, BUCKET, BUCKET, BUCKET, BUCKET_CACHE);
    public static final NatsuProperties.Auth AUTH =
            new NatsuProperties.Auth(Duration.ofHours(1), Duration.ofDays(365), Duration.ofHours(2));
    public static final NatsuProperties.OAuth2 OAUTH2 =
            new NatsuProperties.OAuth2("http://localhost:3000", "natsu-mobile", new NatsuProperties.OAuth2.Jwt("", ""));
    public static final NatsuProperties.BookImportRecovery BOOK_IMPORT_RECOVERY =
            new NatsuProperties.BookImportRecovery(true, 15, 5, 3);
    public static final NatsuProperties.DictionaryCache DICTIONARY_CACHE = new NatsuProperties.DictionaryCache(
            new NatsuProperties.DictionaryCache.CacheSpec(5, 10_000),
            new NatsuProperties.DictionaryCache.CacheSpec(30, 10_000));
    public static final NatsuProperties.BookImportExecutor BOOK_IMPORT_EXECUTOR =
            new NatsuProperties.BookImportExecutor(2, 4, 50);

    private NatsuPropertiesFixtures() {}

    public static NatsuProperties minimal(String storageRoot, long maxPackageBytes, long maxStorageBytesPerUser) {
        return minimal(storageRoot, maxPackageBytes, maxStorageBytesPerUser, new String[0]);
    }

    public static NatsuProperties minimal(
            String storageRoot, long maxPackageBytes, long maxStorageBytesPerUser, String... trustedProxyCidrs) {
        return new NatsuProperties(
                storageRoot,
                maxPackageBytes,
                maxStorageBytesPerUser,
                List.of("*"),
                List.copyOf(Arrays.asList(trustedProxyCidrs)),
                RATE_LIMIT,
                "http://localhost:3000/reset-password?token={token}",
                "noreply@example.com",
                AUTH,
                OAUTH2,
                BOOK_IMPORT_RECOVERY,
                DICTIONARY_CACHE,
                BOOK_IMPORT_EXECUTOR);
    }
}
