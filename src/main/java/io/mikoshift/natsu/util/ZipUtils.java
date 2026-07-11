package io.mikoshift.natsu.util;

import io.mikoshift.natsu.config.NatsuProperties;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ZipUtils {

    /** Matches default {@code natsu.max-package-bytes} in application.yml. */
    public static final long DEFAULT_MAX_PACKAGE_BYTES = 52_428_800L;

    /** {@value io.mikoshift.natsu.config.NatsuProperties#ZIP_DECOMPRESSED_RATIO_PER_ENTRY}× {@link #DEFAULT_MAX_PACKAGE_BYTES}. */
    public static final long DEFAULT_MAX_DECOMPRESSED_BYTES_PER_ENTRY =
            DEFAULT_MAX_PACKAGE_BYTES * NatsuProperties.ZIP_DECOMPRESSED_RATIO_PER_ENTRY;

    /** {@value io.mikoshift.natsu.config.NatsuProperties#ZIP_DECOMPRESSED_RATIO_TOTAL}× {@link #DEFAULT_MAX_PACKAGE_BYTES}. */
    public static final long DEFAULT_MAX_DECOMPRESSED_BYTES_TOTAL =
            DEFAULT_MAX_PACKAGE_BYTES * NatsuProperties.ZIP_DECOMPRESSED_RATIO_TOTAL;

    private static final int READ_BUFFER_SIZE = 8192;

    private ZipUtils() {}

    /**
     * Reads every non-directory entry using {@link #DEFAULT_MAX_DECOMPRESSED_BYTES_PER_ENTRY} and
     * {@link #DEFAULT_MAX_DECOMPRESSED_BYTES_TOTAL}.
     *
     * @throws UncheckedIOException if the bytes aren't a readable zip archive or limits are exceeded.
     */
    public static Map<String, byte[]> readEntries(byte[] zipBytes) {
        return readEntries(
                zipBytes, DEFAULT_MAX_DECOMPRESSED_BYTES_PER_ENTRY, DEFAULT_MAX_DECOMPRESSED_BYTES_TOTAL);
    }

    /**
     * @param maxDecompressedBytesPerEntry abort when a single entry exceeds this many decompressed bytes
     * @param maxDecompressedBytesTotal abort when the sum across all entries exceeds this many bytes
     * @throws UncheckedIOException if the bytes aren't a readable zip archive or limits are exceeded.
     */
    public static Map<String, byte[]> readEntries(
            byte[] zipBytes, long maxDecompressedBytesPerEntry, long maxDecompressedBytesTotal) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long totalDecompressed = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    byte[] content = readEntryBytes(
                            zip,
                            entry.getName(),
                            maxDecompressedBytesPerEntry,
                            maxDecompressedBytesTotal,
                            totalDecompressed);
                    totalDecompressed += content.length;
                    entries.put(entry.getName(), content);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return entries;
    }

    private static byte[] readEntryBytes(
            ZipInputStream zip,
            String entryName,
            long maxDecompressedBytesPerEntry,
            long maxDecompressedBytesTotal,
            long totalDecompressedBeforeEntry)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[READ_BUFFER_SIZE];
        long entryDecompressed = 0;
        int read;
        while ((read = zip.read(buffer)) != -1) {
            entryDecompressed += read;
            if (entryDecompressed > maxDecompressedBytesPerEntry) {
                throw new ZipExpansionLimitExceededException(
                        "Zip entry '"
                                + entryName
                                + "' exceeds decompressed size limit of "
                                + maxDecompressedBytesPerEntry
                                + " bytes");
            }
            long totalDecompressed = totalDecompressedBeforeEntry + entryDecompressed;
            if (totalDecompressed > maxDecompressedBytesTotal) {
                throw new ZipExpansionLimitExceededException(
                        "Zip archive exceeds total decompressed size limit of "
                                + maxDecompressedBytesTotal
                                + " bytes");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
