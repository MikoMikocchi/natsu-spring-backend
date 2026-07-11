package io.mikoshift.natsu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "dictionaries")
@Getter
@Setter
@NoArgsConstructor
public class Dictionary {

    @Id
    private UUID id;

    @Column(name = "catalog_id", nullable = false, unique = true)
    private String catalogId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String revision = "1";

    @Column(name = "term_count", nullable = false)
    private int termCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
