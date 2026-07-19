package io.mikoshift.natsu.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "natsu")
public record NatsuProperties(
        String storageRoot,
        long maxPackageBytes,
        long maxStorageBytesPerUser,
        List<String> corsAllowedOrigins,
        /**
         * CIDR ranges of reverse proxies/load balancers in front of this app. {@code X-Forwarded-For}
         * is consulted for per-IP rate limiting only when the direct TCP peer matches one of these
         * ranges. Empty means the header is never trusted (appropriate when the app is exposed
         * directly, without a proxy).
         */
        List<String> trustedProxyCidrs,
        RateLimit rateLimit,
        // Deep-link/web URL the mobile app (or a future web client) resolves the reset token
        // against. "{token}" is substituted with the raw (unhashed) reset token. No real web
        // frontend exists yet, so this defaults to a clearly-a-placeholder localhost URL --
        // override via NATSU_PASSWORD_RESET_URL_TEMPLATE once a real one exists.
        String passwordResetUrlTemplate,
        // "From" address for outgoing mail. Override via NATSU_MAIL_FROM in any real deployment.
        String mailFrom,
        /** JWT access/refresh lifetimes and password-reset token TTL. */
        Auth auth,
        OAuth2 oauth2,
        BookImportRecovery bookImportRecovery,
        DictionaryCache dictionaryCache,
        BookImportExecutor bookImportExecutor,
        Idempotency idempotency) {

    /** Per-entry decompressed cap as a multiple of {@link #maxPackageBytes()}. */
    public static final int ZIP_DECOMPRESSED_RATIO_PER_ENTRY = 2;

    /** Total decompressed cap across all entries as a multiple of {@link #maxPackageBytes()}. */
    public static final int ZIP_DECOMPRESSED_RATIO_TOTAL = 4;

    /** Max decompressed bytes for a single zip entry ({@value #ZIP_DECOMPRESSED_RATIO_PER_ENTRY}× package limit). */
    public long maxZipDecompressedBytesPerEntry() {
        return maxPackageBytes * ZIP_DECOMPRESSED_RATIO_PER_ENTRY;
    }

    /** Max decompressed bytes for the whole archive ({@value #ZIP_DECOMPRESSED_RATIO_TOTAL}× package limit). */
    public long maxZipDecompressedBytesTotal() {
        return maxPackageBytes * ZIP_DECOMPRESSED_RATIO_TOTAL;
    }

    /**
     * Per-endpoint, per-dimension throttle settings. Each bucket is independent -- e.g. login has its
     * own per-IP bucket AND its own per-email bucket, mirroring the two independent Rack::Attack
     * throttles on the sibling Rails backend.
     */
    public record RateLimit(
            Bucket login,
            Bucket loginEmail,
            Bucket register,
            Bucket passwordReset,
            Bucket refresh,
            Bucket refreshToken,
            /** TTL for idle rate-limit bucket keys in Redis (Bucket4j expiration strategy). */
            BucketCache bucketCache) {

        public record Bucket(int capacity, int windowSeconds) {}

        public record BucketCache(int expireAfterAccessMinutes) {}
    }

    /** Redis-backed dictionary caches (invalidated via {@code User.dictCacheVersion} in cache keys). */
    public record DictionaryCache(CacheSpec lookup, CacheSpec enabledIds) {

        public record CacheSpec(int expireAfterWriteMinutes) {}
    }

    /** Thread pool for {@code @Async} book import tasks ({@code bookImportExecutor}). */
    public record BookImportExecutor(int corePoolSize, int maxPoolSize, int queueCapacity) {}

    public record Auth(Duration accessTokenTtl, Duration refreshTokenTtl, Duration resetTokenTtl) {}

    public record OAuth2(String issuer, String clientId, Jwt jwt) {

        public record Jwt(String privateKey, String publicKey) {}
    }

    /**
     * Governs the job that finds {@code Document}s stuck in {@code PENDING} (e.g. the app crashed or
     * restarted mid-import, stranding the in-memory {@code @Async} task) and flips them to {@code
     * FAILED} so the user sees an actual error instead of a permanent "importing..." spinner. See
     * {@code io.mikoshift.natsu.service.bookimport.StaleImportRecoveryService}.
     */
    public record BookImportRecovery(
            boolean enabled, int staleAfterMinutes, long checkIntervalMinutes, int maxAttempts) {}

    /** TTL for {@code Idempotency-Key} records on mutating POST endpoints (Stripe-style replay window). */
    public record Idempotency(Duration keyTtl) {}
}
