package io.mikoshift.natsu.service.bookimport;

import io.mikoshift.natsu.entity.Document.SourceFormat;
import java.util.List;

/** Written as manifest.json at the root of every package zip. */
public record PackageManifest(
        int schemaVersion,
        String title,
        List<String> authors,
        String language,
        String coverAssetId,
        SourceFormat sourceFormat,
        List<ManifestTocNode> toc,
        List<ManifestSection> sections) {

    public record ManifestTocNode(String title, String sectionId, List<ManifestTocNode> children) {}

    public record ManifestSection(String id, String title, String path, int wordCount, String checksum) {}
}
