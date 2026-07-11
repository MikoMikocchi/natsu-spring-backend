package io.mikoshift.natsu.util;

import java.io.IOException;

/** Thrown when a zip archive expands beyond configured decompressed-size limits (zip bomb guard). */
public class ZipExpansionLimitExceededException extends IOException {

    public ZipExpansionLimitExceededException(String message) {
        super(message);
    }
}
