package io.mikoshift.natsu.service.bookimport;

import io.mikoshift.natsu.entity.Document.SourceFormat;
import io.mikoshift.natsu.util.ZipUtils;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Zip-based formats (EPUB, DOCX) are indistinguishable by extension alone, so detection sniffs the
 * actual bytes: the zip local-file-header signature, then which entries are inside. Formats with no
 * reliable magic bytes of their own (Markdown, plain text) fall back to the filename extension.
 */
@Component
public class FormatDetector {

    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] RTF_MAGIC = "{\\rtf".getBytes(StandardCharsets.US_ASCII);

    public SourceFormat detect(String filename, byte[] content) {
        String extension = extensionOf(filename);

        if (startsWith(content, ZIP_MAGIC)) {
            return detectZipFormat(content, extension, filename);
        }
        if (startsWith(content, RTF_MAGIC)) {
            return SourceFormat.RTF;
        }
        if (looksLikeFb2(content)) {
            return SourceFormat.FB2;
        }

        // Magic-byte sniffing didn't produce a confident answer (e.g. the upload is simply
        // corrupt). Trust a recognized extension anyway and let the async importer -- which
        // already turns a malformed file into a FAILED document with a real error message --
        // be the one to reject it, rather than gatekeeping synchronously on content we can't
        // otherwise identify.
        return switch (extension) {
            case "epub" -> SourceFormat.EPUB;
            case "md", "markdown" -> SourceFormat.MARKDOWN;
            case "txt" -> SourceFormat.PLAIN_TEXT;
            case "fb2" -> SourceFormat.FB2;
            case "docx" -> SourceFormat.DOCX;
            case "rtf" -> SourceFormat.RTF;
            default -> throw new ImportException("Unsupported or unrecognized file type: " + filename);
        };
    }

    public String titleFromFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "Untitled";
        }
        String base = filename.contains("/") ? filename.substring(filename.lastIndexOf('/') + 1) : filename;
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }

    private static SourceFormat detectZipFormat(byte[] content, String extension, String filename) {
        Map<String, byte[]> entries;
        try {
            entries = ZipUtils.readEntries(content);
        } catch (UncheckedIOException e) {
            throw new ImportException("File looks like a zip archive but could not be read: " + filename);
        }
        if (entries.containsKey("META-INF/container.xml") || entries.containsKey("mimetype")) {
            return SourceFormat.EPUB;
        }
        if (entries.keySet().stream().anyMatch(name -> name.startsWith("word/"))) {
            return SourceFormat.DOCX;
        }
        if ("epub".equals(extension)) {
            return SourceFormat.EPUB;
        }
        if ("docx".equals(extension)) {
            return SourceFormat.DOCX;
        }
        throw new ImportException("Unrecognized zip-based file type: expected EPUB or DOCX");
    }

    private static boolean looksLikeFb2(byte[] content) {
        int len = Math.min(content.length, 1024);
        String head = new String(content, 0, len, StandardCharsets.UTF_8);
        return head.contains("<FictionBook");
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        return dot >= 0 ? lower.substring(dot + 1) : "";
    }
}
