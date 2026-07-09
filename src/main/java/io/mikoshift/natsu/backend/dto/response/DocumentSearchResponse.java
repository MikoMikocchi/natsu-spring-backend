package io.mikoshift.natsu.backend.dto.response;

import java.util.List;

public record DocumentSearchResponse(List<DocumentSearchResult> results, long serverTimeMs) {}
