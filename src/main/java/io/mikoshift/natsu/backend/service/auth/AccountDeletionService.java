package io.mikoshift.natsu.backend.service.auth;

import io.mikoshift.natsu.backend.entity.User;
import io.mikoshift.natsu.backend.exception.InvalidCredentialsException;
import io.mikoshift.natsu.backend.repository.DocumentRepository;
import io.mikoshift.natsu.backend.repository.UserRepository;
import io.mikoshift.natsu.backend.service.storage.PackageStorageService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Synchronous account deletion for now; auth_tokens/documents/settings cascade-delete at the DB
 * level via ON DELETE CASCADE FKs. That cascade only covers the {@code documents} rows though --
 * package files live on disk (or, later, object storage) and aren't touched by it, so they're
 * removed explicitly here once the deletion actually commits (registerSynchronization rather than
 * a second {@code @Transactional} method, since self-invocation would bypass the proxy and just run
 * inline in the same transaction anyway). Revisit as {@code @Async} once AsyncConfig lands in the
 * import-pipeline phase, if deletion cost grows with related data.
 */
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final PackageStorageService packageStorageService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void deleteAccount(User user, String password) {
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        List<UUID> documentIds = documentRepository.findIdsByUser(user);
        userRepository.delete(user);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    documentIds.forEach(packageStorageService::delete);
                }
            });
        }
    }
}
