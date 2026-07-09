package io.mikoshift.natsu.backend.service.bookimport;

import io.mikoshift.natsu.backend.entity.Document.SourceFormat;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

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

        StringBuilder html = new StringBuilder();
        for (String paragraph : text.split("\\r?\\n\\s*\\r?\\n")) {
            String trimmed = paragraph.strip();
            if (!trimmed.isEmpty()) {
                html.append("<p>").append(HtmlUtils.htmlEscape(trimmed).replace("\n", "<br/>")).append("</p>\n");
            }
        }

        return new ImportedBook(null, List.of(new ImportedSection("section-0", null, html.toString())));
    }
}
