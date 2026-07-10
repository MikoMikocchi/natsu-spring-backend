package io.mikoshift.natsu.backend.dto.response;

import io.mikoshift.natsu.backend.entity.Dictionary;
import java.util.UUID;

public record DictionaryResponse(
    UUID id, String catalogId, String title, String revision, int termCount, boolean enabled) {

  public static DictionaryResponse from(Dictionary dictionary, boolean enabled) {
    return new DictionaryResponse(
        dictionary.getId(),
        dictionary.getCatalogId(),
        dictionary.getTitle(),
        dictionary.getRevision(),
        dictionary.getTermCount(),
        enabled);
  }
}
