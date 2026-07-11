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
     * processed.
     */
    @Query("select d from Document d where d.user = :user and d.deletedAt is null "
            + "and (lower(d.title) like lower(concat('%', :query, '%')) "
            + "or lower(d.searchText) like lower(concat('%', :query, '%'))) "
            + "order by d.updatedAtMs desc")
    List<Document> searchByUserAndQuery(@Param("user") User user, @Param("query") String query);

    @Query("select coalesce(sum(d.packageSizeBytes), 0) from Document d where d.user = :user and d.deletedAt is null")
    long sumPackageSizeBytesByUser(@Param("user") User user);

    @Query("select d.id from Document d where d.user = :user")
    List<UUID> findIdsByUser(@Param("user") User user);
}
