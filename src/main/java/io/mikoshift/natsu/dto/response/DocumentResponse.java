package io.mikoshift.natsu.dto.response;

import io.mikoshift.natsu.entity.Document;
import io.mikoshift.natsu.entity.Document.SourceFormat;
import io.mikoshift.natsu.entity.Document.Status;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String title,
        SourceFormat sourceFormat,
        Status status,
        String importError,
        long importedAt,
        int charCount,
        int lastReadCharOffset,
        String lastReadSectionId,
        int lastReadBlockIndex,
        int lastReadBlockCharOffset,
        long updatedAtMs,
        long packageSizeBytes,
        long packageUpdatedAtMs,
        String packageSha256,
        boolean deleted) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getSourceFormat(),
                document.getStatus(),
                document.getImportError(),
                document.getImportedAt(),
                document.getCharCount(),
                document.getLastReadCharOffset(),
                document.getLastReadSectionId(),
                document.getLastReadBlockIndex(),
                document.getLastReadBlockCharOffset(),
                document.getUpdatedAtMs(),
                document.getPackageSizeBytes(),
                document.getPackageUpdatedAtMs(),
                document.getPackageSha256(),
                document.getDeletedAt() != null);
    }
}
