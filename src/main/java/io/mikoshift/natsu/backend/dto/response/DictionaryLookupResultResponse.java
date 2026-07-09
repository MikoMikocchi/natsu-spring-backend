package io.mikoshift.natsu.backend.dto.response;

import io.mikoshift.natsu.backend.service.dictionary.MatchKind;
import java.util.List;

public record DictionaryLookupResultResponse(
        String word,
        String reading,
        MatchKind matchKind,
        String ruleName,
        String ruleDescription,
        List<DictionarySenseResponse> senses) {}
