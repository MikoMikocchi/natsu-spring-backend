package io.mikoshift.natsu.service.bookimport;

import java.util.List;

/** One chapter/section worth of content, as an ordered list of blocks, in reading order. */
public record ImportedSection(String id, String title, List<Block> blocks) {}
