package io.mikoshift.natsu.backend.service.bookimport;

import java.util.List;

public record ImportedBook(String title, List<ImportedSection> sections) {}
