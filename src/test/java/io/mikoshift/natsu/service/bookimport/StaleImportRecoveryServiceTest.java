package io.mikoshift.natsu.service.bookimport;

import static org.assertj.core.api.Assertions.assertThat;

import io.mikoshift.natsu.TestcontainersConfiguration;
import io.mikoshift.natsu.entity.Document;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.repository.DocumentRepository;
import io.mikoshift.natsu.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Seeds Documents directly at PENDING with a backdated {@code created_at} (JPA won't let a normal
 * save touch that column post-insert -- {@code updatable = false} -- so a raw SQL update is used to
 * simulate "this document has been sitting here since before the app last restarted"), then invokes
 * {@link StaleImportRecoveryService#recoverStaleImports()} directly rather than waiting on the real
 * schedule.
 */
// See AuthFlowIntegrationTest for why this override is needed on every integration test class.
@TestPropertySource(
        properties = {
            "natsu.rate-limit.login.capacity=1000000",
            "natsu.rate-limit.login-email.capacity=1000000",
            "natsu.rate-limit.register.capacity=1000000",
            "natsu.rate-limit.password-reset.capacity=1000000",
            "natsu.rate-limit.refresh.capacity=1000000",
            "natsu.rate-limit.refresh-token.capacity=1000000",
            // Isolated from the default application.yml value so this test controls staleness
            // directly via how far back it backdates created_at, rather than depending on timing.
            // Explicitly enabled (already the default) since this class specifically tests the
            // recovery job's behavior -- unlike every other integration test class, which disables
            // it to keep the scheduled scan from touching PENDING documents it never asked about.
            "natsu.book-import-recovery.enabled=true",
            "natsu.book-import-recovery.stale-after-minutes=10",
            "natsu.book-import-recovery.max-attempts=3"
        })
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StaleImportRecoveryServiceTest {

    @Autowired
    private StaleImportRecoveryService recoveryService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User createUser(String email) {
        User user = new User();
        user.setName("Reader");
        user.setEmail(email);
        user.setPasswordHash("irrelevant");
        return userRepository.save(user);
    }

    private Document seedPendingDocument(User user, int importAttempts, long ageMinutes) {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setUser(user);
        document.setTitle("Stuck Import");
        document.setSourceFormat(Document.SourceFormat.PLAIN_TEXT);
        document.setStatus(Document.Status.PENDING);
        document.setImportAttempts(importAttempts);
        document = documentRepository.save(document);

        Instant backdated = Instant.now().minus(ageMinutes, ChronoUnit.MINUTES);
        jdbcTemplate.update(
                "update documents set created_at = ? where id = ?", Timestamp.from(backdated), document.getId());
        return document;
    }

    @Test
    void marksAStalePendingDocumentFailedWithAClearError() {
        User user = createUser("recovery-stale@example.com");
        Document stuck = seedPendingDocument(user, 0, 30);

        recoveryService.recoverStaleImports();

        Document reloaded = documentRepository.findById(stuck.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(Document.Status.FAILED);
        assertThat(reloaded.getImportError()).isNotBlank();
        assertThat(reloaded.getImportAttempts()).isEqualTo(1);
    }

    @Test
    void leavesARecentlyStartedPendingDocumentAlone() {
        User user = createUser("recovery-fresh@example.com");
        // Only 1 minute old -- well under the 10-minute staleness threshold, so this looks like a
        // genuinely in-progress import rather than something stranded by a restart.
        Document fresh = seedPendingDocument(user, 0, 1);

        recoveryService.recoverStaleImports();

        Document reloaded = documentRepository.findById(fresh.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(Document.Status.PENDING);
        assertThat(reloaded.getImportAttempts()).isZero();
    }

    @Test
    void leavesAlreadyTerminalDocumentsAlone() {
        User user = createUser("recovery-ready@example.com");
        Document ready = seedPendingDocument(user, 0, 30);
        ready.setStatus(Document.Status.READY);
        documentRepository.save(ready);

        recoveryService.recoverStaleImports();

        Document reloaded = documentRepository.findById(ready.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(Document.Status.READY);
        assertThat(reloaded.getImportAttempts()).isZero();
    }

    @Test
    void aDocumentAlreadyAtTheAttemptCapIsMarkedFailedRatherThanRecoveredAgain() {
        User user = createUser("recovery-cap@example.com");
        // Already at max-attempts (3) from prior recovery passes -- this call would push it to 4.
        Document exhausted = seedPendingDocument(user, 3, 30);

        recoveryService.recoverStaleImports();

        Document reloaded = documentRepository.findById(exhausted.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(Document.Status.FAILED);
        assertThat(reloaded.getImportAttempts()).isEqualTo(4);
        assertThat(reloaded.getImportError()).isNotBlank();
    }
}
