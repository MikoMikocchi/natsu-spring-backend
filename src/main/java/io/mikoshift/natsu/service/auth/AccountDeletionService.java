package io.mikoshift.natsu.service.auth;

import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.exception.InvalidCredentialsException;
import io.mikoshift.natsu.repository.DocumentRepository;
import io.mikoshift.natsu.repository.UserRepository;
import io.mikoshift.natsu.security.oauth2.OAuth2AuthorizationSupport;
import io.mikoshift.natsu.service.storage.PackageStorageService;
import io.mikoshift.natsu.util.TransactionHooks;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final PackageStorageService packageStorageService;
    private final PasswordEncoder passwordEncoder;
    private final OAuth2AuthorizationSupport authorizationSupport;

    @Transactional
    public void deleteAccount(User authenticatedUser, String password) {
        User user = userRepository
                .findById(authenticatedUser.getId())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        List<UUID> documentIds = documentRepository.findIdsByUser(user);
        authorizationSupport.revokeAllForUser(user);
        userRepository.delete(user);
        TransactionHooks.afterCommit(() -> documentIds.forEach(packageStorageService::delete));
    }
}
