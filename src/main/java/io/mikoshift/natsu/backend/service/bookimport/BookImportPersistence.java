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

    /**
     * Re-checks (inside its own short transaction, right before acting) that the document is still
     * PENDING, bumps its recovery-attempt counter, and marks it FAILED -- all as a single atomic
     * unit. Doing the PENDING re-check and the write in one transaction (rather than splitting the
     * decision and the write across two calls) keeps a concurrent completion (the same instance's
     * executor finishing the real import a moment later) from being clobbered by a recovery pass that
     * read stale data, and avoids the self-invocation trap of calling a {@code @Transactional} method
     * from another method on this same bean.
     *
     * <p>Every recovery pass here is terminal -- there is no way to actually resume an import once
     * the original upload bytes are gone (see {@code StaleImportRecoveryService} for why) -- so
     * {@code maxAttempts} doesn't gate a retry-vs-give-up choice the way it would for a real retry
     * loop. It's still tracked and reported back so the caller can log distinctly once a document has
     * been (re)flagged more times than expected, which would itself be a signal something is wrong
     * (e.g. this method being called repeatedly for a document that should have already left
     * PENDING).
     *
     * @return the resulting {@link RecoveryOutcome}
     */
    @Transactional
    RecoveryOutcome recoverStaleDocument(UUID documentId, int maxAttempts) {
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null || document.getStatus() != Document.Status.PENDING) {
            return RecoveryOutcome.SKIPPED_NOT_PENDING;
        }

        int attempts = document.getImportAttempts() + 1;
        document.setImportAttempts(attempts);
        document.setStatus(Document.Status.FAILED);
        document.setImportError("Import was interrupted and could not be resumed; please re-upload the file.");
        document.setUpdatedAtMs(Instant.now().toEpochMilli());

        return attempts > maxAttempts ? RecoveryOutcome.FAILED_ATTEMPTS_EXCEEDED : RecoveryOutcome.FAILED_STALE;
    }

    enum RecoveryOutcome {
        /**
         * Document was no longer PENDING by the time recovery tried to act on it -- nothing was done.
         */
        SKIPPED_NOT_PENDING,
        /** Document was stale and has now been marked FAILED, within the normal attempt cap. */
        FAILED_STALE,
        /** Document was stale and marked FAILED, but this was past the configured attempt cap. */
        FAILED_ATTEMPTS_EXCEEDED
    }
}
