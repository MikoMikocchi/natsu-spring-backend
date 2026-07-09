package io.mikoshift.natsu.backend.controller.v1;

import io.mikoshift.natsu.backend.dto.request.DocumentSyncRequest;
import io.mikoshift.natsu.backend.dto.response.DocumentIndexResponse;
import io.mikoshift.natsu.backend.dto.response.DocumentResponse;
import io.mikoshift.natsu.backend.dto.response.DocumentSearchResponse;
import io.mikoshift.natsu.backend.dto.response.DocumentShowResponse;
import io.mikoshift.natsu.backend.entity.Document;
import io.mikoshift.natsu.backend.entity.User;
import io.mikoshift.natsu.backend.service.ServerTimeService;
import io.mikoshift.natsu.backend.service.documents.DocumentQueryService;
import io.mikoshift.natsu.backend.service.documents.DocumentSyncService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/documents")
@Validated
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentQueryService documentQueryService;
    private final DocumentSyncService documentSyncService;
    private final ServerTimeService serverTimeService;

    @GetMapping
    DocumentIndexResponse index(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") long since,
            @RequestParam(required = false) Integer limit) {
        return toIndexResponse(documentQueryService.listSince(user, since, limit));
    }

    @GetMapping("/search")
    DocumentSearchResponse search(@AuthenticationPrincipal User user, @RequestParam @NotBlank String q) {
        return new DocumentSearchResponse(documentQueryService.search(user, q), serverTimeService.nowMs());
    }

    @GetMapping("/{id}")
    DocumentShowResponse show(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        Document document = documentQueryService.get(user, id);
        return new DocumentShowResponse(DocumentResponse.from(document), serverTimeService.nowMs());
    }

    @PostMapping("/sync")
    DocumentIndexResponse sync(@AuthenticationPrincipal User user, @Valid @RequestBody DocumentSyncRequest request) {
        return toIndexResponse(documentSyncService.sync(user, request));
    }

    private DocumentIndexResponse toIndexResponse(List<Document> documents) {
        return new DocumentIndexResponse(
                documents.stream().map(DocumentResponse::from).toList(), serverTimeService.nowMs());
    }
}
