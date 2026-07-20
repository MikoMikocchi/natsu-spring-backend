package io.mikoshift.natsu.service.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mikoshift.natsu.config.NatsuProperties;
import io.mikoshift.natsu.config.PropertiesFixtures;
import io.mikoshift.natsu.dto.request.DocumentSyncItemRequest;
import io.mikoshift.natsu.dto.request.DocumentSyncRequest;
import io.mikoshift.natsu.dto.response.DocumentIndexResponse;
import io.mikoshift.natsu.dto.response.DocumentResponse;
import io.mikoshift.natsu.entity.Document;
import io.mikoshift.natsu.entity.Document.SourceFormat;
import io.mikoshift.natsu.entity.IdempotencyRecord;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.exception.ValidationException;
import io.mikoshift.natsu.repository.IdempotencyRecordRepository;
import io.mikoshift.natsu.service.documents.DocumentSyncService;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private IdempotencyRecordRepository repository;

    @Mock
    private DocumentSyncService documentSyncService;

    private IdempotencyService idempotencyService;
    private ObjectMapper objectMapper;
    private User user;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        NatsuProperties properties = PropertiesFixtures.minimal("./storage", 1024, 1024);
        idempotencyService =
                new IdempotencyService(repository, objectMapper, Clock.fixed(FIXED_NOW, ZoneOffset.UTC), properties);
        user = new User();
        user.setId(1L);
    }

    @Test
    void replaysCachedResponseForDuplicateKeyAndMatchingBody() throws Exception {
        UUID documentId = UUID.randomUUID();
        DocumentSyncRequest request = sampleRequest(documentId, "Title", 1000L);
        DocumentIndexResponse cachedResponse = sampleResponse(documentId, "Title", 1000L);
        IdempotencyRecord record = new IdempotencyRecord(
                1L,
                "key-1",
                hashRequest(request),
                objectMapper.writeValueAsString(cachedResponse),
                FIXED_NOW.minusSeconds(60));
        when(repository.findByUserIdAndIdempotencyKeyForUpdate(1L, "key-1")).thenReturn(Optional.of(record));

        DocumentIndexResponse response = idempotencyService.executeDocumentSync(
                user, "key-1", request, () -> toIndexResponse(documentSyncService.sync(user, request)));

        assertThat(response.documents()).hasSize(1);
        assertThat(response.documents().get(0).title()).isEqualTo("Title");
        verify(documentSyncService, never()).sync(any(), any());
    }

    @Test
    void executesSyncAndPersistsRecordForNewKey() {
        UUID documentId = UUID.randomUUID();
        DocumentSyncRequest request = sampleRequest(documentId, "Fresh", 2000L);
        when(repository.findByUserIdAndIdempotencyKeyForUpdate(1L, "key-new")).thenReturn(Optional.empty());
        when(documentSyncService.sync(user, request)).thenReturn(List.of(new Document()));

        idempotencyService.executeDocumentSync(
                user, "key-new", request, () -> toIndexResponse(documentSyncService.sync(user, request)));

        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("key-new");
        assertThat(captor.getValue().getRequestHash()).isEqualTo(hashRequest(request));
    }

    @Test
    void rejectsDuplicateKeyWithDifferentRequestBody() throws Exception {
        UUID documentId = UUID.randomUUID();
        DocumentSyncRequest original = sampleRequest(documentId, "Title", 1000L);
        DocumentSyncRequest changed = sampleRequest(documentId, "Changed", 1000L);
        IdempotencyRecord record = new IdempotencyRecord(
                1L,
                "key-1",
                hashRequest(original),
                objectMapper.writeValueAsString(sampleResponse(documentId, "Title", 1000L)),
                FIXED_NOW.minusSeconds(60));
        when(repository.findByUserIdAndIdempotencyKeyForUpdate(1L, "key-1")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> idempotencyService.executeDocumentSync(
                        user, "key-1", changed, () -> toIndexResponse(documentSyncService.sync(user, changed))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("idempotency_key");
        verify(documentSyncService, never()).sync(any(), any());
    }

    @Test
    void reExecutesAfterExpiredRecordIsDeleted() {
        UUID documentId = UUID.randomUUID();
        DocumentSyncRequest request = sampleRequest(documentId, "Retry", 3000L);
        IdempotencyRecord expired = new IdempotencyRecord(
                1L,
                "key-expired",
                hashRequest(request),
                "{}",
                FIXED_NOW.minus(PropertiesFixtures.IDEMPOTENCY.keyTtl()).minusSeconds(1));
        when(repository.findByUserIdAndIdempotencyKeyForUpdate(1L, "key-expired"))
                .thenReturn(Optional.of(expired));
        when(documentSyncService.sync(user, request)).thenReturn(List.of(new Document()));

        idempotencyService.executeDocumentSync(
                user, "key-expired", request, () -> toIndexResponse(documentSyncService.sync(user, request)));

        verify(repository).delete(expired);
        verify(repository, times(1)).save(any(IdempotencyRecord.class));
        verify(documentSyncService).sync(user, request);
    }

    private DocumentSyncRequest sampleRequest(UUID id, String title, long updatedAtMs) {
        return new DocumentSyncRequest(List.of(new DocumentSyncItemRequest(
                id, "item-key", title, SourceFormat.EPUB, 1000L, 500, 0, null, 0, 0, updatedAtMs, false)));
    }

    private DocumentIndexResponse sampleResponse(UUID id, String title, long updatedAtMs) {
        return new DocumentIndexResponse(
                List.of(new DocumentResponse(
                        id,
                        title,
                        SourceFormat.EPUB,
                        Document.Status.READY,
                        null,
                        1000L,
                        500,
                        0,
                        null,
                        0,
                        0,
                        updatedAtMs,
                        0L,
                        0L,
                        null,
                        false)),
                FIXED_NOW.toEpochMilli());
    }

    private DocumentIndexResponse toIndexResponse(List<Document> documents) {
        return new DocumentIndexResponse(
                documents.stream().map(DocumentResponse::from).toList(), FIXED_NOW.toEpochMilli());
    }

    private String hashRequest(DocumentSyncRequest request) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JacksonException | java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
