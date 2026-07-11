package io.mikoshift.natsu.backend.service.dictionary;

import io.mikoshift.natsu.backend.entity.Dictionary;
import io.mikoshift.natsu.backend.entity.User;
import io.mikoshift.natsu.backend.entity.UserDictionarySetting;
import io.mikoshift.natsu.backend.exception.NotFoundException;
import io.mikoshift.natsu.backend.repository.DictionaryRepository;
import io.mikoshift.natsu.backend.repository.UserDictionarySettingRepository;
import io.mikoshift.natsu.backend.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DictionaryToggleService {

    private final DictionaryRepository dictionaryRepository;
    private final UserDictionarySettingRepository userDictionarySettingRepository;
    private final UserRepository userRepository;

    /**
     * Creates or deletes the opt-out row, then bumps the user's cache-version counter. {@code user}
     * arrives detached (loaded in an earlier request-handling transaction by the auth filter), so the
     * version bump needs an explicit save -- merely mutating a detached entity's field does nothing
     * without one.
     */
    @Transactional
    public void toggle(User user, UUID dictionaryId) {
        Dictionary dictionary = dictionaryRepository
                .findById(dictionaryId)
                .orElseThrow(() -> new NotFoundException("Dictionary not found"));

        userDictionarySettingRepository
                .findByUserAndDictionary(user, dictionary)
                .ifPresentOrElse(userDictionarySettingRepository::delete, () -> {
                    UserDictionarySetting setting = new UserDictionarySetting();
                    setting.setUser(user);
                    setting.setDictionary(dictionary);
                    userDictionarySettingRepository.save(setting);
                });

        user.setDictCacheVersion(user.getDictCacheVersion() + 1);
        userRepository.save(user);
    }
}
