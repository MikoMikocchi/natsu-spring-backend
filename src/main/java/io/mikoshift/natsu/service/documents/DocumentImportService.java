package io.mikoshift.natsu.service.documents;

import io.mikoshift.natsu.entity.Document;
import io.mikoshift.natsu.entity.Document.SourceFormat;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.exception.ValidationException;
import io.mikoshift.natsu.repository.DocumentRepository;
import io.mikoshift.natsu.service.bookimport.BookImportOrchestrator;
import io.mikoshift.natsu.service.bookimport.FormatDetector;
import io.mikoshift.natsu.service.bookimport.ImportException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DocumentImportService {

    private final DocumentRepository documentRepository;
    private final FormatDetector formatDetector;
    private final StorageQuotaService storageQuotaService;
    private final BookImportOrchestrator orchestrator;

    /**
     * Creates the pending document and hands it to the async orchestrator. Deliberately NOT
     * {@code @Transactional}: {@code documentRepository.save} commits on its own, and the async
     * import must only start once that commit is visible -- kicking off the async call from inside a
     * still-open transaction here would race it against a connection that hasn't committed yet.
     */
    public Document startImport(User user, MultipartFile file) {
        if (file.isEmpty()) {
            throw ValidationException.of("file", "must not be empty");
        }
        storageQuotaService.checkUploadSize(file.getSize());

        SourceFormat format;
        try {
            format = formatDetector.detect(file.getOriginalFilename());
        } catch (ImportException e) {
            // Detection happens synchronously against the request itself (unlike parsing, which
            // runs async), so a bad filename should surface as a normal validation error here
            // rather than reaching the client as an opaque 500.
            throw ValidationException.of("file", e.getMessage());
        }
        String fallbackTitle = formatDetector.titleFromFilename(file.getOriginalFilename());

        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setUser(user);
        document.setTitle(fallbackTitle);
        document.setSourceFormat(format);
        document.setStatus(Document.Status.PENDING);
        long now = Instant.now().toEpochMilli();
        document.setImportedAt(now);
        document.setUpdatedAtMs(now);
        document = documentRepository.save(document);

        orchestrator.importAsync(document.getId(), readBytes(file), fallbackTitle);
        return document;
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
