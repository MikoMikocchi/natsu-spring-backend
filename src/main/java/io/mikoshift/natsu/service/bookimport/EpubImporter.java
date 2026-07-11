package io.mikoshift.natsu.service.bookimport;

import io.mikoshift.natsu.config.NatsuProperties;
import io.mikoshift.natsu.entity.Document.SourceFormat;
import io.mikoshift.natsu.service.bookimport.InlineObject.InlineObjectType;
import io.mikoshift.natsu.service.bookimport.Mark.MarkType;
import io.mikoshift.natsu.util.HashUtils;
import io.mikoshift.natsu.util.ZipExpansionLimitExceededException;
import io.mikoshift.natsu.util.ZipUtils;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

/**
 * Reads container.xml -> the OPF manifest/spine -> each spine item's XHTML body, in reading order,
 * converting each item into blocks (see {@link Block}) rather than raw HTML. Also extracts inline
 * images/cover as content-addressed assets and a hierarchical table of contents from the EPUB3 nav
 * document (falling back to the EPUB2 toc.ncx, then a flat list derived from the spine). Covers a
 * standard, well-formed EPUB 2/3 package; it does not handle DRM/encryption.
 */
@Component
public class EpubImporter implements BookImporter {

    private final NatsuProperties properties;

    public EpubImporter(NatsuProperties properties) {
        this.properties = properties;
    }

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
        List<String> authors = extractAuthors(opf);
        String language = extractLanguage(opf);
        Map<String, String> manifestHrefById = extractManifest(opf);
        List<String> spineIds = extractSpineOrder(opf);
        if (spineIds.isEmpty()) {
            throw new ImportException("EPUB spine has no reading-order items");
        }

        String opfDir = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/')) : "";
        List<ImportedAsset> assets = new ArrayList<>();
        Map<String, String> assetIdByPath = new HashMap<>();

        String coverAssetId = extractCoverAssetId(opf, opfDir, entries, assetIdByPath, assets);

        List<ImportedSection> sections = new ArrayList<>();
        Map<String, String> pathToSectionId = new LinkedHashMap<>();
        int index = 0;
        for (String idref : spineIds) {
            String href = manifestHrefById.get(idref);
            if (href == null) {
                continue;
            }
            String path = resolvePath(opfDir, href);
            byte[] content = entries.get(path);
            if (content == null) {
                continue;
            }
            Document xhtml = Jsoup.parse(new String(content, StandardCharsets.UTF_8));
            String sectionId = "section-" + index;
            String sectionTitle = firstHeadingText(xhtml);
            List<Block> blocks = xhtml.body() != null
                    ? toBlocks(xhtml.body(), sectionId, path, entries, assetIdByPath, assets)
                    : List.of();
            sections.add(new ImportedSection(sectionId, sectionTitle, blocks));
            pathToSectionId.put(path, sectionId);
            index++;
        }
        if (sections.isEmpty()) {
            throw new ImportException("None of the EPUB spine items could be read");
        }

        List<TocNode> toc = extractToc(opf, opfDir, entries, manifestHrefById, pathToSectionId, sections);

