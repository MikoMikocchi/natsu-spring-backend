package io.mikoshift.natsu.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DocumentSyncRequest(
        @NotEmpty @Size(max = 100) @Valid List<DocumentSyncItemRequest> documents) {}
