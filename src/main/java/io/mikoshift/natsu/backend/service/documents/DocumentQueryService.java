package io.mikoshift.natsu.backend.service.documents;

import io.mikoshift.natsu.backend.dto.response.DocumentSearchMatch;
import io.mikoshift.natsu.backend.dto.response.DocumentSearchResult;
import io.mikoshift.natsu.backend.entity.Document;
import io.mikoshift.natsu.backend.entity.User;
import io.mikoshift.natsu.backend.exception.NotFoundException;
import io.mikoshift.natsu.backend.repository.DocumentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentQueryService {

    private static final int SNIPPET_CONTEXT_CHARS = 40;
    private static final int MAX_MATCHES_PER_DOCUMENT = 5;

    private final DocumentRepository documentRepository;

    @Transactional(readOnly = true)
    public List<Document> listSince(User user, long since, Integer limit) {
        List<Document> documents =
                documentRepository.findByUserAndUpdatedAtMsGreaterThanOrderByUpdatedAtMsAsc(user, since);
        return limit != null && limit < documents.size() ? documents.subList(0, limit) : documents;
    }

    @Transactional(readOnly = true)
    public Document get(User user, UUID id) {
        return documentRepository
                .findByIdAndUser(id, user)
                .orElseThrow(() -> new NotFoundException("Document not found"));
    }

    @Transactional(readOnly = true)
    public List<DocumentSearchResult> search(User user, String query) {
        return documentRepository.searchByUserAndQuery(user, query).stream()
                .map(document -> buildSearchResult(document, query))
                .toList();
    }

    private DocumentSearchResult buildSearchResult(Document document, String query) {
        String haystack =
                document.getTitle() + "\n" + (document.getSearchText() != null ? document.getSearchText() : "");
        String lowerHaystack = haystack.toLowerCase();
        String lowerQuery = query.toLowerCase();

        List<DocumentSearchMatch> matches = new ArrayList<>();
        int from = 0;
        while (matches.size() < MAX_MATCHES_PER_DOCUMENT) {
            int index = lowerHaystack.indexOf(lowerQuery, from);
            if (index < 0) {
                break;
            }
            int start = Math.max(0, index - SNIPPET_CONTEXT_CHARS);
            int end = Math.min(haystack.length(), index + lowerQuery.length() + SNIPPET_CONTEXT_CHARS);
            matches.add(new DocumentSearchMatch(index, haystack.substring(start, end)));
            from = index + lowerQuery.length();
        }
        return new DocumentSearchResult(document.getId(), document.getTitle(), matches);
    }
}
