package io.mikoshift.natsu.repository;

import io.mikoshift.natsu.entity.DictionaryTerm;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DictionaryTermRepository extends JpaRepository<DictionaryTerm, Long> {

    @Query(
            "select t from DictionaryTerm t join fetch t.dictionary"
                    + " where t.expression in :words or t.reading in :words")
    List<DictionaryTerm> findByWords(@Param("words") Collection<String> words);
}
