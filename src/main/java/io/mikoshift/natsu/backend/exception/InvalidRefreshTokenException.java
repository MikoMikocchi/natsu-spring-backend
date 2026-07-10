package io.mikoshift.natsu.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends ApiException {

  public InvalidRefreshTokenException() {
    super(HttpStatus.UNAUTHORIZED, "base", "Invalid or expired refresh token");
  }
}
