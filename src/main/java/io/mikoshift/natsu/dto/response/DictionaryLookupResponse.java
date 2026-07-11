package io.mikoshift.natsu.dto.response;

import java.util.List;

public record DictionaryLookupResponse(List<DictionaryLookupResultResponse> data, long serverTimeMs) {}
