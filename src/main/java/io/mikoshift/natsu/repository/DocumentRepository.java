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
     * ordinary document reads -- only this query touches it, via an explicit left join since the
     * two entities aren't otherwise associated. Left (not inner) so documents that haven't
     * finished importing yet -- and so have no search-text row -- still match on title.
     */
    @Query("select new io.mikoshift.natsu.repository.DocumentSearchRow(d.id, d.title, st.searchText) "
            + "from Document d left join DocumentSearchText st on st.documentId = d.id "
            + "where d.user = :user and d.deletedAt is null "
            + "and (lower(d.title) like lower(concat('%', :query, '%')) "
            + "or lower(st.searchText) like lower(concat('%', :query, '%'))) "
            + "order by d.updatedAtMs desc")
    List<DocumentSearchRow> searchByUserAndQuery(@Param("user") User user, @Param("query") String query);

    @Query("select coalesce(sum(d.packageSizeBytes), 0) from Document d where d.user = :user and d.deletedAt is null")
    long sumPackageSizeBytesByUser(@Param("user") User user);

    @Query("select d.id from Document d where d.user = :user")
    List<UUID> findIdsByUser(@Param("user") User user);
}
