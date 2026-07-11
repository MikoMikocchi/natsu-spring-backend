package io.mikoshift.natsu.service.bookimport;

/** A binary asset (image) referenced by blocks/cover, content-addressed by the sha256 of its bytes. */
public record ImportedAsset(String sha256, String contentType, byte[] content) {}
