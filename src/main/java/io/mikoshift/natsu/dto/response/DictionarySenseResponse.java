package io.mikoshift.natsu.dto.response;

import java.util.List;

public record DictionarySenseResponse(List<String> definitions, List<String> partsOfSpeech, String dictionaryTitle) {}
