package io.mikoshift.natsu.service.bookimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MarkdownImporterTest {

    private final MarkdownImporter importer = new MarkdownImporter();

    @Test
    void extractsTitleFromLeadingH1AndRendersHtml() {
        byte[] bytes = "# My Book\n\nSome *emphasised* text.".getBytes(StandardCharsets.UTF_8);

        ImportedBook book = importer.importFrom(bytes);

        assertThat(book.title()).isEqualTo("My Book");
        assertThat(book.sections()).hasSize(1);
        assertThat(book.sections().get(0).html()).contains("<em>emphasised</em>");
    }

    @Test
    void hasNullTitleWhenNoLeadingH1() {
        byte[] bytes = "Just a paragraph, no heading.".getBytes(StandardCharsets.UTF_8);

        ImportedBook book = importer.importFrom(bytes);

        assertThat(book.title()).isNull();
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> importer.importFrom(new byte[0])).isInstanceOf(ImportException.class);
    }
}
