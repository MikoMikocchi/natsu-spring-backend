package io.mikoshift.natsu.service.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.exception.InvalidCredentialsException;
import io.mikoshift.natsu.repository.DocumentRepository;
import io.mikoshift.natsu.repository.UserRepository;
import io.mikoshift.natsu.security.oauth2.OAuth2AuthorizationSupport;
import io.mikoshift.natsu.service.storage.PackageStorageService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private PackageStorageService packageStorageService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OAuth2AuthorizationSupport authorizationSupport;

    private AccountDeletionService accountDeletionService;
    private User user;

    @BeforeEach
    void setUp() {
        accountDeletionService = new AccountDeletionService(
                userRepository, documentRepository, packageStorageService, passwordEncoder, authorizationSupport);
        user = new User();
        user.setId(1L);
        user.setPasswordHash("hashed");
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rejectsDeletionWithWrongPassword() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> accountDeletionService.deleteAccount(user, "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository, never()).delete(user);
    }

    @Test
    void deletesUserAndPurgesPackageFilesOnceTheTransactionCommits() {
        UUID documentA = UUID.randomUUID();
        UUID documentB = UUID.randomUUID();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(documentRepository.findIdsByUser(user)).thenReturn(List.of(documentA, documentB));

        TransactionSynchronizationManager.initSynchronization();
        try {
            accountDeletionService.deleteAccount(user, "password123");
            verify(authorizationSupport).revokeAllForUser(user);
            verify(userRepository).delete(user);
            // File cleanup is deferred until the DB transaction actually commits.
            verify(packageStorageService, never()).delete(documentA);
            verify(packageStorageService, never()).delete(documentB);

            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(packageStorageService).delete(documentA);
        verify(packageStorageService).delete(documentB);
    }
}
