package io.mikoshift.natsu.backend.service.bookimport;

import io.mikoshift.natsu.backend.entity.Document.SourceFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class BookImporterRegistry {

  private final Map<SourceFormat, BookImporter> importers;

  public BookImporterRegistry(List<BookImporter> importers) {
    this.importers =
        importers.stream()
            .collect(Collectors.toMap(BookImporter::supportedFormat, Function.identity()));
  }

  public BookImporter forFormat(SourceFormat format) {
    BookImporter importer = importers.get(format);
    if (importer == null) {
      throw new ImportException("No importer registered for format " + format);
    }
    return importer;
  }
}
