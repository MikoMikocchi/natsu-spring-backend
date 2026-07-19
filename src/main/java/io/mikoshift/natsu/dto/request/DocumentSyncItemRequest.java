package io.mikoshift.natsu.dto.request;

import io.mikoshift.natsu.entity.Document.SourceFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record DocumentSyncItemRequest(
        @NotNull UUID id,
        @NotBlank @Size(max = 255) String idempotencyKey,
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
