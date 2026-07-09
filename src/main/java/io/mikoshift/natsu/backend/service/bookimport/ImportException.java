package io.mikoshift.natsu.backend.service.bookimport;

/** A permanent, non-retryable import failure (malformed/unsupported source file). */
public class ImportException extends RuntimeException {

    public ImportException(String message) {
        super(message);
    }

    public ImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
