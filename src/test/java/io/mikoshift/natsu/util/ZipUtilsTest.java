package io.mikoshift.natsu.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class ZipUtilsTest {

    @Test
    void readsSmallZipEntries() {
        byte[] zip = zipOf("hello.txt", "world".getBytes(StandardCharsets.UTF_8));

        assertThat(ZipUtils.readEntries(zip)).containsEntry("hello.txt", "world".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsEntryThatExceedsPerEntryDecompressedLimit() {
        byte[] zip = zipOf("bomb.bin", new byte[64 * 1024]);

        assertThatThrownBy(() -> ZipUtils.readEntries(zip, 32 * 1024, 1024 * 1024))
                .isInstanceOf(UncheckedIOException.class)
                .hasCauseInstanceOf(ZipExpansionLimitExceededException.class)
                .hasRootCauseMessage("Zip entry 'bomb.bin' exceeds decompressed size limit of 32768 bytes");
    }

    @Test
    void rejectsArchiveThatExceedsTotalDecompressedLimit() {
        byte[] zip = zipOfEntries(entry("a.bin", new byte[24 * 1024]), entry("b.bin", new byte[24 * 1024]));

        assertThatThrownBy(() -> ZipUtils.readEntries(zip, 64 * 1024, 32 * 1024))
                .isInstanceOf(UncheckedIOException.class)
                .hasCauseInstanceOf(ZipExpansionLimitExceededException.class)
                .hasRootCauseMessage("Zip archive exceeds total decompressed size limit of 32768 bytes");
    }

    private static byte[] zipOf(String entryName, byte[] content) {
        return zipOfEntries(entry(entryName, content));
    }

    private static ZipEntryPayload entry(String entryName, byte[] content) {
        return new ZipEntryPayload(entryName, content);
    }

    private static byte[] zipOfEntries(ZipEntryPayload... entries) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
                for (ZipEntryPayload entry : entries) {
                    zip.putNextEntry(new ZipEntry(entry.name()));
                    zip.write(entry.content());
                    zip.closeEntry();
                }
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record ZipEntryPayload(String name, byte[] content) {}
}
