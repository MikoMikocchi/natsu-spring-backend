package io.mikoshift.natsu.backend.repository;

import io.mikoshift.natsu.backend.entity.DictionaryTerm;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DictionaryTermRepository extends JpaRepository<DictionaryTerm, Long> {

    @Query("select t from DictionaryTerm t join fetch t.dictionary where t.expression = :word or t.reading = :word")
    List<DictionaryTerm> findByWord(@Param("word") String word);
}
