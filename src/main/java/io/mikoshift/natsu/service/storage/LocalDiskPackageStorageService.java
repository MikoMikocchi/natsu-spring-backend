package io.mikoshift.natsu.service.storage;

import io.mikoshift.natsu.config.NatsuProperties;
import io.mikoshift.natsu.util.HashUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocalDiskPackageStorageService implements PackageStorageService {

    private final NatsuProperties properties;

    @Override
    public StoredPackage store(UUID documentId, byte[] content) {
        try {
            Path path = packagePath(documentId);
            Files.createDirectories(path.getParent());
            Files.write(path, content);
            return new StoredPackage(content.length, HashUtils.sha256Hex(content));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store package for document " + documentId, e);
        }
    }

    @Override
    public Resource load(UUID documentId) {
        return new FileSystemResource(packagePath(documentId));
    }

    @Override
    public boolean exists(UUID documentId) {
        return Files.exists(packagePath(documentId));
    }

    @Override
    public void delete(UUID documentId) {
        try {
            Files.deleteIfExists(packagePath(documentId));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete package for document " + documentId, e);
        }
    }

    private Path packagePath(UUID documentId) {
        return Path.of(properties.storageRoot(), "packages", documentId + ".zip");
    }
}
