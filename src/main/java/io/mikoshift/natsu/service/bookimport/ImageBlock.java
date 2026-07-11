package io.mikoshift.natsu.service.bookimport;

/** A block-level image; {@code assetId} is the sha256 of the asset content. */
public record ImageBlock(String id, String assetId, String alt) implements Block {}
