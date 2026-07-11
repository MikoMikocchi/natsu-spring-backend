package io.mikoshift.natsu.backend.service.documents;

import io.mikoshift.natsu.backend.dto.request.DocumentSyncItemRequest;
import io.mikoshift.natsu.backend.dto.request.DocumentSyncRequest;
import io.mikoshift.natsu.backend.entity.Document;
import io.mikoshift.natsu.backend.entity.User;
import io.mikoshift.natsu.backend.repository.DocumentRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentSyncService {

    private final DocumentRepository documentRepository;

    /**
     * Batch-upserts up to 100 documents. Each row is merged independently: a row whose updatedAtMs is
     * older than what's stored is a no-op (the stored state is returned as-is), exactly like the
     * single-row settings merge, just applied per item in one request. A row that doesn't exist yet
     * for this user is always created (there is no prior state to compare against) -- this is how a
     * device pushes a locally-created document for the first time.
     */
    @Transactional
    public List<Document> sync(User user, DocumentSyncRequest request) {
        return request.documents().stream().map(item -> applyItem(user, item)).toList();
    }

    private Document applyItem(User user, DocumentSyncItemRequest item) {
        Document document = documentRepository.findByIdAndUser(item.id(), user).orElse(null);
        if (document == null) {
            document = new Document();
            document.setId(item.id());
            document.setUser(user);
        } else if (item.updatedAtMs() < document.getUpdatedAtMs()) {
            return document;
        }

        document.setTitle(item.title() != null ? item.title() : "");
        document.setSourceFormat(item.sourceFormat());
        document.setImportedAt(item.importedAt());
        document.setCharCount(item.charCount());
        document.setLastReadCharOffset(item.lastReadCharOffset());
        document.setLastReadSectionId(item.lastReadSectionId());
        document.setLastReadBlockIndex(item.lastReadBlockIndex());
        document.setLastReadBlockCharOffset(item.lastReadBlockCharOffset());
        document.setUpdatedAtMs(item.updatedAtMs());
        document.setDeletedAt(Boolean.TRUE.equals(item.deleted()) ? Instant.now() : null);
        return documentRepository.save(document);
    }
}
