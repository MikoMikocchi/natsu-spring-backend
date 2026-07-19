package io.mikoshift.natsu.service.sync;

import io.mikoshift.natsu.config.NatsuProperties;
import io.mikoshift.natsu.dto.request.DocumentSyncRequest;
import io.mikoshift.natsu.dto.response.DocumentIndexResponse;
import io.mikoshift.natsu.entity.IdempotencyRecord;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.exception.ValidationException;
import io.mikoshift.natsu.repository.IdempotencyRecordRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final NatsuProperties properties;

    @Transactional
    public DocumentIndexResponse executeDocumentSync(
            User user, String idempotencyKey, DocumentSyncRequest request, Supplier<DocumentIndexResponse> action) {
        String requestHash = hashRequest(request);
        Instant now = clock.instant();

        IdempotencyRecord existing =
                repository.findByUserIdAndIdempotencyKeyForUpdate(user.getId(), idempotencyKey).orElse(null);
        if (existing != null) {
            if (isExpired(existing.getCreatedAt(), now)) {
                repository.delete(existing);
            } else {
                if (!existing.getRequestHash().equals(requestHash)) {
                    throw ValidationException.of(
                            "idempotency_key",
                            "Idempotency key was already used with a different request body");
                }
                return deserialize(existing.getResponseBody());
            }
        }

        DocumentIndexResponse response = action.get();
        repository.save(new IdempotencyRecord(
                user.getId(), idempotencyKey, requestHash, serialize(response), now));
        return response;
    }

    private boolean isExpired(Instant createdAt, Instant now) {
        return createdAt.isBefore(now.minus(properties.idempotency().keyTtl()));
    }

    private String hashRequest(DocumentSyncRequest request) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json));
        } catch (JacksonException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to hash idempotent request", ex);
        }
    }

    private String serialize(DocumentIndexResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize idempotent response", ex);
        }
    }

    private DocumentIndexResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, DocumentIndexResponse.class);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to deserialize idempotent response", ex);
        }
    }
}
