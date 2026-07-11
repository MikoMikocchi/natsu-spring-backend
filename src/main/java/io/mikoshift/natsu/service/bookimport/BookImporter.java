package io.mikoshift.natsu.service.bookimport;

import io.mikoshift.natsu.entity.Document.SourceFormat;

public interface BookImporter {

    SourceFormat supportedFormat();

    /**
     * @throws ImportException if the source bytes are not a well-formed document of this format.
     */
    ImportedBook importFrom(byte[] sourceBytes);
}
