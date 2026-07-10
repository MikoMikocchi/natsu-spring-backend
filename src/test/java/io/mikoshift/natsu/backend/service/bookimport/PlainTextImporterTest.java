package io.mikoshift.natsu.backend.service.bookimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PlainTextImporterTest {

  private final PlainTextImporter importer = new PlainTextImporter();

  @Test
  void wrapsBlankLineSeparatedParagraphsInPTags() {
    byte[] bytes =
        "First paragraph.\n\nSecond paragraph,\nstill second.".getBytes(StandardCharsets.UTF_8);

    ImportedBook book = importer.importFrom(bytes);

    assertThat(book.title()).isNull();
    assertThat(book.sections()).hasSize(1);
    String html = book.sections().get(0).html();
    assertThat(html).contains("<p>First paragraph.</p>");
    assertThat(html).contains("Second paragraph,<br/>still second.");
  }

  @Test
  void escapesHtmlSpecialCharacters() {
    byte[] bytes = "Tom & Jerry <fight>".getBytes(StandardCharsets.UTF_8);

    ImportedBook book = importer.importFrom(bytes);

    assertThat(book.sections().get(0).html()).contains("Tom &amp; Jerry &lt;fight&gt;");
  }

  @Test
  void rejectsEmptyFile() {
    assertThatThrownBy(() -> importer.importFrom(new byte[0])).isInstanceOf(ImportException.class);
  }
}
