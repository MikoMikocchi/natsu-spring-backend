package io.mikoshift.natsu.backend.dto.request;

import io.mikoshift.natsu.backend.entity.Document.SourceFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record DocumentSyncItemRequest(
        @NotNull UUID id,
        String title,
        @NotNull SourceFormat sourceFormat,
        @NotNull @PositiveOrZero Long importedAt,
        @NotNull @PositiveOrZero Integer charCount,
        @NotNull @PositiveOrZero Integer lastReadCharOffset,
        String lastReadSectionId,
        @NotNull @PositiveOrZero Integer lastReadBlockIndex,
        @NotNull @PositiveOrZero Integer lastReadBlockCharOffset,
        @NotNull @PositiveOrZero Long updatedAtMs,
        @NotNull Boolean deleted) {}
