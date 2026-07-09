package io.mikoshift.natsu.backend.service.bookimport;

import io.mikoshift.natsu.backend.entity.Document.SourceFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

/**
 * Reads container.xml -> the OPF manifest/spine -> each spine item's XHTML body, in reading
 * order. Covers a standard, well-formed EPUB 2/3 package; it does not handle DRM/encryption or
 * fall back to a table-of-contents-derived reading order when the spine is missing.
 */
@Component
public class EpubImporter implements BookImporter {

    @Override
    public SourceFormat supportedFormat() {
        return SourceFormat.EPUB;
    }

    @Override
    public ImportedBook importFrom(byte[] sourceBytes) {
        Map<String, byte[]> entries = readZipEntries(sourceBytes);

        byte[] containerXml = entries.get("META-INF/container.xml");
        if (containerXml == null) {
            throw new ImportException("EPUB is missing META-INF/container.xml");
        }
        String opfPath = parseOpfPath(containerXml);

        byte[] opfBytes = entries.get(opfPath);
        if (opfBytes == null) {
            throw new ImportException("EPUB manifest not found at " + opfPath);
        }
        Document opf = Jsoup.parse(new String(opfBytes, StandardCharsets.UTF_8), "", Parser.xmlParser());

        String title = extractTitle(opf);
        Map<String, String> manifest = extractManifest(opf);
        List<String> spineIds = extractSpineOrder(opf);
        if (spineIds.isEmpty()) {
            throw new ImportException("EPUB spine has no reading-order items");
        }

        String opfDir = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/')) : "";
        List<ImportedSection> sections = new ArrayList<>();
        int index = 0;
        for (String idref : spineIds) {
            String href = manifest.get(idref);
            if (href == null) {
                continue;
            }
            String path = resolvePath(opfDir, href);
            byte[] content = entries.get(path);
            if (content == null) {
                continue;
            }
            Document xhtml = Jsoup.parse(new String(content, StandardCharsets.UTF_8));
            String sectionTitle = firstHeadingText(xhtml);
            String bodyHtml = xhtml.body() != null ? xhtml.body().html() : "";
            sections.add(new ImportedSection("section-" + index, sectionTitle, bodyHtml));
            index++;
        }
        if (sections.isEmpty()) {
            throw new ImportException("None of the EPUB spine items could be read");
        }

        return new ImportedBook(title, sections);
    }

    private static Map<String, byte[]> readZipEntries(byte[] sourceBytes) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(sourceBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zip.readAllBytes());
                }
            }
        } catch (IOException e) {
            throw new ImportException("EPUB is not a valid zip archive", e);
        }
        if (entries.isEmpty()) {
            throw new ImportException("EPUB archive is empty");
        }
        return entries;
    }

    private static String parseOpfPath(byte[] containerXml) {
        Document container = Jsoup.parse(new String(containerXml, StandardCharsets.UTF_8), "", Parser.xmlParser());
        Element rootfile = container.selectFirst("rootfile[full-path]");
        if (rootfile == null) {
            throw new ImportException("container.xml has no rootfile entry");
        }
        return rootfile.attr("full-path");
    }

    private static String extractTitle(Document opf) {
        Elements candidates = opf.select("metadata > title, metadata > dc\\:title");
        if (candidates.isEmpty()) {
            return null;
        }
        String text = candidates.first().text().strip();
        return text.isEmpty() ? null : text;
    }

    private static Map<String, String> extractManifest(Document opf) {
        Map<String, String> manifest = new LinkedHashMap<>();
        for (Element item : opf.select("manifest > item[id][href]")) {
            manifest.put(item.attr("id"), item.attr("href"));
        }
        return manifest;
    }

    private static List<String> extractSpineOrder(Document opf) {
        List<String> ids = new ArrayList<>();
        for (Element itemref : opf.select("spine > itemref[idref]")) {
            ids.add(itemref.attr("idref"));
        }
        return ids;
    }

    private static String firstHeadingText(Document xhtml) {
        Element heading = xhtml.selectFirst("h1, h2, h3, h4, h5, h6");
        if (heading == null) {
            return null;
        }
        String text = heading.text().strip();
        return text.isEmpty() ? null : text;
    }

    private static String resolvePath(String baseDir, String href) {
        String decoded = URLDecoder.decode(href, StandardCharsets.UTF_8);
        String combined = baseDir.isEmpty() ? decoded : baseDir + "/" + decoded;
        Deque<String> stack = new ArrayDeque<>();
        for (String segment : combined.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (!stack.isEmpty()) {
                    stack.removeLast();
                }
            } else {
                stack.addLast(segment);
            }
        }
        return String.join("/", stack);
    }
}
