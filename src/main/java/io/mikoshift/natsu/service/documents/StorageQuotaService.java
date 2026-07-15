package io.mikoshift.natsu.service.documents;

import io.mikoshift.natsu.config.NatsuProperties;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.exception.NotFoundException;
import io.mikoshift.natsu.exception.QuotaExceededException;
import io.mikoshift.natsu.exception.ValidationException;
import io.mikoshift.natsu.repository.DocumentRepository;
import io.mikoshift.natsu.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StorageQuotaService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final NatsuProperties properties;

    public void checkUploadSize(long sizeBytes) {
        if (sizeBytes > properties.maxPackageBytes()) {
            throw ValidationException.of(
                    "file", "is too large (max " + (properties.maxPackageBytes() / (1024 * 1024)) + " MB)");
        }
    }

    /**
     * Excludes {@code currentSizeOfReplacedDocument}, since that document is about to be replaced by
     * {@code newSizeBytes}. Must run inside the caller's write transaction so the {@code SELECT ...
     * FOR UPDATE} on the user row is held until the document size change commits.
     */
    public void checkUserQuota(User user, long newSizeBytes, long currentSizeOfReplacedDocument) {
        userRepository.findByIdForUpdate(user.getId()).orElseThrow(() -> new NotFoundException("User not found"));
        long used = documentRepository.sumPackageSizeBytesByUser(user) - currentSizeOfReplacedDocument;
        if (used + newSizeBytes > properties.maxStorageBytesPerUser()) {
            throw new QuotaExceededException("Storage quota exceeded (max "
                    + (properties.maxStorageBytesPerUser() / (1024 * 1024))
                    + " MB per account)");
        }
    }
}
