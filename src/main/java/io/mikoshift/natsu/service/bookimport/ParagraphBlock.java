package io.mikoshift.natsu.service.bookimport;

import java.util.List;

public record ParagraphBlock(String id, String text, List<Mark> marks, List<InlineObject> inlineObjects)
        implements Block {}
