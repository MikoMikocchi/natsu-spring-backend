package io.mikoshift.natsu.service.bookimport;

import java.util.List;

public record ListItemBlock(String id, int level, boolean ordered, String text, List<Mark> marks) implements Block {}
