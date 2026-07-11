package io.mikoshift.natsu.repository;

import java.util.UUID;

/**
 * Lean projection for {@link DocumentRepository#searchByUserAndQuery} -- carries just what a
 * search result needs, rather than a full {@code Document} entity.
 */
public record DocumentSearchRow(UUID id, String title, String searchText) {}
