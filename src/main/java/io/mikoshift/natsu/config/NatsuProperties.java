package io.mikoshift.natsu.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "natsu")
public record NatsuProperties(
        String storageRoot,
        long maxPackageBytes,
        long maxStorageBytesPerUser,
        List<String> corsAllowedOrigins,
        RateLimit rateLimit,
        // Deep-link/web URL the mobile app (or a future web client) resolves the reset token
        // against. "{token}" is substituted with the raw (unhashed) reset token. No real web
        // frontend exists yet, so this defaults to a clearly-a-placeholder localhost URL --
        // override via NATSU_PASSWORD_RESET_URL_TEMPLATE once a real one exists.
        String passwordResetUrlTemplate,
        // "From" address for outgoing mail. Override via NATSU_MAIL_FROM in any real deployment.
        String mailFrom,
        BookImportRecovery bookImportRecovery) {

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
            Bucket refreshToken) {

        public record Bucket(int capacity, int windowSeconds) {}
    }

    /**
     * Governs the job that finds {@code Document}s stuck in {@code PENDING} (e.g. the app crashed or
     * restarted mid-import, stranding the in-memory {@code @Async} task) and flips them to {@code
     * FAILED} so the user sees an actual error instead of a permanent "importing..." spinner. See
     * {@code io.mikoshift.natsu.service.bookimport.StaleImportRecoveryService}.
     */
    public record BookImportRecovery(
            boolean enabled, int staleAfterMinutes, long checkIntervalMinutes, int maxAttempts) {}
}
