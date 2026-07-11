package io.mikoshift.natsu.backend.service.bookimport;

import io.mikoshift.natsu.backend.entity.Document;
import io.mikoshift.natsu.backend.service.storage.PackageStorageService;
import io.mikoshift.natsu.backend.service.storage.StoredPackage;
import java.io.UncheckedIOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs off the request thread on {@code bookImportExecutor}. A malformed source file ({@link
 * ImportException}) is a permanent failure handled inline, with no retry. A storage I/O hiccup
 * ({@link TransientImportException}) is retried up to 3 times with exponential backoff; once
 * retries are exhausted, {@link #recover} marks the document failed the same way a permanent
 * failure would. The actual file parsing/zip-building work runs outside any DB transaction -- only
 * the short read-before and write-after steps (in {@link BookImportPersistence}) are transactional,
 * so a slow import never holds a pooled connection open.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookImportOrchestrator {

    private final BookImportPersistence persistence;
    private final BookImporterRegistry importerRegistry;
    private final PackageBuilder packageBuilder;
    private final PackageStorageService packageStorageService;

    @Async("bookImportExecutor")
    @Retryable(
            retryFor = TransientImportException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    public void importAsync(UUID documentId, byte[] sourceBytes, String fallbackTitle) {
        Document document = persistence.findPending(documentId);
        if (document == null) {
            return;
        }

        try {
            BookImporter importer = importerRegistry.forFormat(document.getSourceFormat());
            ImportedBook book = importer.importFrom(sourceBytes);
            String title = book.title() != null && !book.title().isBlank() ? book.title() : fallbackTitle;
            String plainText = packageBuilder.extractPlainText(book.sections());
            byte[] packageZip = packageBuilder.buildZip(title, document.getSourceFormat(), book.sections());

            StoredPackage stored = store(documentId, packageZip);
            persistence.applySuccess(documentId, title, plainText.length(), plainText, stored);
        } catch (ImportException e) {
            log.warn("Import of document {} failed permanently: {}", documentId, e.getMessage());
            persistence.markFailed(documentId, e.getMessage());
        }
    }

    @Recover
    public void recover(TransientImportException e, UUID documentId, byte[] sourceBytes, String fallbackTitle) {
        log.error("Import of document {} failed after retries", documentId, e);
        persistence.markFailed(documentId, "Import failed after repeated storage errors");
    }

    private StoredPackage store(UUID documentId, byte[] packageZip) {
        try {
            return packageStorageService.store(documentId, packageZip);
        } catch (UncheckedIOException e) {
            throw new TransientImportException("Failed to write package for document " + documentId, e);
        }
    }
}
