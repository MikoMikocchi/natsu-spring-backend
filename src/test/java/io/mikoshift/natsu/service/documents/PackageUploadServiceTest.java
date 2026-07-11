package io.mikoshift.natsu.service.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.mikoshift.natsu.entity.Document;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.exception.NotFoundException;
import io.mikoshift.natsu.exception.QuotaExceededException;
import io.mikoshift.natsu.exception.ValidationException;
import io.mikoshift.natsu.repository.DocumentRepository;
import io.mikoshift.natsu.service.storage.PackageStorageService;
import io.mikoshift.natsu.service.storage.StoredPackage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class PackageUploadServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private StorageQuotaService storageQuotaService;

    @Mock
    private PackageStorageService packageStorageService;

    private PackageUploadService uploadService;
    private User user;
    private UUID documentId;
    private Document document;

    @BeforeEach
    void setUp() {
        uploadService = new PackageUploadService(documentRepository, storageQuotaService, packageStorageService);
        user = new User();
        user.setId(1L);
        documentId = UUID.randomUUID();
        document = new Document();
        document.setId(documentId);
        document.setUser(user);
        document.setPackageSizeBytes(100L);
    }

    @Test
    void uploadsAValidZipAndUpdatesDocumentMetadata() throws IOException {
        byte[] zip = zipWithEntries("manifest.json");
        MockMultipartFile file = new MockMultipartFile("package", "book.zip", "application/zip", zip);
        when(documentRepository.findByIdAndUser(documentId, user)).thenReturn(Optional.of(document));
        when(packageStorageService.store(eq(documentId), any(byte[].class)))
                .thenReturn(new StoredPackage(zip.length, "deadbeef"));
        when(documentRepository.save(document)).thenReturn(document);

        Document result = uploadService.upload(user, documentId, file);

        assertThat(result.getPackageSizeBytes()).isEqualTo(zip.length);
        assertThat(result.getPackageSha256()).isEqualTo("deadbeef");
        assertThat(result.getPackageUpdatedAtMs()).isGreaterThan(0);
        assertThat(result.getUpdatedAtMs()).isEqualTo(result.getPackageUpdatedAtMs());
    }

    @Test
    void rejectsAnEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile("package", "book.zip", "application/zip", new byte[0]);
        when(documentRepository.findByIdAndUser(documentId, user)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> uploadService.upload(user, documentId, emptyFile))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsAZipThatIsMissingTheManifest() throws IOException {
        byte[] zip = zipWithEntries("content.txt");
        MockMultipartFile file = new MockMultipartFile("package", "book.zip", "application/zip", zip);
        when(documentRepository.findByIdAndUser(documentId, user)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> uploadService.upload(user, documentId, file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("manifest.json");
    }

    @Test
    void rejectsContentThatIsNotAValidZipArchive() {
        byte[] garbage = new byte[] {1, 2, 3, 4, 5};
        MockMultipartFile file = new MockMultipartFile("package", "book.zip", "application/zip", garbage);
        when(documentRepository.findByIdAndUser(documentId, user)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> uploadService.upload(user, documentId, file)).isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsUploadWhenDocumentDoesNotBelongToTheUser() {
        MockMultipartFile file = new MockMultipartFile("package", "book.zip", "application/zip", new byte[] {1});
        when(documentRepository.findByIdAndUser(documentId, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> uploadService.upload(user, documentId, file)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void propagatesQuotaExceededFromTheQuotaService() throws IOException {
        byte[] zip = zipWithEntries("manifest.json");
        MockMultipartFile file = new MockMultipartFile("package", "book.zip", "application/zip", zip);
        when(documentRepository.findByIdAndUser(documentId, user)).thenReturn(Optional.of(document));
        doThrow(new QuotaExceededException("Storage quota exceeded"))
                .when(storageQuotaService)
                .checkUserQuota(user, zip.length, document.getPackageSizeBytes());

        assertThatThrownBy(() -> uploadService.upload(user, documentId, file))
                .isInstanceOf(QuotaExceededException.class);
    }

    private static byte[] zipWithEntries(String... entryNames) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String entryName : entryNames) {
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write("content".getBytes());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
