package io.mikoshift.natsu.service.bookimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mikoshift.natsu.config.NatsuProperties;
import io.mikoshift.natsu.config.NatsuPropertiesFixtures;
import io.mikoshift.natsu.entity.Document.SourceFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FormatDetectorTest {

    private FormatDetector detector;

    @BeforeEach
    void setUp() {
        detector = new FormatDetector(testProperties());
    }

    @Test
    void detectsEpubByContainerXmlEntryRegardlessOfExtension() {
        byte[] zip = zipOf("META-INF/container.xml", "<container/>");

        assertThat(detector.detect("book.bin", zip)).isEqualTo(SourceFormat.EPUB);
    }

    @Test
    void detectsDocxByWordEntry() {
        byte[] zip = zipOf("word/document.xml", "<document/>");

        assertThat(detector.detect("book.bin", zip)).isEqualTo(SourceFormat.DOCX);
    }

    @Test
    void detectsRtfByMagicBytes() {
        byte[] content = "{\\rtf1 hello}".getBytes(StandardCharsets.US_ASCII);

        assertThat(detector.detect("book.bin", content)).isEqualTo(SourceFormat.RTF);
    }

    @Test
    void detectsFb2ByFictionBookRootElement() {
        byte[] content = "<?xml version=\"1.0\"?><FictionBook><body/></FictionBook>".getBytes(StandardCharsets.UTF_8);

        assertThat(detector.detect("book.bin", content)).isEqualTo(SourceFormat.FB2);
    }

    @Test
    void fallsBackToExtensionForMarkdownAndPlainText() {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

        assertThat(detector.detect("notes.md", content)).isEqualTo(SourceFormat.MARKDOWN);
        assertThat(detector.detect("notes.txt", content)).isEqualTo(SourceFormat.PLAIN_TEXT);
    }

    @Test
    void rejectsUnrecognizedContent() {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> detector.detect("mystery.bin", content)).isInstanceOf(ImportException.class);
    }

    private static byte[] zipOf(String entryName, String content) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write(content.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static NatsuProperties testProperties() {
        return NatsuPropertiesFixtures.minimal("/tmp/natsu-test", 52_428_800L, 524_288_000L);
    }
}
