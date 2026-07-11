package io.mikoshift.natsu.service.bookimport;

import io.mikoshift.natsu.config.NatsuProperties;
import io.mikoshift.natsu.entity.Document;
import io.mikoshift.natsu.repository.DocumentRepository;
import io.mikoshift.natsu.service.bookimport.BookImportPersistence.RecoveryOutcome;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Finds {@link Document}s stuck in {@code PENDING} for longer than {@code
 * natsu.book-import-recovery.stale-after-minutes} and fails them with a clear error, so a crash or
 * restart mid-import (which silently discards the in-memory {@code @Async} task backing {@link
 * BookImportOrchestrator#importAsync}) never leaves a document stuck showing "importing..."
 * forever.
 *
 * <p><b>Why this can only fail the document, not actually resume the import:</b> {@code
 * importAsync} takes the raw source file bytes as a plain method argument -- they are never
 * persisted to disk or the database (only the final built package is, and only after a successful
 * import). Once the process that received the upload is gone, so are those bytes, in this instance
 * and every other one. Re-submitting into the pipeline as the ticket envisions is therefore only
 * possible for genuinely in-process retries (already handled by the existing spring-retry config on
 * {@code importAsync} for transient storage errors), not for crash/restart recovery. Persisting raw
 * uploads so a real re-import could happen later would be a materially bigger change (new storage,
 * quota accounting, cleanup-on-success) than this ticket -- narrowly scoped to "don't strand the
 * user with a silent, permanent 'importing...' spinner" -- calls for, so it's left out of scope
 * here and noted for a future ticket.
 *
 * <p><b>Same-instance race safety:</b> no in-memory tracking set is used. {@link
 * BookImportPersistence#recoverStaleDocument} re-reads the document inside its own short
 * transaction immediately before acting and only proceeds if it is still PENDING; a real import
 * completing (or failing) on this same JVM a moment before that check simply means the document is
 * no longer PENDING and the recovery pass is a no-op for it. Combined with a staleness threshold
 * (default 15 minutes) set comfortably above realistic import durations, this makes a false
 * positive against a slow-but-genuinely-running import on this instance very unlikely, without
 * needing to track in-flight document IDs in memory.
 *
 * <p><b>Multi-instance caveat:</b> nothing in this repository suggests more than one instance runs
 * at once today (no Kubernetes/orchestration manifests, a single-service docker-compose for local
 * dev only). If that ever changes, this scan-and-claim approach has a gap: two instances could both
 * read the same stale PENDING document as eligible and both attempt to recover it at nearly the
 * same moment. In this implementation that race is harmless (both sides just mark it FAILED with
 * the same message; the second write is a harmless no-op duplicate), but if a future version of
 * this job actually resubmits work rather than just failing it, that would need a DB-level claim
 * (e.g. a conditional {@code UPDATE ... WHERE status = 'PENDING'} or a {@code SELECT ... FOR UPDATE
 * SKIP LOCKED}) to avoid two instances double-processing the same document. Documented here rather
 * than solved, since it's out of scope for the current single-instance deployment.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StaleImportRecoveryService {

    private final DocumentRepository documentRepository;
    private final BookImportPersistence persistence;
    private final NatsuProperties properties;

    /**
     * {@code initialDelay = 0} makes this run once immediately on startup -- catching anything
     * stranded by a previous instance's crash/restart -- and then every {@code
     * check-interval-minutes} after that, catching anything that gets stuck mid-run without a restart
     * (e.g. a thread pool exception that didn't route through the normal retry/failure path). One
     * scheduled method covers both triggers the ticket asks for: there's nothing a dedicated startup
     * hook would do differently from the first tick of this schedule.
     */
    @Scheduled(
            initialDelayString = "0",
            fixedDelayString = "${natsu.book-import-recovery.check-interval-minutes:5}",
            timeUnit = TimeUnit.MINUTES)
    public void recoverStaleImports() {
        NatsuProperties.BookImportRecovery config = properties.bookImportRecovery();
        if (!config.enabled()) {
            return;
        }
        Instant cutoff = Instant.now().minus(config.staleAfterMinutes(), ChronoUnit.MINUTES);

        List<Document> candidates = documentRepository.findByStatusAndCreatedAtBefore(Document.Status.PENDING, cutoff);
        for (Document candidate : candidates) {
            // Calling out to BookImportPersistence (a separate Spring-managed bean) rather than a
            // @Transactional method on this class -- an @Transactional method called via "this"
            // bypasses the AOP proxy and would silently run without a transaction at all.
            RecoveryOutcome outcome = persistence.recoverStaleDocument(candidate.getId(), config.maxAttempts());
            logOutcome(candidate.getId(), outcome, config.maxAttempts());
        }
    }

    private void logOutcome(UUID documentId, RecoveryOutcome outcome, int maxAttempts) {
        switch (outcome) {
            case SKIPPED_NOT_PENDING ->
                log.debug("Document {} was no longer PENDING by the time recovery ran; skipping", documentId);
            case FAILED_STALE ->
                log.warn(
                        "Document {} was stuck in PENDING past the staleness threshold; marking failed since "
                                + "the original upload can't be resumed after a restart",
                        documentId);
            case FAILED_ATTEMPTS_EXCEEDED ->
                log.error(
                        "Document {} was recovered past the configured attempt cap ({}); marking failed. This is "
                                + "unexpected -- recovery should normally resolve a document on its first pass",
                        documentId,
                        maxAttempts);
        }
    }
}
