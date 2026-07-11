package io.mikoshift.natsu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * The id is client-assigned (a device generates it locally before the document ever reaches the
 * server, so it can be created offline), never server-generated -- there is deliberately no
 * {@code @GeneratedValue} here.
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
public class Document {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "source_format", nullable = false)
    private SourceFormat sourceFormat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.READY;

    @Column(name = "import_error")
    private String importError;

    /**
     * Incremented each time the stale-import recovery job (see {@code bookimport.recovery}) acts on
     * this document. Used to cap how many times recovery will touch the same stuck document before
     * giving up permanently, so a document that can never actually recover doesn't get scanned and
     * re-flagged forever.
     */
    @Column(name = "import_attempts", nullable = false)
    private int importAttempts = 0;

    @Column(name = "imported_at", nullable = false)
    private long importedAt = 0L;

    @Column(name = "char_count", nullable = false)
    private int charCount = 0;

    @Column(name = "last_read_char_offset", nullable = false)
    private int lastReadCharOffset = 0;

    @Column(name = "last_read_section_id")
    private String lastReadSectionId;

    @Column(name = "last_read_block_index", nullable = false)
    private int lastReadBlockIndex = 0;

    @Column(name = "last_read_block_char_offset", nullable = false)
    private int lastReadBlockCharOffset = 0;

    /**
     * Client-authoritative logical clock used to resolve sync conflicts; not a JPA-managed timestamp.
     */
    @Column(name = "updated_at_ms", nullable = false)
    private long updatedAtMs = 0L;

    @Column(name = "package_size_bytes", nullable = false)
    private long packageSizeBytes = 0L;

    @Column(name = "package_updated_at_ms", nullable = false)
    private long packageUpdatedAtMs = 0L;

    @Column(name = "package_sha256", length = 64)
    private String packageSha256;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum SourceFormat {
        EPUB,
        MARKDOWN,
        PLAIN_TEXT,
        FB2,
        DOCX,
        RTF
    }

    public enum Status {
        PENDING,
        READY,
        FAILED
    }
}
