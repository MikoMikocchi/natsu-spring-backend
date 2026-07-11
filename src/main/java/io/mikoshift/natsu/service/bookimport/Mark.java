package io.mikoshift.natsu.service.bookimport;

/** Inline text formatting applied to the [start, end) character range of a block's plain text. */
public record Mark(MarkType type, int start, int end) {

    public enum MarkType {
        BOLD,
        ITALIC
    }
}
