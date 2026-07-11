package io.mikoshift.natsu.service.bookimport;

import io.mikoshift.natsu.entity.Document.SourceFormat;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

@Component
public class MarkdownImporter implements BookImporter {

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();

    @Override
    public SourceFormat supportedFormat() {
        return SourceFormat.MARKDOWN;
    }

    @Override
    public ImportedBook importFrom(byte[] sourceBytes) {
        String markdown = new String(sourceBytes, StandardCharsets.UTF_8).strip();
        if (markdown.isEmpty()) {
            throw new ImportException("Markdown file is empty");
        }

        Node document = parser.parse(markdown);
        String html = renderer.render(document);
        String title = firstHeadingText(document);

        return new ImportedBook(title, List.of(new ImportedSection("section-0", null, html)));
    }

    private static String firstHeadingText(Node document) {
        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading heading && heading.getLevel() == 1) {
                StringBuilder text = new StringBuilder();
                for (Node child = heading.getFirstChild(); child != null; child = child.getNext()) {
                    if (child instanceof Text textNode) {
                        text.append(textNode.getLiteral());
                    }
                }
                String result = text.toString().strip();
                return result.isEmpty() ? null : result;
            }
        }
        return null;
    }
}
