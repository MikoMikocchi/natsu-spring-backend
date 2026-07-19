package io.mikoshift.natsu.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.mikoshift.natsu.TestcontainersConfiguration;
import io.mikoshift.natsu.entity.Document;
import io.mikoshift.natsu.repository.DocumentRepository;
import io.mikoshift.natsu.repository.UserRepository;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(
        properties = {
            "natsu.max-package-bytes=10000",
            "natsu.max-storage-bytes-per-user=1000",
            "natsu.rate-limit.login.capacity=1000000",
            "natsu.rate-limit.login-email.capacity=1000000",
            "natsu.rate-limit.register.capacity=1000000",
            "natsu.rate-limit.password-reset.capacity=1000000",
            "natsu.rate-limit.refresh.capacity=1000000",
            "natsu.rate-limit.refresh-token.capacity=1000000",
            "natsu.book-import-recovery.enabled=false"
        })
class StorageQuotaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void parallelPackageUploadsCannotExceedPerUserStorageQuota() throws Exception {
        String token = registerAndGetToken("quota-race@example.com");
        String documentIdA = createSyncedDocument(token);
        String documentIdB = createSyncedDocument(token);

        byte[] packageBytes = buildZip("manifest.json", "x".repeat(700));
        assertThat(packageBytes.length).isGreaterThan(600).isLessThan(1000);

        MockMultipartFile packageA = new MockMultipartFile("package", "a.zip", "application/zip", packageBytes);
        MockMultipartFile packageB = new MockMultipartFile("package", "b.zip", "application/zip", packageBytes);

        List<Integer> statuses = uploadInParallel(
                token, List.of(uploadRequest(documentIdA, packageA), uploadRequest(documentIdB, packageB)));

        assertThat(statuses.stream().filter(code -> code == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(code -> code == 422).count()).isEqualTo(1);

        long userId = userRepository
                .findByEmailIgnoreCase("quota-race@example.com")
                .orElseThrow()
                .getId();
        long totalUsed = documentRepository.sumPackageSizeBytesByUser(
                userRepository.findById(userId).orElseThrow());
        assertThat(totalUsed).isLessThanOrEqualTo(1000L);
    }

    @Test
    void databaseTriggerRejectsDirectWritesThatWouldExceedQuota() throws Exception {
        String token = registerAndGetToken("quota-db@example.com");
        var user = userRepository.findByEmailIgnoreCase("quota-db@example.com").orElseThrow();
        String documentIdA = createSyncedDocument(token);
        String documentIdB = createSyncedDocument(token);

        byte[] packageBytes = buildZip("manifest.json", "x".repeat(750));
        mockMvc.perform(uploadRequest(
                                documentIdA, new MockMultipartFile("package", "a.zip", "application/zip", packageBytes))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Document secondDocument = documentRepository
                .findByIdAndUser(UUID.fromString(documentIdB), user)
                .orElseThrow();
        secondDocument.setPackageSizeBytes(300);

        assertThatThrownBy(() -> documentRepository.saveAndFlush(secondDocument))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("storage_quota_exceeded");
    }

    private String registerAndGetToken(String email) throws Exception {
        return OAuth2TestSupport.registerAndLogin(mockMvc, email).accessToken();
    }

    private String createSyncedDocument(String token) throws Exception {
        String id = UUID.randomUUID().toString();
        mockMvc.perform(DocumentSyncTestSupport.syncPost(
                        token,
                        DocumentSyncTestSupport.freshIdempotencyKey(),
                        """
                                {"documents":[{
                                  "id":"%s","idempotency_key":"%s","title":"Doc","source_format":"PLAIN_TEXT","imported_at":1000,
                                  "char_count":10,"last_read_char_offset":0,"last_read_block_index":0,
                                  "last_read_block_char_offset":0,"updated_at_ms":1000,"deleted":false
                                }]}
                                """
                                .formatted(id, DocumentSyncTestSupport.freshIdempotencyKey())))
                .andExpect(status().isOk());
        return id;
    }

    private static MockMultipartHttpServletRequestBuilder uploadRequest(String documentId, MockMultipartFile file) {
        return multipart(HttpMethod.PUT, "/v1/documents/" + documentId + "/package")
                .file(file);
    }

    private List<Integer> uploadInParallel(String token, List<MockMultipartHttpServletRequestBuilder> requests)
            throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(requests.size())) {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (MockMultipartHttpServletRequestBuilder request : requests) {
                tasks.add(() -> mockMvc.perform(request.header("Authorization", "Bearer " + token))
                        .andReturn()
                        .getResponse()
                        .getStatus());
            }
            List<Future<Integer>> futures = executor.invokeAll(tasks);
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
            return statuses;
        }
    }

    private static byte[] buildZip(String entryName, String content) throws Exception {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        CRC32 crc = new CRC32();
        crc.update(data);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            ZipEntry entry = new ZipEntry(entryName);
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(data.length);
            entry.setCompressedSize(data.length);
            entry.setCrc(crc.getValue());
            zip.putNextEntry(entry);
            zip.write(data);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
