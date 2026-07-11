package io.mikoshift.natsu.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "base", "Incorrect email or password");
    }
}
