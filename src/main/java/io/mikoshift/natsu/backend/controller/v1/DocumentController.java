package io.mikoshift.natsu.backend.controller.v1;

import io.mikoshift.natsu.backend.dto.request.DocumentSyncRequest;
import io.mikoshift.natsu.backend.dto.response.DocumentIndexResponse;
import io.mikoshift.natsu.backend.dto.response.DocumentResponse;
import io.mikoshift.natsu.backend.dto.response.DocumentSearchResponse;
import io.mikoshift.natsu.backend.dto.response.DocumentShowResponse;
import io.mikoshift.natsu.backend.entity.Document;
import io.mikoshift.natsu.backend.entity.User;
import io.mikoshift.natsu.backend.exception.NotFoundException;
import io.mikoshift.natsu.backend.service.ServerTimeService;
import io.mikoshift.natsu.backend.service.documents.DocumentImportService;
import io.mikoshift.natsu.backend.service.documents.DocumentQueryService;
import io.mikoshift.natsu.backend.service.documents.DocumentSyncService;
import io.mikoshift.natsu.backend.service.documents.PackageUploadService;
import io.mikoshift.natsu.backend.service.storage.PackageStorageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/documents")
@Validated
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentQueryService documentQueryService;
    private final DocumentSyncService documentSyncService;
    private final DocumentImportService documentImportService;
    private final PackageUploadService packageUploadService;
    private final PackageStorageService packageStorageService;
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
        return toShowResponse(documentQueryService.get(user, id));
    }

    @PostMapping("/sync")
    DocumentIndexResponse sync(@AuthenticationPrincipal User user, @Valid @RequestBody DocumentSyncRequest request) {
        return toIndexResponse(documentSyncService.sync(user, request));
    }

    @PostMapping("/import")
    ResponseEntity<DocumentShowResponse> importDocument(
            @AuthenticationPrincipal User user, @RequestParam("file") MultipartFile file) {
        Document document = documentImportService.startImport(user, file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toShowResponse(document));
    }

    @PutMapping("/{id}/package")
    DocumentShowResponse uploadPackage(
            @AuthenticationPrincipal User user, @PathVariable UUID id, @RequestParam("package") MultipartFile file) {
        return toShowResponse(packageUploadService.upload(user, id, file));
    }

    @GetMapping("/{id}/package")
    ResponseEntity<Resource> downloadPackage(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        Document document = requirePackage(user, id);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/zip"))
                .header("X-Package-Sha256", document.getPackageSha256())
                .header("X-Package-Updated-At-Ms", String.valueOf(document.getPackageUpdatedAtMs()))
                .contentLength(document.getPackageSizeBytes())
                .body(packageStorageService.load(id));
    }

    @RequestMapping(value = "/{id}/package", method = RequestMethod.HEAD)
    ResponseEntity<Void> headPackage(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        Document document = requirePackage(user, id);
        return ResponseEntity.ok()
                .header("X-Package-Sha256", document.getPackageSha256())
                .header("X-Package-Updated-At-Ms", String.valueOf(document.getPackageUpdatedAtMs()))
                .contentLength(document.getPackageSizeBytes())
                .build();
    }

    private Document requirePackage(User user, UUID id) {
        Document document = documentQueryService.get(user, id);
        if (document.getPackageSha256() == null) {
            throw new NotFoundException("Package not attached");
        }
        return document;
    }

    private DocumentIndexResponse toIndexResponse(List<Document> documents) {
        return new DocumentIndexResponse(
                documents.stream().map(DocumentResponse::from).toList(), serverTimeService.nowMs());
    }

    private DocumentShowResponse toShowResponse(Document document) {
        return new DocumentShowResponse(DocumentResponse.from(document), serverTimeService.nowMs());
    }
}
