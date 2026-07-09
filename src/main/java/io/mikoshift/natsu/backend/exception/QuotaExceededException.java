package io.mikoshift.natsu.backend.exception;

import org.springframework.http.HttpStatus;

public class QuotaExceededException extends ApiException {

    public QuotaExceededException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "base", message);
    }
}
