package io.mikoshift.natsu.service.bookimport;

import java.util.List;

public record BlockquoteBlock(String id, String text, List<Mark> marks) implements Block {}
