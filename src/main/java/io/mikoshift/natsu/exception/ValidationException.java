package io.mikoshift.natsu.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends ApiException {

    private ValidationException(String field, String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, field, message);
    }

    public static ValidationException of(String field, String message) {
        return new ValidationException(field, message);
    }
}
