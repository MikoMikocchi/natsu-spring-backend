package io.mikoshift.natsu.backend.service.storage;

import io.mikoshift.natsu.backend.config.NatsuProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
            return new StoredPackage(content.length, sha256Hex(content));
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

    private static String sha256Hex(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
