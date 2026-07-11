package io.mikoshift.natsu.service.bookimport;

import java.util.List;

public record ImportedBook(
        String title,
        List<String> authors,
        String language,
        String coverAssetId,
        List<TocNode> toc,
        List<ImportedSection> sections,
        List<ImportedAsset> assets) {

    /** Convenience for importers with no metadata/assets/nesting beyond a flat list of sections. */
    public static ImportedBook of(String title, List<ImportedSection> sections) {
        List<TocNode> toc = sections.stream()
                .map(section -> new TocNode(section.title(), section.id(), List.of()))
                .toList();
        return new ImportedBook(title, List.of(), null, null, toc, sections, List.of());
    }
}
