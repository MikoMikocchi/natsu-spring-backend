package io.mikoshift.natsu.service.documents;

import io.mikoshift.natsu.config.NatsuProperties;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.exception.QuotaExceededException;
import io.mikoshift.natsu.exception.ValidationException;
import io.mikoshift.natsu.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StorageQuotaService {

    private final DocumentRepository documentRepository;
    private final NatsuProperties properties;

    public void checkUploadSize(long sizeBytes) {
        if (sizeBytes > properties.maxPackageBytes()) {
            throw ValidationException.of(
                    "file", "is too large (max " + (properties.maxPackageBytes() / (1024 * 1024)) + " MB)");
        }
    }

    /**
     * Excludes {@code excludingDocumentId}'s current size, since it's about to be replaced by {@code
     * newSizeBytes}.
     */
    @Transactional(readOnly = true)
    public void checkUserQuota(User user, long newSizeBytes, long currentSizeOfReplacedDocument) {
        long used = documentRepository.sumPackageSizeBytesByUser(user) - currentSizeOfReplacedDocument;
        if (used + newSizeBytes > properties.maxStorageBytesPerUser()) {
            throw new QuotaExceededException("Storage quota exceeded (max "
                    + (properties.maxStorageBytesPerUser() / (1024 * 1024))
                    + " MB per account)");
        }
    }
}
