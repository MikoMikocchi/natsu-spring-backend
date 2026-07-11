package io.mikoshift.natsu.backend.service.storage;

import java.util.UUID;
import org.springframework.core.io.Resource;

/**
 * Storage is abstracted behind this interface so a second implementation (e.g. S3-backed) could be
 * swapped in later via a {@code @ConditionalOnProperty} without touching callers; only a local disk
 * implementation exists for now.
 */
public interface PackageStorageService {

    StoredPackage store(UUID documentId, byte[] content);

    Resource load(UUID documentId);

    boolean exists(UUID documentId);

    void delete(UUID documentId);
}
