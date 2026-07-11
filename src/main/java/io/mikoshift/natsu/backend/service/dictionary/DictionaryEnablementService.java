package io.mikoshift.natsu.backend.service.dictionary;

import io.mikoshift.natsu.backend.config.CacheConfig;
import io.mikoshift.natsu.backend.entity.User;
import io.mikoshift.natsu.backend.repository.UserDictionarySettingRepository;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kept as its own bean (rather than a private method on the lookup service) so {@code @Cacheable}
 * is applied via the Spring proxy -- calling a {@code @Cacheable} method from another method on the
 * same bean bypasses the proxy and silently skips the cache.
 */
@Service
@RequiredArgsConstructor
public class DictionaryEnablementService {

    private final UserDictionarySettingRepository userDictionarySettingRepository;

    @Cacheable(cacheNames = CacheConfig.DICT_ENABLED_IDS_CACHE, key = "#user.id + ':' + #user.dictCacheVersion")
    @Transactional(readOnly = true)
    public Set<UUID> disabledDictionaryIds(User user) {
        return userDictionarySettingRepository.findDisabledDictionaryIdsByUser(user);
    }
}
