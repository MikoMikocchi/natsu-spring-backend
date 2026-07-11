package io.mikoshift.natsu.service.dictionary;

/**
 * Raised by {@link TermBankImportService} for a malformed archive; not translated to an API error
 * shape, since this is a seed-time-only operation with no HTTP request behind it.
 */
public class TermBankImportException extends RuntimeException {

    public TermBankImportException(String message) {
        super(message);
    }

    public TermBankImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
