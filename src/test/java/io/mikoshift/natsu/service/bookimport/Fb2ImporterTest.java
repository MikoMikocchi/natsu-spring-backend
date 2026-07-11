package io.mikoshift.natsu.service.bookimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Fb2ImporterTest {

    private final Fb2Importer importer = new Fb2Importer();

    @Test
    void parsesTitleAuthorAndNestedSections() {
        byte[] fb2 = """
                <?xml version="1.0" encoding="UTF-8"?>
                <FictionBook xmlns:l="http://www.w3.org/1999/xlink">
                  <description>
                    <title-info>
                      <book-title>My FB2 Book</book-title>
                      <author><first-name>Jane</first-name><last-name>Author</last-name></author>
                      <lang>ru</lang>
                    </title-info>
                  </description>
                  <body>
                    <section>
                      <title><p>Part One</p></title>
                      <section>
                        <title><p>Chapter One</p></title>
                        <p>Hello <strong>bold</strong> world.</p>
                      </section>
                    </section>
                  </body>
                </FictionBook>
                """.getBytes(StandardCharsets.UTF_8);

        ImportedBook book = importer.importFrom(fb2);

        assertThat(book.title()).isEqualTo("My FB2 Book");
        assertThat(book.authors()).containsExactly("Jane Author");
        assertThat(book.language()).isEqualTo("ru");
        assertThat(book.sections()).hasSize(2);
        assertThat(book.sections().get(0).title()).isEqualTo("Part One");
        assertThat(book.sections().get(1).title()).isEqualTo("Chapter One");

        ParagraphBlock paragraph = (ParagraphBlock) book.sections().get(1).blocks().stream()
                .filter(ParagraphBlock.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertThat(paragraph.text()).isEqualTo("Hello bold world.");
        assertThat(paragraph.marks()).containsExactly(new Mark(Mark.MarkType.BOLD, 6, 10));

        assertThat(book.toc()).hasSize(1);
        assertThat(book.toc().get(0).title()).isEqualTo("Part One");
        assertThat(book.toc().get(0).children()).hasSize(1);
        assertThat(book.toc().get(0).children().get(0).title()).isEqualTo("Chapter One");
    }

    @Test
    void rejectsNonFb2Xml() {
        byte[] xml = "<root/>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> importer.importFrom(xml)).isInstanceOf(ImportException.class);
    }
}
