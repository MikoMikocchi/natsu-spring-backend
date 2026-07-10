package io.mikoshift.natsu.backend.service.bookimport;

import io.mikoshift.natsu.backend.entity.Document.SourceFormat;
import org.springframework.stereotype.Component;

@Component
public class FormatDetector {

  public SourceFormat detect(String filename) {
    String lower = filename == null ? "" : filename.toLowerCase();
    if (lower.endsWith(".epub")) {
      return SourceFormat.EPUB;
    }
    if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
      return SourceFormat.MARKDOWN;
    }
    if (lower.endsWith(".txt")) {
      return SourceFormat.PLAIN_TEXT;
    }
    throw new ImportException("Unsupported file type: " + filename);
  }

  public String titleFromFilename(String filename) {
    if (filename == null || filename.isBlank()) {
      return "Untitled";
    }
    String base =
        filename.contains("/") ? filename.substring(filename.lastIndexOf('/') + 1) : filename;
    int dot = base.lastIndexOf('.');
    return dot > 0 ? base.substring(0, dot) : base;
  }
}
