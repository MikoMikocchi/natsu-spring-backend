package io.mikoshift.natsu.service.bookimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PlainTextImporterTest {

    private final PlainTextImporter importer = new PlainTextImporter();

    @Test
    void splitsBlankLineSeparatedParagraphsIntoBlocks() {
        byte[] bytes = "First paragraph.\n\nSecond paragraph,\nstill second.".getBytes(StandardCharsets.UTF_8);

        ImportedBook book = importer.importFrom(bytes);

        assertThat(book.title()).isNull();
        assertThat(book.sections()).hasSize(1);
        var blocks = book.sections().get(0).blocks();
        assertThat(blocks).hasSize(2);
        assertThat(((ParagraphBlock) blocks.get(0)).text()).isEqualTo("First paragraph.");
        assertThat(((ParagraphBlock) blocks.get(1)).text()).isEqualTo("Second paragraph,\nstill second.");
    }

    @Test
    void preservesTextVerbatimWithoutHtmlEscaping() {
        byte[] bytes = "Tom & Jerry <fight>".getBytes(StandardCharsets.UTF_8);

        ImportedBook book = importer.importFrom(bytes);

        assertThat(((ParagraphBlock) book.sections().get(0).blocks().get(0)).text())
                .isEqualTo("Tom & Jerry <fight>");
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> importer.importFrom(new byte[0])).isInstanceOf(ImportException.class);
    }
}
