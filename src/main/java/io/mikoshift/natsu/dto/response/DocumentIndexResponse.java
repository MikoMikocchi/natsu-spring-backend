package io.mikoshift.natsu.dto.response;

import java.util.List;

public record DocumentIndexResponse(List<DocumentResponse> documents, long serverTimeMs) {}
