package io.mikoshift.natsu.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class TooManyRequestsException extends ApiException {

    private final int retryAfterSeconds;

    public TooManyRequestsException(int retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "base", "Too many requests, try again later");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
