package io.mikoshift.natsu.backend.repository;

import io.mikoshift.natsu.backend.entity.Document;
import io.mikoshift.natsu.backend.entity.User;
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
     * Matches against title and/or the extracted body text (search_text stays null until package
     * processing exists, so early on this is effectively a title search).
     */
    @Query("select d from Document d where d.user = :user and d.deletedAt is null "
            + "and (lower(d.title) like lower(concat('%', :query, '%')) "
            + "or lower(d.searchText) like lower(concat('%', :query, '%'))) "
            + "order by d.updatedAtMs desc")
    List<Document> searchByUserAndQuery(@Param("user") User user, @Param("query") String query);
}
