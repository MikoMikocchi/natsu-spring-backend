package io.mikoshift.natsu.service.bookimport;

/**
 * A non-text element anchored at a character offset inside a block's plain text -- e.g. an inline
 * image or footnote reference embedded mid-paragraph -- as opposed to {@link ImageBlock}, which is
 * a block-level image occupying a section on its own.
 */
public record InlineObject(InlineObjectType type, int offset, String assetId, String footnoteText) {

    public enum InlineObjectType {
        IMAGE,
        FOOTNOTE
    }
}
