package io.mikoshift.natsu.backend.service.bookimport;

import io.mikoshift.natsu.backend.entity.Document;
import io.mikoshift.natsu.backend.repository.DocumentRepository;
import io.mikoshift.natsu.backend.service.storage.StoredPackage;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kept as a separate bean (rather than protected methods on the orchestrator) so each read/write
 * gets a short, real transaction via the normal Spring proxy -- calling a {@code @Transactional}
 * method from another method on the *same* bean bypasses the proxy and silently runs without a
 * transaction at all.
 */
@Component
@RequiredArgsConstructor
class BookImportPersistence {

    private final DocumentRepository documentRepository;

    @Transactional(readOnly = true)
    Document findPending(UUID documentId) {
        Document document = documentRepository.findById(documentId).orElse(null);
        return document != null && document.getStatus() == Document.Status.PENDING ? document : null;
    }

    @Transactional
    void applySuccess(UUID documentId, String title, int charCount, String searchText, StoredPackage stored) {
        documentRepository.findById(documentId).ifPresent(document -> {
            long nowMs = Instant.now().toEpochMilli();
            document.setTitle(title);
            document.setCharCount(charCount);
            document.setSearchText(searchText);
            document.setPackageSizeBytes(stored.sizeBytes());
            document.setPackageSha256(stored.sha256());
            document.setPackageUpdatedAtMs(nowMs);
            document.setUpdatedAtMs(nowMs);
            document.setStatus(Document.Status.READY);
            document.setImportError(null);
        });
    }

    @Transactional
    void markFailed(UUID documentId, String message) {
        documentRepository.findById(documentId).ifPresent(document -> {
            document.setStatus(Document.Status.FAILED);
            document.setImportError(message);
            document.setUpdatedAtMs(Instant.now().toEpochMilli());
        });
    }
}
