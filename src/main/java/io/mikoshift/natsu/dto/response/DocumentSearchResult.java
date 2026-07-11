package io.mikoshift.natsu.dto.response;

import java.util.List;
import java.util.UUID;

public record DocumentSearchResult(UUID id, String title, List<DocumentSearchMatch> matches) {}
