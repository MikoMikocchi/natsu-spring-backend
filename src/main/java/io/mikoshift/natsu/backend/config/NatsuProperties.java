package io.mikoshift.natsu.backend.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "natsu")
public record NatsuProperties(
        String storageRoot,
        long maxPackageBytes,
        long maxStorageBytesPerUser,
        List<String> corsAllowedOrigins,
        RateLimit rateLimit) {

    /**
     * Per-endpoint, per-dimension throttle settings. Each bucket is independent -- e.g. login has
     * its own per-IP bucket AND its own per-email bucket, mirroring the two independent Rack::Attack
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
}
