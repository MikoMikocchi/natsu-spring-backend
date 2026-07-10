package io.mikoshift.natsu.backend.service.bookimport;

/**
 * A transient failure (e.g. a storage I/O hiccup) worth retrying, as opposed to {@link
 * ImportException}.
 */
public class TransientImportException extends RuntimeException {

  public TransientImportException(String message, Throwable cause) {
    super(message, cause);
  }
}
