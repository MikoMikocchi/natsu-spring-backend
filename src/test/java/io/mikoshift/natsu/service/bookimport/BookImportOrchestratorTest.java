package io.mikoshift.natsu.service.bookimport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mikoshift.natsu.entity.Document;
import io.mikoshift.natsu.service.storage.PackageStorageService;
import io.mikoshift.natsu.service.storage.StoredPackage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookImportOrchestratorTest {

    @Mock
    private BookImportPersistence persistence;

    @Mock
    private BookImporterRegistry importerRegistry;

    @Mock
    private PackageBuilder packageBuilder;

    @Mock
    private PackageStorageService packageStorageService;

    @Mock
    private BookImporter importer;

    private BookImportOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new BookImportOrchestrator(persistence, importerRegistry, packageBuilder, packageStorageService);
    }

    @Test
    void unexpectedFailureAfterStoreMarksFailedAndDeletesPackage() {
        UUID documentId = UUID.randomUUID();
        Document document = pendingDocument(documentId);
        StoredPackage stored = new StoredPackage(1, "abc");

        when(persistence.findPending(documentId)).thenReturn(document);
        when(importerRegistry.forFormat(Document.SourceFormat.PLAIN_TEXT)).thenReturn(importer);
        when(importer.importFrom(any())).thenReturn(ImportedBook.of("Title", List.of()));
        when(packageBuilder.extractPlainText(any())).thenReturn("text");
        when(packageBuilder.buildZip(any(), any(), any())).thenReturn(new byte[] {1});
        when(packageStorageService.store(documentId, new byte[] {1})).thenReturn(stored);
        doThrow(new RuntimeException("DB blew up"))
                .when(persistence)
                .applySuccess(eq(documentId), eq("Title"), eq(4), eq("text"), eq(stored));

        orchestrator.importAsync(documentId, new byte[] {1}, "fallback");

        verify(packageStorageService).delete(documentId);
        verify(persistence).markFailed(documentId, "Import failed unexpectedly; please try uploading again.");
    }

    @Test
    void unexpectedFailureBeforeStoreDoesNotDeletePackage() {
        UUID documentId = UUID.randomUUID();
        Document document = pendingDocument(documentId);

        when(persistence.findPending(documentId)).thenReturn(document);
        when(importerRegistry.forFormat(Document.SourceFormat.PLAIN_TEXT)).thenReturn(importer);
        when(importer.importFrom(any())).thenThrow(new RuntimeException("Parser blew up"));

        orchestrator.importAsync(documentId, new byte[] {1}, "fallback");

        verify(packageStorageService, never()).delete(documentId);
        verify(persistence).markFailed(documentId, "Import failed unexpectedly; please try uploading again.");
    }

    private static Document pendingDocument(UUID documentId) {
        Document document = new Document();
        document.setId(documentId);
        document.setSourceFormat(Document.SourceFormat.PLAIN_TEXT);
        document.setStatus(Document.Status.PENDING);
        return document;
    }
}
