package io.mikoshift.natsu.backend.dto.response;

import java.util.List;

public record DocumentIndexResponse(List<DocumentResponse> documents, long serverTimeMs) {}
