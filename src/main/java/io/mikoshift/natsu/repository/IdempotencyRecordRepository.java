package io.mikoshift.natsu.repository;

import io.mikoshift.natsu.entity.IdempotencyRecord;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT record FROM IdempotencyRecord record "
            + "WHERE record.userId = :userId AND record.idempotencyKey = :idempotencyKey")
    Optional<IdempotencyRecord> findByUserIdAndIdempotencyKeyForUpdate(
            @Param("userId") Long userId, @Param("idempotencyKey") String idempotencyKey);
}
