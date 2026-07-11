package io.mikoshift.natsu.repository;

import io.mikoshift.natsu.entity.ReaderSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReaderSettingRepository extends JpaRepository<ReaderSetting, Long> {

    /** Serializes first-time creation for a user so concurrent get-or-create calls cannot race. */
    @Query(value = "SELECT pg_advisory_xact_lock(:userId)", nativeQuery = true)
    void acquireCreationLock(@Param("userId") Long userId);
}
