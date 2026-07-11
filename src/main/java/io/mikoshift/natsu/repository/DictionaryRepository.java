package io.mikoshift.natsu.repository;

import io.mikoshift.natsu.entity.Dictionary;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DictionaryRepository extends JpaRepository<Dictionary, UUID> {

    Optional<Dictionary> findByCatalogId(String catalogId);
}
