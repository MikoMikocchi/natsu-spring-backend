package io.mikoshift.natsu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", nullable = false, columnDefinition = "jsonb")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public IdempotencyRecord(
            Long userId, String idempotencyKey, String requestHash, String responseBody, Instant createdAt) {
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.responseBody = responseBody;
        this.createdAt = createdAt;
    }
}
