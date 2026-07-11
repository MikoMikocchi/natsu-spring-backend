package io.mikoshift.natsu.service.bookimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MarkdownImporterTest {

    private final MarkdownImporter importer = new MarkdownImporter();

    @Test
    void extractsTitleFromLeadingH1AndEmitsBlocks() {
        byte[] bytes = "# My Book\n\nSome *emphasised* text.".getBytes(StandardCharsets.UTF_8);

        ImportedBook book = importer.importFrom(bytes);

        assertThat(book.title()).isEqualTo("My Book");
        assertThat(book.sections()).hasSize(1);
        var blocks = book.sections().get(0).blocks();
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0)).isInstanceOf(HeadingBlock.class);
        assertThat(((HeadingBlock) blocks.get(0)).text()).isEqualTo("My Book");

        ParagraphBlock paragraph = (ParagraphBlock) blocks.get(1);
        assertThat(paragraph.text()).isEqualTo("Some emphasised text.");
        assertThat(paragraph.marks()).containsExactly(new Mark(Mark.MarkType.ITALIC, 5, 15));
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
