package io.mikoshift.natsu.service.bookimport;

import io.mikoshift.natsu.entity.Document.SourceFormat;
import io.mikoshift.natsu.service.bookimport.InlineObject.InlineObjectType;
import io.mikoshift.natsu.service.bookimport.Mark.MarkType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;

/** Walks the commonmark AST directly into {@link Block}s -- no intermediate HTML rendering. */
@Component
public class MarkdownImporter implements BookImporter {

    private final Parser parser = Parser.builder().build();

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
        String title = firstHeadingText(document);
        List<Block> blocks = toBlocks(document, "section-0");
        ImportedSection section = new ImportedSection("section-0", title, blocks);

        return ImportedBook.of(title, List.of(section));
    }

    private static String firstHeadingText(Node document) {
        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading heading && heading.getLevel() == 1) {
                String text = extractText(heading).text();
                return text.isEmpty() ? null : text;
            }
        }
        return null;
    }

    private static List<Block> toBlocks(Node document, String sectionId) {
        List<Block> blocks = new ArrayList<>();
        int[] counter = {0};
        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            appendBlock(node, sectionId, counter, blocks);
        }
        return blocks;
    }

    private static void appendBlock(Node node, String sectionId, int[] counter, List<Block> blocks) {
        if (node instanceof Heading heading) {
            TextWithMarks t = extractText(heading);
            blocks.add(new HeadingBlock(nextId(sectionId, counter), heading.getLevel(), t.text(), t.marks()));
        } else if (node instanceof Paragraph paragraph) {
            TextWithMarks t = extractText(paragraph);
            if (!t.text().isBlank() || !t.inlineObjects().isEmpty()) {
                blocks.add(new ParagraphBlock(nextId(sectionId, counter), t.text(), t.marks(), t.inlineObjects()));
            }
        } else if (node instanceof BlockQuote blockQuote) {
            blocks.add(blockquoteBlock(blockQuote, sectionId, counter));
        } else if (node instanceof ThematicBreak) {
            blocks.add(new DividerBlock(nextId(sectionId, counter)));
        } else if (node instanceof BulletList list) {
            appendListItems(list, false, sectionId, counter, blocks);
        } else if (node instanceof OrderedList list) {
            appendListItems(list, true, sectionId, counter, blocks);
        } else if (node instanceof FencedCodeBlock fenced) {
            blocks.add(new ParagraphBlock(nextId(sectionId, counter), fenced.getLiteral(), List.of(), List.of()));
        } else if (node instanceof IndentedCodeBlock indented) {
            blocks.add(new ParagraphBlock(nextId(sectionId, counter), indented.getLiteral(), List.of(), List.of()));
        }
        // Other node kinds (raw HTML blocks, etc.) are rare in book-length markdown and skipped.
    }

    private static BlockquoteBlock blockquoteBlock(BlockQuote blockQuote, String sectionId, int[] counter) {
        StringBuilder combined = new StringBuilder();
        List<Mark> marks = new ArrayList<>();
        for (Node child = blockQuote.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Paragraph) {
                TextWithMarks t = extractText(child);
                int offset = combined.length();
                for (Mark m : t.marks()) {
                    marks.add(new Mark(m.type(), m.start() + offset, m.end() + offset));
                }
                if (!combined.isEmpty()) {
                    combined.append('\n');
                }
                combined.append(t.text());
            }
        }
        return new BlockquoteBlock(nextId(sectionId, counter), combined.toString(), marks);
    }

    private static void appendListItems(
            Node list, boolean ordered, String sectionId, int[] counter, List<Block> blocks) {
        for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
            if (item instanceof ListItem) {
                TextWithMarks t = extractListItemText(item);
                blocks.add(new ListItemBlock(nextId(sectionId, counter), 0, ordered, t.text(), t.marks()));
            }
        }
    }

    private static TextWithMarks extractListItemText(Node listItem) {
        Node first = listItem.getFirstChild();
        return first instanceof Paragraph ? extractText(first) : extractText(listItem);
    }

    private static String nextId(String sectionId, int[] counter) {
        return sectionId + "-b" + (counter[0]++);
    }

    private record TextWithMarks(String text, List<Mark> marks, List<InlineObject> inlineObjects) {}

    private static TextWithMarks extractText(Node inlineParent) {
        StringBuilder text = new StringBuilder();
        List<Mark> marks = new ArrayList<>();
        List<InlineObject> inline = new ArrayList<>();
        appendInline(inlineParent.getFirstChild(), text, marks, inline);
        return new TextWithMarks(text.toString(), marks, inline);
    }

    private static void appendInline(Node node, StringBuilder text, List<Mark> marks, List<InlineObject> inline) {
        for (Node n = node; n != null; n = n.getNext()) {
            if (n instanceof Text t) {
                text.append(t.getLiteral());
            } else if (n instanceof SoftLineBreak) {
                text.append(' ');
            } else if (n instanceof HardLineBreak) {
                text.append('\n');
            } else if (n instanceof Code c) {
                text.append(c.getLiteral());
            } else if (n instanceof Image image) {
                inline.add(new InlineObject(InlineObjectType.IMAGE, text.length(), null, image.getTitle()));
            } else if (n instanceof Emphasis) {
                int start = text.length();
                appendInline(n.getFirstChild(), text, marks, inline);
                if (text.length() > start) {
                    marks.add(new Mark(MarkType.ITALIC, start, text.length()));
                }
            } else if (n instanceof StrongEmphasis) {
                int start = text.length();
                appendInline(n.getFirstChild(), text, marks, inline);
                if (text.length() > start) {
                    marks.add(new Mark(MarkType.BOLD, start, text.length()));
                }
            } else if (n instanceof Link) {
                appendInline(n.getFirstChild(), text, marks, inline);
            } else if (n.getFirstChild() != null) {
                appendInline(n.getFirstChild(), text, marks, inline);
            }
        }
    }
}
