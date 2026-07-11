package io.mikoshift.natsu.service.bookimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mikoshift.natsu.config.NatsuProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EpubImporterTest {

    private EpubImporter importer;

    @BeforeEach
    void setUp() {
        NatsuProperties.RateLimit.Bucket bucket = new NatsuProperties.RateLimit.Bucket(5, 60);
        NatsuProperties.RateLimit rateLimit =
                new NatsuProperties.RateLimit(bucket, bucket, bucket, bucket, bucket, bucket);
        importer = new EpubImporter(new NatsuProperties(
                "/tmp/natsu-test",
                52_428_800L,
                524_288_000L,
                List.of("*"),
                rateLimit,
                "http://localhost:3000/reset-password?token={token}",
                "noreply@example.com",
                new NatsuProperties.BookImportRecovery(true, 15, 5, 3)));
    }

    @Test
    void parsesTitleAndSpineOrderedChapters() {
        byte[] epub = buildEpub(Map.of(
                "META-INF/container.xml", containerXml("OEBPS/content.opf"),
                "OEBPS/content.opf", opfXml(),
                "OEBPS/chapter1.xhtml", chapterXhtml("Chapter One", "Hello world."),
                "OEBPS/chapter2.xhtml", chapterXhtml("Chapter Two", "Second chapter content.")));

        ImportedBook book = importer.importFrom(epub);

        assertThat(book.title()).isEqualTo("My Test Book");
        assertThat(book.authors()).containsExactly("Jane Author");
        assertThat(book.sections()).hasSize(2);
        assertThat(book.sections().get(0).title()).isEqualTo("Chapter One");
        assertThat(paragraphText(book.sections().get(0))).isEqualTo("Hello world.");
        assertThat(book.sections().get(1).title()).isEqualTo("Chapter Two");
        assertThat(paragraphText(book.sections().get(1))).isEqualTo("Second chapter content.");
    }

    @Test
    void extractsBoldAndItalicMarks() {
        byte[] epub = buildEpub(Map.of(
                "META-INF/container.xml", containerXml("OEBPS/content.opf"),
                "OEBPS/content.opf", opfXml(),
                "OEBPS/chapter1.xhtml",
                        chapterXhtmlRaw("Chapter One", "<p>Some <strong>bold</strong> and <em>italic</em> text.</p>")));

        ImportedBook book = importer.importFrom(epub);

        ParagraphBlock paragraph = (ParagraphBlock) book.sections().get(0).blocks().stream()
                .filter(ParagraphBlock.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertThat(paragraph.text()).isEqualTo("Some bold and italic text.");
        assertThat(paragraph.marks())
                .extracting(Mark::type)
                .containsExactlyInAnyOrder(Mark.MarkType.BOLD, Mark.MarkType.ITALIC);
    }

    @Test
    void rejectsArchiveWithoutContainerXml() {
        byte[] epub = buildEpub(Map.of("OEBPS/content.opf", opfXml()));

        assertThatThrownBy(() -> importer.importFrom(epub)).isInstanceOf(ImportException.class);
    }

    @Test
    void rejectsNonZipInput() {
        assertThatThrownBy(() -> importer.importFrom("not a zip".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ImportException.class);
    }

    private static String paragraphText(ImportedSection section) {
        return section.blocks().stream()
                .filter(ParagraphBlock.class::isInstance)
                .map(b -> ((ParagraphBlock) b).text())
                .findFirst()
                .orElseThrow();
    }

    private static String containerXml(String opfPath) {
        return """
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="%s" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.formatted(opfPath);
    }

    private static String opfXml() {
        return """
                <?xml version="1.0"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>My Test Book</dc:title>
                    <dc:creator>Jane Author</dc:creator>
                  </metadata>
                  <manifest>
                    <item id="ch1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="ch2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="ch1"/>
                    <itemref idref="ch2"/>
                  </spine>
                </package>
                """;
    }

    private static String chapterXhtml(String heading, String paragraph) {
        return chapterXhtmlRaw(heading, "<p>%s</p>".formatted(paragraph));
    }

    private static String chapterXhtmlRaw(String heading, String bodyExtra) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>%s</title></head>
                <body><h1>%s</h1>%s</body>
                </html>
                """.formatted(heading, heading, bodyExtra);
    }

    private static byte[] buildEpub(Map<String, String> entries) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, String> entry : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
