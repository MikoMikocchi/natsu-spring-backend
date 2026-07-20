package io.mikoshift.natsu.service.bookimport;

import io.mikoshift.natsu.entity.Document.SourceFormat;
import io.mikoshift.natsu.service.bookimport.PackageManifest.ManifestSection;
import io.mikoshift.natsu.service.bookimport.PackageManifest.ManifestTocNode;
import io.mikoshift.natsu.util.HashUtils;
import io.mikoshift.natsu.util.TextCountUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds the on-disk package format (schema version 2): a zip of manifest.json, one JSON block
 * array per section (sections/&lt;id&gt;.json), and content-addressed assets (assets/&lt;sha256&gt;.ext).
 */
@Component
@RequiredArgsConstructor
public class PackageBuilder {

    private static final int SCHEMA_VERSION = 2;

    private final ObjectMapper objectMapper;

    public byte[] buildZip(String title, SourceFormat sourceFormat, ImportedBook book) {
        List<ManifestSection> manifestSections = new ArrayList<>();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (ImportedSection section : book.sections()) {
                byte[] json = writeSectionBlocks(section.blocks());
                String path = "sections/" + section.id() + ".json";
                writeEntry(zip, path, json);
                manifestSections.add(new ManifestSection(
                        section.id(), section.title(), path, wordCount(section.blocks()), HashUtils.sha256Hex(json)));
            }
            Set<String> writtenAssetIds = new HashSet<>();
            for (ImportedAsset asset : book.assets()) {
                if (!writtenAssetIds.add(asset.sha256())) {
                    continue;
                }
                writeEntry(zip, assetPath(asset), asset.content());
            }

            PackageManifest manifest = new PackageManifest(
                    SCHEMA_VERSION,
                    title,
                    book.authors(),
                    book.language(),
                    book.coverAssetId(),
                    sourceFormat,
                    toManifestToc(book.toc()),
                    manifestSections);
            writeEntry(zip, "manifest.json", objectMapper.writeValueAsBytes(manifest));
        } catch (IOException e) {
            throw new ImportException("Failed to build package archive", e);
        }
        return buffer.toByteArray();
    }

    public String extractPlainText(List<ImportedSection> sections) {
        StringBuilder text = new StringBuilder();
        for (ImportedSection section : sections) {
            for (Block block : section.blocks()) {
                String blockText = plainTextOf(block);
                if (!blockText.isBlank()) {
                    text.append(blockText).append('\n');
                }
            }
        }
        return text.toString().strip();
    }

    private byte[] writeSectionBlocks(List<Block> blocks) throws IOException {
        ArrayNode array = objectMapper.createArrayNode();
        for (Block block : blocks) {
            ObjectNode node = (ObjectNode) objectMapper.valueToTree(block);
            node.put("type", blockType(block));
            array.add(node);
        }
        return objectMapper.writeValueAsBytes(array);
    }

    private static String blockType(Block block) {
        return switch (block) {
            case ParagraphBlock b -> "paragraph";
            case HeadingBlock b -> "heading";
            case ImageBlock b -> "image";
            case BlockquoteBlock b -> "blockquote";
            case ListItemBlock b -> "list_item";
            case DividerBlock b -> "divider";
        };
    }

    private static List<ManifestTocNode> toManifestToc(List<TocNode> toc) {
        return toc.stream()
                .map(node -> new ManifestTocNode(node.title(), node.sectionId(), toManifestToc(node.children())))
                .toList();
    }

    private static int wordCount(List<Block> blocks) {
        int count = 0;
        for (Block block : blocks) {
            count += TextCountUtils.countReadingUnits(plainTextOf(block));
        }
        return count;
    }

    private static String plainTextOf(Block block) {
        return switch (block) {
            case ParagraphBlock b -> b.text();
            case HeadingBlock b -> b.text();
            case BlockquoteBlock b -> b.text();
            case ListItemBlock b -> b.text();
            case ImageBlock b -> b.alt() != null ? b.alt() : "";
            case DividerBlock b -> "";
        };
    }

    private static String assetPath(ImportedAsset asset) {
        return "assets/" + asset.sha256() + extensionFor(asset.contentType());
    }

    private static String extensionFor(String contentType) {
        if (contentType == null) {
            return "";
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/svg+xml" -> ".svg";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }

    private static void writeEntry(ZipOutputStream zip, String path, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content);
        zip.closeEntry();
    }
}
