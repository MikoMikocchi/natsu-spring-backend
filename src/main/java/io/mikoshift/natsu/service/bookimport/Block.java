package io.mikoshift.natsu.service.bookimport;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** One unit of section content. Every variant carries a section-scoped, stable {@code id}. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ParagraphBlock.class, name = "paragraph"),
    @JsonSubTypes.Type(value = HeadingBlock.class, name = "heading"),
    @JsonSubTypes.Type(value = ImageBlock.class, name = "image"),
    @JsonSubTypes.Type(value = BlockquoteBlock.class, name = "blockquote"),
    @JsonSubTypes.Type(value = ListItemBlock.class, name = "list_item"),
    @JsonSubTypes.Type(value = DividerBlock.class, name = "divider")
})
public sealed interface Block
        permits ParagraphBlock, HeadingBlock, ImageBlock, BlockquoteBlock, ListItemBlock, DividerBlock {

    String id();
}
