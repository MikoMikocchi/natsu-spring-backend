package io.mikoshift.natsu.service.bookimport;

import java.util.List;

public record HeadingBlock(String id, int level, String text, List<Mark> marks) implements Block {}
