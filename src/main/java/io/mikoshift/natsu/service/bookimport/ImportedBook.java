package io.mikoshift.natsu.service.bookimport;

import java.util.List;

public record ImportedBook(String title, List<ImportedSection> sections) {}
