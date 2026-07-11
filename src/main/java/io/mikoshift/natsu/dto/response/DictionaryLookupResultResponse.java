package io.mikoshift.natsu.dto.response;

import io.mikoshift.natsu.service.dictionary.MatchKind;
import java.util.List;

public record DictionaryLookupResultResponse(
        String word,
        String reading,
        MatchKind matchKind,
        String ruleName,
        String ruleDescription,
        List<DictionarySenseResponse> senses) {}
