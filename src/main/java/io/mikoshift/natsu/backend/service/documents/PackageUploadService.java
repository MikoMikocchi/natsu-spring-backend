package io.mikoshift.natsu.backend.service.documents;

import io.mikoshift.natsu.backend.entity.Document;
import io.mikoshift.natsu.backend.entity.User;
import io.mikoshift.natsu.backend.exception.NotFoundException;
import io.mikoshift.natsu.backend.exception.ValidationException;
import io.mikoshift.natsu.backend.repository.DocumentRepository;
import io.mikoshift.natsu.backend.service.storage.PackageStorageService;
import io.mikoshift.natsu.backend.service.storage.StoredPackage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Handles direct package uploads (a client that already built its own package zip, as opposed to server-side import). */
@Service
@RequiredArgsConstructor
public class PackageUploadService {

    private final DocumentRepository documentRepository;
    private final StorageQuotaService storageQuotaService;
    private final PackageStorageService packageStorageService;

    @Transactional
    public Document upload(User user, UUID documentId, MultipartFile file) {
        Document document = documentRepository
                .findByIdAndUser(documentId, user)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        if (file.isEmpty()) {
            throw ValidationException.of("package", "must not be empty");
        }
        storageQuotaService.checkUploadSize(file.getSize());
        storageQuotaService.checkUserQuota(user, file.getSize(), document.getPackageSizeBytes());

        byte[] content = readBytes(file);
        validateZipHasManifest(content);

        StoredPackage stored = packageStorageService.store(documentId, content);
        long nowMs = Instant.now().toEpochMilli();
        document.setPackageSizeBytes(stored.sizeBytes());
        document.setPackageSha256(stored.sha256());
        document.setPackageUpdatedAtMs(nowMs);
        document.setUpdatedAtMs(nowMs);
        return documentRepository.save(document);
    }

    private static void validateZipHasManifest(byte[] content) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("manifest.json")) {
                    return;
                }
            }
        } catch (IOException e) {
            throw ValidationException.of("package", "is not a valid zip archive");
        }
        throw ValidationException.of("package", "is missing manifest.json");
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
