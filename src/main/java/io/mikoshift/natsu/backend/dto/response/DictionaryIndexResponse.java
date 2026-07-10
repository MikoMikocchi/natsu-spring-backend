package io.mikoshift.natsu.backend.dto.response;

import java.util.List;

public record DictionaryIndexResponse(
    List<DictionaryResponse> dictionaries, PaginationResponse pagination, long serverTimeMs) {}
