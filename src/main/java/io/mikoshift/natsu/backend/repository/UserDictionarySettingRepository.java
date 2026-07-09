package io.mikoshift.natsu.backend.repository;

import io.mikoshift.natsu.backend.entity.Dictionary;
import io.mikoshift.natsu.backend.entity.User;
import io.mikoshift.natsu.backend.entity.UserDictionarySetting;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserDictionarySettingRepository extends JpaRepository<UserDictionarySetting, Long> {

    Optional<UserDictionarySetting> findByUserAndDictionary(User user, Dictionary dictionary);

    @Query("select s.dictionary.id from UserDictionarySetting s where s.user = :user")
    Set<UUID> findDisabledDictionaryIdsByUser(@Param("user") User user);
}
