package io.mikoshift.natsu.service.bookimport;

import io.mikoshift.natsu.entity.Document.SourceFormat;
import io.mikoshift.natsu.service.bookimport.Mark.MarkType;
import io.mikoshift.natsu.util.HashUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

/**
 * Parses FictionBook (FB2) XML. Every top-level and nested {@code <section>} under {@code <body>}
 * becomes its own {@link ImportedSection}, flattened into a single reading-order list; the original
 * nesting is preserved as the hierarchical {@link TocNode} tree returned alongside it. {@code
 * <binary>} elements are decoded once up front and resolved into content-addressed {@link
 * ImportedAsset}s wherever a {@code <coverpage>}/{@code <image>} references them.
 */
@Component
public class Fb2Importer implements BookImporter {

    @Override
    public SourceFormat supportedFormat() {
        return SourceFormat.FB2;
    }

    @Override
    public ImportedBook importFrom(byte[] sourceBytes) {
        Document doc;
        try {
            doc = Jsoup.parse(new String(sourceBytes, StandardCharsets.UTF_8), "", Parser.xmlParser());
        } catch (RuntimeException e) {
            throw new ImportException("FB2 file is not well-formed XML", e);
        }
        if (doc.selectFirst("FictionBook") == null) {
            throw new ImportException("Not a valid FB2 (FictionBook) document");
        }

        Map<String, FbBinary> binaries = extractBinaries(doc);
        Map<String, String> assetIdByHref = new HashMap<>();
        List<ImportedAsset> assets = new ArrayList<>();

        String title = textOf(doc.selectFirst("description > title-info > book-title"));
        List<String> authors = extractAuthors(doc);
        String language = textOf(doc.selectFirst("description > title-info > lang"));
        String coverAssetId = extractCover(doc, binaries, assetIdByHref, assets);

        Element body = doc.selectFirst("FictionBook > body, body");
        if (body == null) {
            throw new ImportException("FB2 document has no <body>");
        }

        List<ImportedSection> sections = new ArrayList<>();
        List<TocNode> toc = new ArrayList<>();
        int[] counter = {0};
        for (Element child : body.children()) {
            if (child.tagName().equalsIgnoreCase("section")) {
                toc.add(convertSection(child, counter, binaries, assetIdByHref, assets, sections));
            }
        }
        if (sections.isEmpty()) {
            throw new ImportException("FB2 document has no readable sections");
        }

        return new ImportedBook(title, authors, language, coverAssetId, toc, sections, assets);
    }

    private static List<String> extractAuthors(Document doc) {
        List<String> authors = new ArrayList<>();
        for (Element author : doc.select("description > title-info > author")) {
            String first = textOf(author.selectFirst("first-name"));
            String last = textOf(author.selectFirst("last-name"));
            String name = ((first == null ? "" : first) + " " + (last == null ? "" : last)).strip();
            if (name.isEmpty()) {
                name = textOf(author.selectFirst("nickname"));
            }
            if (name != null && !name.isEmpty()) {
                authors.add(name);
            }
        }
        return authors;
    }

    private static String extractCover(
            Document doc,
            Map<String, FbBinary> binaries,
            Map<String, String> assetIdByHref,
            List<ImportedAsset> assets) {
        Element image = doc.selectFirst("description > title-info > coverpage > image");
        if (image == null) {
            return null;
        }
        return resolveAsset(hrefOf(image), binaries, assetIdByHref, assets);
    }

    // ---------------------------------------------------------------- section -> blocks/toc

