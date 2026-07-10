package io.mikoshift.natsu.backend.exception;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base for exceptions rendered by GlobalExceptionHandler as {@code {"errors": {"field":
 * ["message"]}}}.
 */
@Getter
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final Map<String, List<String>> errors;

  protected ApiException(HttpStatus status, Map<String, List<String>> errors) {
    super(errors.toString());
    this.status = status;
    this.errors = errors;
  }

  protected ApiException(HttpStatus status, String field, String message) {
    this(status, Map.of(field, List.of(message)));
  }
}
