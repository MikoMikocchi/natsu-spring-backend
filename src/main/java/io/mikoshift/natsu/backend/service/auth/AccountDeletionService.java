package io.mikoshift.natsu.backend.service.auth;

import io.mikoshift.natsu.backend.entity.User;
import io.mikoshift.natsu.backend.exception.InvalidCredentialsException;
import io.mikoshift.natsu.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Synchronous account deletion for now; auth_tokens (and, from later phases, documents/settings)
 * cascade-delete at the DB level via ON DELETE CASCADE FKs. Revisit as {@code @Async} once
 * AsyncConfig lands in the import-pipeline phase, if deletion cost grows with related data.
 */
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void deleteAccount(User user, String password) {
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        userRepository.delete(user);
    }
}
