package io.mikoshift.natsu.service.bookimport;

import io.mikoshift.natsu.entity.Document.SourceFormat;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PlainTextImporter implements BookImporter {

    @Override
    public SourceFormat supportedFormat() {
        return SourceFormat.PLAIN_TEXT;
    }

    @Override
    public ImportedBook importFrom(byte[] sourceBytes) {
        String text = new String(sourceBytes, StandardCharsets.UTF_8).strip();
        if (text.isEmpty()) {
            throw new ImportException("Plain text file is empty");
        }

        List<Block> blocks = new ArrayList<>();
        int index = 0;
        for (String paragraph : text.split("\\r?\\n\\s*\\r?\\n")) {
            String trimmed = paragraph.strip();
            if (!trimmed.isEmpty()) {
                blocks.add(new ParagraphBlock("section-0-b" + index, trimmed, List.of(), List.of()));
                index++;
            }
        }
        ImportedSection section = new ImportedSection("section-0", null, blocks);

        return ImportedBook.of(null, List.of(section));
    }
}
