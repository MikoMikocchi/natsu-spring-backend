package io.mikoshift.natsu.service.bookimport;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.mikoshift.natsu.repository.DocumentRepository;
import io.mikoshift.natsu.repository.DocumentSearchTextRepository;
import io.mikoshift.natsu.service.documents.StorageQuotaService;
import io.mikoshift.natsu.service.storage.PackageStorageService;
import io.mikoshift.natsu.service.storage.StoredPackage;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookImportPersistenceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentSearchTextRepository documentSearchTextRepository;

    @Mock
    private StorageQuotaService storageQuotaService;

    @Mock
    private PackageStorageService packageStorageService;

    private BookImportPersistence persistence;

    @BeforeEach
    void setUp() {
        persistence = new BookImportPersistence(
                documentRepository, documentSearchTextRepository, storageQuotaService, packageStorageService);
    }

    @Test
    void applySuccessDeletesPackageWhenDocumentWasRemovedDuringImport() {
        UUID documentId = UUID.randomUUID();
        StoredPackage stored = new StoredPackage(42, "sha");

        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        persistence.applySuccess(documentId, "Title", 5, "text", stored);

        verify(packageStorageService).delete(documentId);
        verifyNoInteractions(documentSearchTextRepository);
    }
}
