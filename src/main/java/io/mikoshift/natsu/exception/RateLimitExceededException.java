package io.mikoshift.natsu.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Thrown by controllers/services when a request-body-keyed throttle (e.g. per-email,
 * per-refresh-token) rejects a call. Per-IP throttling that doesn't need the body is handled
 * directly in {@link io.mikoshift.natsu.security.RateLimitFilter} instead.
 */
@Getter
public class RateLimitExceededException extends ApiException {

    private final int retryAfterSeconds;

    public RateLimitExceededException(int retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "base", "Too many requests, try again later");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
