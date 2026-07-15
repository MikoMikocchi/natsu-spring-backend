package io.mikoshift.natsu.repository;

import io.mikoshift.natsu.entity.Document;
import io.mikoshift.natsu.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndUser(UUID id, User user);

    List<Document> findByUserAndUpdatedAtMsGreaterThanOrderByUpdatedAtMsAsc(User user, long since);

    /**
     * Documents still PENDING whose creation predates the staleness cutoff -- candidates for
     * stale-import recovery.
     */
    List<Document> findByStatusAndCreatedAtBefore(Document.Status status, Instant cutoff);

    /**
     * Matches against title and/or the extracted body text populated once a package has been
     * processed. The body text lives in {@code document_search_text}, a separate table (see
     * {@link io.mikoshift.natsu.entity.DocumentSearchText}) so that it never rides along with
     * ordinary document reads -- only this query touches it. Native SQL with {@code ILIKE} so
     * PostgreSQL can use the {@code pg_trgm} GIN indexes from migration 007.
     */
    @Query(value = """
                    select d.id as id, d.title as title, st.search_text as searchText
                    from documents d
                    left join document_search_text st on st.document_id = d.id
                    where d.user_id = :userId
                      and d.deleted_at is null
                      and (d.title ilike concat('%', :query, '%')
                           or st.search_text ilike concat('%', :query, '%'))
                    order by d.updated_at_ms desc
                    """, nativeQuery = true)
    List<DocumentSearchRow> searchByUserAndQuery(@Param("userId") Long userId, @Param("query") String query);

    @Query("select coalesce(sum(d.packageSizeBytes), 0) from Document d where d.user = :user and d.deletedAt is null")
    long sumPackageSizeBytesByUser(@Param("user") User user);

    @Query("select d.id from Document d where d.user = :user")
    List<UUID> findIdsByUser(@Param("user") User user);
}
