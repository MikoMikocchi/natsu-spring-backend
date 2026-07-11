package io.mikoshift.natsu.service.bookimport;

import java.util.List;

/** One entry of a (possibly nested) table of contents. {@code sectionId} may be null if unresolved. */
public record TocNode(String title, String sectionId, List<TocNode> children) {}
