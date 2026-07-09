package io.mikoshift.natsu.backend.service.documents;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.mikoshift.natsu.backend.config.NatsuProperties;
import io.mikoshift.natsu.backend.entity.User;
import io.mikoshift.natsu.backend.exception.QuotaExceededException;
import io.mikoshift.natsu.backend.exception.ValidationException;
import io.mikoshift.natsu.backend.repository.DocumentRepository;
import java.util.List;
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

    private StorageQuotaService quotaService;
    private User user;

    @BeforeEach
    void setUp() {
        quotaService = new StorageQuotaService(
                documentRepository,
                new NatsuProperties("/tmp/natsu-test", MAX_PACKAGE_BYTES, MAX_STORAGE_PER_USER, List.of("*"), 5, 60));
        user = new User();
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
        when(documentRepository.sumPackageSizeBytesByUser(user)).thenReturn(4500L);

        assertThatThrownBy(() -> quotaService.checkUserQuota(user, 600, 0)).isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void excludesTheReplacedDocumentsCurrentSizeFromTheUsageTotal() {
        // 4500 already used, of which 400 belongs to the document being replaced; replacing it
        // with a 600-byte package brings usage to 4500 - 400 + 600 = 4700, under the 5000 cap.
        when(documentRepository.sumPackageSizeBytesByUser(user)).thenReturn(4500L);

        assertThatCode(() -> quotaService.checkUserQuota(user, 600, 400)).doesNotThrowAnyException();
    }
}
