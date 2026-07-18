package io.mikoshift.natsu.exception;

import org.springframework.http.HttpStatus;

public class QuotaExceededException extends ApiException {

    public QuotaExceededException(String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "base", message);
    }
}