        return new ImportedBook(title, authors, language, coverAssetId, toc, sections, assets);
    }

    // ---------------------------------------------------------------- zip / opf plumbing

    private Map<String, byte[]> readZipEntries(byte[] sourceBytes) {
        Map<String, byte[]> entries;
        try {
            entries = ZipUtils.readEntries(
                    sourceBytes,
                    properties.maxZipDecompressedBytesPerEntry(),
                    properties.maxZipDecompressedBytesTotal());
        } catch (UncheckedIOException e) {
            if (e.getCause() instanceof ZipExpansionLimitExceededException) {
                throw new ImportException("EPUB decompresses to too much data", e);
            }
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

    private static List<String> extractAuthors(Document opf) {
        Elements candidates = opf.select("metadata > creator, metadata > dc\\:creator");
        return candidates.stream()
                .map(el -> el.text().strip())
                .filter(text -> !text.isEmpty())
                .toList();
    }

    private static String extractLanguage(Document opf) {
        Element el = opf.selectFirst("metadata > language, metadata > dc\\:language");
        if (el == null) {
            return null;
        }
        String text = el.text().strip();
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

    // ---------------------------------------------------------------- cover / assets

    private static String extractCoverAssetId(
            Document opf,
            String opfDir,
            Map<String, byte[]> entries,
            Map<String, String> assetIdByPath,
            List<ImportedAsset> assets) {
        for (Element item : opf.select("manifest > item[href]")) {
            String properties = item.attr("properties").toLowerCase(Locale.ROOT);
            if (properties.contains("cover-image")) {
                return resolveAsset(item.attr("href"), opfDir, entries, assetIdByPath, assets);
            }
        }
        Element meta = opf.selectFirst("metadata > meta[name=cover]");
        if (meta != null) {
            String itemId = meta.attr("content");
            for (Element item : opf.select("manifest > item[href]")) {
                if (item.attr("id").equals(itemId)) {
                    return resolveAsset(item.attr("href"), opfDir, entries, assetIdByPath, assets);
                }
            }
        }
        return null;
    }

    private static String resolveAsset(
            String src,
            String baseDir,
            Map<String, byte[]> entries,
            Map<String, String> assetIdByPath,
            List<ImportedAsset> assets) {
        if (src == null || src.isBlank()) {
            return null;
        }
        String path = resolvePath(baseDir, src);
        String cached = assetIdByPath.get(path);
        if (cached != null) {
            return cached;
        }
        byte[] content = entries.get(path);
        if (content == null) {
            return null;
        }
        String sha256 = HashUtils.sha256Hex(content);
        assetIdByPath.put(path, sha256);
        assets.add(new ImportedAsset(sha256, contentTypeFor(path), content));
        return sha256;
    }

    private static String contentTypeFor(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    // ---------------------------------------------------------------- DOM -> blocks

    private static List<Block> toBlocks(
            Element body,
            String sectionId,
            String basePath,
            Map<String, byte[]> entries,
            Map<String, String> assetIdByPath,
            List<ImportedAsset> assets) {
        List<Block> blocks = new ArrayList<>();
        int[] counter = {0};
        for (Element child : body.children()) {
            walk(child, sectionId, basePath, entries, assetIdByPath, assets, counter, blocks);
        }
        return blocks;
    }

    private static void walk(
            Element el,
            String sectionId,
            String basePath,
            Map<String, byte[]> entries,
            Map<String, String> assetIdByPath,
            List<ImportedAsset> assets,
            int[] counter,
            List<Block> blocks) {
        String tag = el.tagName().toLowerCase(Locale.ROOT);
        switch (tag) {
            case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                TextWithMarks t = extractText(el, basePath, entries, assetIdByPath, assets);
                int level = Integer.parseInt(tag.substring(1));
                blocks.add(new HeadingBlock(nextId(sectionId, counter), level, t.text(), t.marks()));
            }
            case "p" -> {
                Element soleImage = soleImage(el);
                if (soleImage != null) {
                    blocks.add(imageBlock(soleImage, sectionId, basePath, entries, assetIdByPath, assets, counter));
                } else {
                    TextWithMarks t = extractText(el, basePath, entries, assetIdByPath, assets);
                    if (!t.text().isBlank() || !t.inlineObjects().isEmpty()) {
                        blocks.add(
                                new ParagraphBlock(nextId(sectionId, counter), t.text(), t.marks(), t.inlineObjects()));
                    }
                }
            }
            case "blockquote" -> {
                TextWithMarks t = extractText(el, basePath, entries, assetIdByPath, assets);
                blocks.add(new BlockquoteBlock(nextId(sectionId, counter), t.text(), t.marks()));
            }
            case "ul", "ol" -> {
                boolean ordered = tag.equals("ol");
                for (Element li : el.children()) {
                    if (li.tagName().equalsIgnoreCase("li")) {
                        TextWithMarks t = extractText(li, basePath, entries, assetIdByPath, assets);
                        blocks.add(new ListItemBlock(nextId(sectionId, counter), 0, ordered, t.text(), t.marks()));
                    }
                }
            }
            case "hr" -> blocks.add(new DividerBlock(nextId(sectionId, counter)));
            case "figure" -> {
                Element img = el.selectFirst("img");
                if (img != null) {
                    blocks.add(imageBlock(img, sectionId, basePath, entries, assetIdByPath, assets, counter));
                }
            }
            case "img" -> blocks.add(imageBlock(el, sectionId, basePath, entries, assetIdByPath, assets, counter));
            default -> {
                if (!el.children().isEmpty()) {
                    for (Element child : el.children()) {
                        walk(child, sectionId, basePath, entries, assetIdByPath, assets, counter, blocks);
                    }
                } else if (!el.text().isBlank()) {
                    TextWithMarks t = extractText(el, basePath, entries, assetIdByPath, assets);
                    blocks.add(new ParagraphBlock(nextId(sectionId, counter), t.text(), t.marks(), t.inlineObjects()));
                }
            }
        }
    }

    private static Element soleImage(Element paragraph) {
        Elements children = paragraph.children();
        if (children.size() == 1 && children.first().tagName().equalsIgnoreCase("img")) {
            return children.first();
        }
        return null;
    }

    private static ImageBlock imageBlock(
            Element img,
            String sectionId,
            String basePath,
            Map<String, byte[]> entries,
            Map<String, String> assetIdByPath,
            List<ImportedAsset> assets,
            int[] counter) {
        String assetId = resolveAsset(img.attr("src"), baseDirOf(basePath), entries, assetIdByPath, assets);
        String alt = img.attr("alt");
        return new ImageBlock(nextId(sectionId, counter), assetId, alt.isBlank() ? null : alt);
    }

    private static String baseDirOf(String path) {
        return path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
    }

    private static String nextId(String sectionId, int[] counter) {
        return sectionId + "-b" + (counter[0]++);
    }

    private record TextWithMarks(String text, List<Mark> marks, List<InlineObject> inlineObjects) {}

    private static TextWithMarks extractText(
            Element el,
            String basePath,
            Map<String, byte[]> entries,
            Map<String, String> assetIdByPath,
            List<ImportedAsset> assets) {
        StringBuilder text = new StringBuilder();
        List<Mark> marks = new ArrayList<>();
        List<InlineObject> inline = new ArrayList<>();
        for (Node child : el.childNodes()) {
            appendNode(child, text, marks, inline, basePath, entries, assetIdByPath, assets);
        }
        return new TextWithMarks(text.toString().strip(), marks, inline);
    }

    private static void appendNode(
            Node node,
            StringBuilder text,
            List<Mark> marks,
            List<InlineObject> inline,
            String basePath,
            Map<String, byte[]> entries,
            Map<String, String> assetIdByPath,
            List<ImportedAsset> assets) {
        if (node instanceof TextNode textNode) {
            text.append(textNode.text());
            return;
        }
        if (!(node instanceof Element element)) {
            return;
        }
        String tag = element.tagName().toLowerCase(Locale.ROOT);
        if (tag.equals("img")) {
            String assetId = resolveAsset(element.attr("src"), baseDirOf(basePath), entries, assetIdByPath, assets);
            inline.add(new InlineObject(InlineObjectType.IMAGE, text.length(), assetId, null));
            return;
        }
        if (tag.equals("br")) {
            text.append('\n');
            return;
        }
        int start = text.length();
        for (Node child : element.childNodes()) {
            appendNode(child, text, marks, inline, basePath, entries, assetIdByPath, assets);
        }
        int end = text.length();
        if (end > start) {
            if (tag.equals("strong") || tag.equals("b")) {
                marks.add(new Mark(MarkType.BOLD, start, end));
            } else if (tag.equals("em") || tag.equals("i")) {
                marks.add(new Mark(MarkType.ITALIC, start, end));
            }
        }
    }

    // ---------------------------------------------------------------- table of contents

    private static List<TocNode> extractToc(
            Document opf,
            String opfDir,
            Map<String, byte[]> entries,
            Map<String, String> manifestHrefById,
            Map<String, String> pathToSectionId,
            List<ImportedSection> sectionsInOrder) {
        for (Element item : opf.select("manifest > item[href]")) {
            if (item.attr("properties").toLowerCase(Locale.ROOT).contains("nav")) {
                String navPath = resolvePath(opfDir, item.attr("href"));
                byte[] navBytes = entries.get(navPath);
                if (navBytes != null) {
                    List<TocNode> toc = parseNavXhtml(navBytes, navPath, pathToSectionId);
                    if (!toc.isEmpty()) {
                        return toc;
                    }
                }
            }
        }

        Element spine = opf.selectFirst("spine[toc]");
        if (spine != null) {
            String href = manifestHrefById.get(spine.attr("toc"));
            if (href != null) {
                String ncxPath = resolvePath(opfDir, href);
                byte[] ncxBytes = entries.get(ncxPath);
                if (ncxBytes != null) {
                    List<TocNode> toc = parseNcx(ncxBytes, ncxPath, pathToSectionId);
                    if (!toc.isEmpty()) {
                        return toc;
                    }
                }
            }
        }

        return sectionsInOrder.stream()
                .map(section -> new TocNode(section.title(), section.id(), List.of()))
                .toList();
    }

    private static List<TocNode> parseNavXhtml(byte[] navBytes, String navPath, Map<String, String> pathToSectionId) {
        Document nav = Jsoup.parse(new String(navBytes, StandardCharsets.UTF_8));
        Element navEl = nav.select("nav").stream()
                .filter(n -> "toc".equalsIgnoreCase(n.attr("epub:type")))
                .findFirst()
                .orElseGet(() -> nav.selectFirst("nav"));
        if (navEl == null) {
            return List.of();
        }
        Element ol = navEl.selectFirst("ol");
        if (ol == null) {
            return List.of();
        }
        return parseNavList(ol, baseDirOf(navPath), pathToSectionId);
    }

    private static List<TocNode> parseNavList(Element ol, String navDir, Map<String, String> pathToSectionId) {
        List<TocNode> nodes = new ArrayList<>();
        for (Element li : ol.children()) {
            if (!li.tagName().equalsIgnoreCase("li")) {
                continue;
            }
            Element a = li.selectFirst("> a, > span");
            String title = a != null ? a.text().strip() : "";
            String sectionId = null;
            if (a != null && a.hasAttr("href")) {
                String target = resolvePath(navDir, a.attr("href").split("#")[0]);
                sectionId = pathToSectionId.get(target);
            }
            Element childOl = li.selectFirst("> ol");
            List<TocNode> children = childOl != null ? parseNavList(childOl, navDir, pathToSectionId) : List.of();
            if (!title.isEmpty() || sectionId != null || !children.isEmpty()) {
                nodes.add(new TocNode(title.isEmpty() ? null : title, sectionId, children));
            }
        }
        return nodes;
    }

    private static List<TocNode> parseNcx(byte[] ncxBytes, String ncxPath, Map<String, String> pathToSectionId) {
        Document ncx = Jsoup.parse(new String(ncxBytes, StandardCharsets.UTF_8), "", Parser.xmlParser());
        Element navMap = ncx.selectFirst("navMap");
        if (navMap == null) {
            return List.of();
        }
        return parseNavPoints(navMap, baseDirOf(ncxPath), pathToSectionId);
    }

    private static List<TocNode> parseNavPoints(Element parent, String ncxDir, Map<String, String> pathToSectionId) {
        List<TocNode> nodes = new ArrayList<>();
        for (Element navPoint : parent.children()) {
            if (!navPoint.tagName().equalsIgnoreCase("navPoint")) {
                continue;
            }
            Element label = navPoint.selectFirst("> navLabel > text");
            Element content = navPoint.selectFirst("> content[src]");
            String title = label != null ? label.text().strip() : null;
            String sectionId = null;
            if (content != null) {
                String target = resolvePath(ncxDir, content.attr("src").split("#")[0]);
                sectionId = pathToSectionId.get(target);
            }
            nodes.add(new TocNode(
                    title == null || title.isEmpty() ? null : title,
                    sectionId,
                    parseNavPoints(navPoint, ncxDir, pathToSectionId)));
        }
        return nodes;
    }
}
