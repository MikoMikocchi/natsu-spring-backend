package io.mikoshift.natsu.backend.service.bookimport;

import io.mikoshift.natsu.backend.entity.Document.SourceFormat;
import java.util.List;

/** Written as manifest.json at the root of every package zip. */
public record PackageManifest(
    int version, String title, SourceFormat sourceFormat, List<ManifestSection> sections) {

  public record ManifestSection(String id, String title, String path) {}
}
