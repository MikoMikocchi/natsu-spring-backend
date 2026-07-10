package io.mikoshift.natsu.backend.service.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mikoshift.natsu.backend.dto.request.DocumentSyncItemRequest;
import io.mikoshift.natsu.backend.dto.request.DocumentSyncRequest;
import io.mikoshift.natsu.backend.entity.Document;
import io.mikoshift.natsu.backend.entity.Document.SourceFormat;
import io.mikoshift.natsu.backend.entity.User;
import io.mikoshift.natsu.backend.repository.DocumentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentSyncServiceTest {

  @Mock private DocumentRepository documentRepository;

  private DocumentSyncService syncService;
  private User user;

  @BeforeEach
  void setUp() {
    syncService = new DocumentSyncService(documentRepository);
    user = new User();
    user.setId(1L);
  }

  @Test
  void createsANewDocumentWhenNoneExistsYetForThisUser() {
    UUID id = UUID.randomUUID();
    DocumentSyncItemRequest item =
        new DocumentSyncItemRequest(
            id, "My Book", SourceFormat.EPUB, 1000L, 500, 10, "sec-1", 0, 10, 5000L, false);
    when(documentRepository.findByIdAndUser(id, user)).thenReturn(Optional.empty());
    when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

    List<Document> result = syncService.sync(user, new DocumentSyncRequest(List.of(item)));

    assertThat(result).hasSize(1);
    Document created = result.get(0);
    assertThat(created.getId()).isEqualTo(id);
    assertThat(created.getUser()).isSameAs(user);
    assertThat(created.getTitle()).isEqualTo("My Book");
    assertThat(created.getUpdatedAtMs()).isEqualTo(5000L);
    assertThat(created.getDeletedAt()).isNull();
    verify(documentRepository).save(created);
  }

  @Test
  void ignoresAnIncomingUpdateOlderThanTheStoredVersion() {
    // A device catching up after being offline pushes a stale snapshot; the newer server-side
    // state must win and must not be overwritten.
    UUID id = UUID.randomUUID();
    Document stored = new Document();
    stored.setId(id);
    stored.setUser(user);
    stored.setTitle("Server Title");
    stored.setUpdatedAtMs(9000L);
    when(documentRepository.findByIdAndUser(id, user)).thenReturn(Optional.of(stored));

    DocumentSyncItemRequest staleItem =
        new DocumentSyncItemRequest(
            id, "Stale Title", SourceFormat.EPUB, 1000L, 500, 10, "sec-1", 0, 10, 8000L, false);

    List<Document> result = syncService.sync(user, new DocumentSyncRequest(List.of(staleItem)));

    assertThat(result).containsExactly(stored);
    assertThat(stored.getTitle()).isEqualTo("Server Title");
    verify(documentRepository, never()).save(any(Document.class));
  }

  @Test
  void appliesAnIncomingUpdateThatIsNewerThanTheStoredVersion() {
    UUID id = UUID.randomUUID();
    Document stored = new Document();
    stored.setId(id);
    stored.setUser(user);
    stored.setTitle("Old Title");
    stored.setUpdatedAtMs(1000L);
    when(documentRepository.findByIdAndUser(id, user)).thenReturn(Optional.of(stored));
    when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

    DocumentSyncItemRequest newerItem =
        new DocumentSyncItemRequest(
            id, "New Title", SourceFormat.EPUB, 2000L, 800, 20, "sec-2", 1, 5, 2000L, false);

    List<Document> result = syncService.sync(user, new DocumentSyncRequest(List.of(newerItem)));

    assertThat(result).containsExactly(stored);
    assertThat(stored.getTitle()).isEqualTo("New Title");
    assertThat(stored.getCharCount()).isEqualTo(800);
    assertThat(stored.getUpdatedAtMs()).isEqualTo(2000L);
    verify(documentRepository).save(stored);
  }

  @Test
  void appliesAnUpdateWithTheSameTimestampAsStored() {
    // Equal timestamps are not treated as "older", so the update is applied -- this matters for
    // idempotent retries of the same push.
    UUID id = UUID.randomUUID();
    Document stored = new Document();
    stored.setId(id);
    stored.setUser(user);
    stored.setUpdatedAtMs(5000L);
    when(documentRepository.findByIdAndUser(id, user)).thenReturn(Optional.of(stored));
    when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

    DocumentSyncItemRequest sameTimestampItem =
        new DocumentSyncItemRequest(
            id, "Same Clock", SourceFormat.EPUB, 1000L, 100, 0, null, 0, 0, 5000L, false);

    syncService.sync(user, new DocumentSyncRequest(List.of(sameTimestampItem)));

    assertThat(stored.getTitle()).isEqualTo("Same Clock");
  }

  @Test
  void marksADocumentDeletedWhenTheDeletedFlagIsSet() {
    UUID id = UUID.randomUUID();
    Document stored = new Document();
    stored.setId(id);
    stored.setUser(user);
    stored.setUpdatedAtMs(1000L);
    when(documentRepository.findByIdAndUser(id, user)).thenReturn(Optional.of(stored));
    when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

    DocumentSyncItemRequest deleteItem =
        new DocumentSyncItemRequest(
            id, "Doomed", SourceFormat.EPUB, 1000L, 100, 0, null, 0, 0, 2000L, true);

    syncService.sync(user, new DocumentSyncRequest(List.of(deleteItem)));

    assertThat(stored.getDeletedAt()).isNotNull();
  }

  @Test
  void defaultsANullTitleToAnEmptyString() {
    UUID id = UUID.randomUUID();
    when(documentRepository.findByIdAndUser(id, user)).thenReturn(Optional.empty());
    when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

    DocumentSyncItemRequest itemWithNullTitle =
        new DocumentSyncItemRequest(
            id, null, SourceFormat.EPUB, 1000L, 100, 0, null, 0, 0, 2000L, false);

    List<Document> result =
        syncService.sync(user, new DocumentSyncRequest(List.of(itemWithNullTitle)));

    assertThat(result.get(0).getTitle()).isEmpty();
  }

  @Test
  void appliesEachItemInABatchIndependently() {
    UUID staleId = UUID.randomUUID();
    UUID newId = UUID.randomUUID();
    Document staleStored = new Document();
    staleStored.setId(staleId);
    staleStored.setUser(user);
    staleStored.setUpdatedAtMs(9000L);
    when(documentRepository.findByIdAndUser(staleId, user)).thenReturn(Optional.of(staleStored));
    when(documentRepository.findByIdAndUser(newId, user)).thenReturn(Optional.empty());
    when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

    DocumentSyncItemRequest staleItem =
        new DocumentSyncItemRequest(
            staleId, "Stale", SourceFormat.EPUB, 1000L, 100, 0, null, 0, 0, 500L, false);
    DocumentSyncItemRequest freshItem =
        new DocumentSyncItemRequest(
            newId, "Fresh", SourceFormat.MARKDOWN, 1000L, 100, 0, null, 0, 0, 500L, false);

    List<Document> result =
        syncService.sync(user, new DocumentSyncRequest(List.of(staleItem, freshItem)));

    assertThat(result).hasSize(2);
    verify(documentRepository, times(1)).save(any(Document.class));
  }
}
