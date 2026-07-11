package io.mikoshift.natsu.service.documents;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.mikoshift.natsu.config.NatsuProperties;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.exception.QuotaExceededException;
import io.mikoshift.natsu.exception.ValidationException;
import io.mikoshift.natsu.repository.DocumentRepository;
import io.mikoshift.natsu.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StorageQuotaServiceTest {

    private static final long MAX_PACKAGE_BYTES = 1000;
    private static final long MAX_STORAGE_PER_USER = 5000;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private UserRepository userRepository;

    private StorageQuotaService quotaService;
    private User user;

    @BeforeEach
    void setUp() {
        NatsuProperties.RateLimit.Bucket bucket = new NatsuProperties.RateLimit.Bucket(5, 60);
        NatsuProperties.RateLimit rateLimit =
                new NatsuProperties.RateLimit(bucket, bucket, bucket, bucket, bucket, bucket);
        quotaService = new StorageQuotaService(
                documentRepository,
                userRepository,
                new NatsuProperties(
                        "/tmp/natsu-test",
                        MAX_PACKAGE_BYTES,
                        MAX_STORAGE_PER_USER,
                        List.of("*"),
                        rateLimit,
                        "http://localhost:3000/reset-password?token={token}",
                        "noreply@example.com",
                        new NatsuProperties.BookImportRecovery(true, 15, 5, 3)));
        user = new User();
        user.setId(1L);
    }

    @Test
    void rejectsSingleUploadOverTheMaxPackageSize() {
        assertThatThrownBy(() -> quotaService.checkUploadSize(MAX_PACKAGE_BYTES + 1))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void allowsSingleUploadAtExactlyTheMaxPackageSize() {
        assertThatCode(() -> quotaService.checkUploadSize(MAX_PACKAGE_BYTES)).doesNotThrowAnyException();
    }

    @Test
    void rejectsWhenTotalUsageWouldExceedThePerUserQuota() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(documentRepository.sumPackageSizeBytesByUser(user)).thenReturn(4500L);

        assertThatThrownBy(() -> quotaService.checkUserQuota(user, 600, 0)).isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void excludesTheReplacedDocumentsCurrentSizeFromTheUsageTotal() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        // 4500 already used, of which 400 belongs to the document being replaced; replacing it
        // with a 600-byte package brings usage to 4500 - 400 + 600 = 4700, under the 5000 cap.
        when(documentRepository.sumPackageSizeBytesByUser(user)).thenReturn(4500L);

        assertThatCode(() -> quotaService.checkUserQuota(user, 600, 400)).doesNotThrowAnyException();
    }
}
