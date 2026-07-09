package io.mikoshift.natsu.backend.service.bookimport;

/** One chapter/section worth of HTML content, in reading order. */
public record ImportedSection(String id, String title, String html) {}