    private static TocNode convertSection(
            Element sectionEl,
            int[] counter,
            Map<String, FbBinary> binaries,
            Map<String, String> assetIdByHref,
            List<ImportedAsset> assets,
            List<ImportedSection> sectionSink) {
        String id = "section-" + counter[0]++;
        // Reserved before descending into any nested <section> so this section lands before its
        // children in the flattened, reading-order sink (pre-order), not after.
        int selfIndex = sectionSink.size();
        sectionSink.add(null);
        List<Block> blocks = new ArrayList<>();
        List<TocNode> children = new ArrayList<>();
        String title = null;
        int[] blockCounter = {0};

        for (Element child : sectionEl.children()) {
            switch (child.tagName().toLowerCase(Locale.ROOT)) {
                case "title" -> {
                    String text = child.text().strip();
                    if (!text.isEmpty()) {
                        title = title == null ? text : title;
                        blocks.add(new HeadingBlock(nextId(id, blockCounter), 1, text, List.of()));
                    }
                }
                case "subtitle" -> {
                    String text = child.text().strip();
                    if (!text.isEmpty()) {
                        blocks.add(new HeadingBlock(nextId(id, blockCounter), 2, text, List.of()));
                    }
                }
                case "p" -> {
                    TextWithMarks t = extractText(child);
                    if (!t.text().isBlank()) {
                        blocks.add(new ParagraphBlock(nextId(id, blockCounter), t.text(), t.marks(), List.of()));
                    }
                }
                case "cite", "epigraph" -> {
                    for (Element p : child.select("> p")) {
                        TextWithMarks t = extractText(p);
                        if (!t.text().isBlank()) {
                            blocks.add(new BlockquoteBlock(nextId(id, blockCounter), t.text(), t.marks()));
                        }
                    }
                }
                case "empty-line" -> blocks.add(new DividerBlock(nextId(id, blockCounter)));
                case "image" -> {
                    String assetId = resolveAsset(hrefOf(child), binaries, assetIdByHref, assets);
                    if (assetId != null) {
                        blocks.add(new ImageBlock(nextId(id, blockCounter), assetId, null));
                    }
                }
                case "section" ->
                    children.add(convertSection(child, counter, binaries, assetIdByHref, assets, sectionSink));
                default -> {}
            }
        }

        sectionSink.set(selfIndex, new ImportedSection(id, title, blocks));
        return new TocNode(title, id, children);
    }

    private static String nextId(String sectionId, int[] counter) {
        return sectionId + "-b" + (counter[0]++);
    }

    // ---------------------------------------------------------------- inline text/marks

    private record TextWithMarks(String text, List<Mark> marks) {}

    private static TextWithMarks extractText(Element el) {
        StringBuilder text = new StringBuilder();
        List<Mark> marks = new ArrayList<>();
        for (Node child : el.childNodes()) {
            appendNode(child, text, marks);
        }
        return new TextWithMarks(text.toString().strip(), marks);
    }

    private static void appendNode(Node node, StringBuilder text, List<Mark> marks) {
        if (node instanceof TextNode textNode) {
            text.append(textNode.text());
            return;
        }
        if (!(node instanceof Element element)) {
            return;
        }
        String tag = element.tagName().toLowerCase(Locale.ROOT);
        if (tag.equals("empty-line")) {
            text.append('\n');
            return;
        }
        int start = text.length();
        for (Node child : element.childNodes()) {
            appendNode(child, text, marks);
        }
        int end = text.length();
        if (end > start) {
            if (tag.equals("strong")) {
                marks.add(new Mark(MarkType.BOLD, start, end));
            } else if (tag.equals("emphasis")) {
                marks.add(new Mark(MarkType.ITALIC, start, end));
            }
        }
    }

    // ---------------------------------------------------------------- binaries / assets

    private static Map<String, FbBinary> extractBinaries(Document doc) {
        Map<String, FbBinary> binaries = new HashMap<>();
        for (Element bin : doc.select("FictionBook > binary, binary")) {
            String id = bin.attr("id");
            if (id.isEmpty()) {
                continue;
            }
            String contentType = bin.attr("content-type");
            byte[] content;
            try {
                content = Base64.getMimeDecoder().decode(bin.text().strip());
            } catch (IllegalArgumentException e) {
                continue;
            }
            binaries.put(id, new FbBinary(contentType.isEmpty() ? "application/octet-stream" : contentType, content));
        }
        return binaries;
    }

    private static String hrefOf(Element el) {
        String href = el.attr("l:href");
        return !href.isEmpty() ? href : el.attr("href");
    }

    private static String resolveAsset(
            String href,
            Map<String, FbBinary> binaries,
            Map<String, String> assetIdByHref,
            List<ImportedAsset> assets) {
        if (href == null || href.isBlank()) {
            return null;
        }
        String id = href.startsWith("#") ? href.substring(1) : href;
        String cached = assetIdByHref.get(id);
        if (cached != null) {
            return cached;
        }
        FbBinary bin = binaries.get(id);
        if (bin == null) {
            return null;
        }
        String sha256 = HashUtils.sha256Hex(bin.content());
        assetIdByHref.put(id, sha256);
        assets.add(new ImportedAsset(sha256, bin.contentType(), bin.content()));
        return sha256;
    }

    private static String textOf(Element el) {
        if (el == null) {
            return null;
        }
        String text = el.text().strip();
        return text.isEmpty() ? null : text;
    }

    private record FbBinary(String contentType, byte[] content) {}
}
