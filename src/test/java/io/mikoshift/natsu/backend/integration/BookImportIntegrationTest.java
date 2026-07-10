package io.mikoshift.natsu.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.mikoshift.natsu.backend.TestcontainersConfiguration;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// See AuthFlowIntegrationTest for why this override is needed on every integration test class.
@TestPropertySource(
        properties = {
            "natsu.rate-limit.login.capacity=1000000",
            "natsu.rate-limit.login-email.capacity=1000000",
            "natsu.rate-limit.register.capacity=1000000",
            "natsu.rate-limit.password-reset.capacity=1000000",
            "natsu.rate-limit.refresh.capacity=1000000",
            "natsu.rate-limit.refresh-token.capacity=1000000",
            "natsu.book-import-recovery.enabled=false"
        })
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BookImportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void storageRoot(DynamicPropertyRegistry registry) {
        registry.add("natsu.storage-root", () -> {
            try {
                return Files.createTempDirectory("natsu-test-storage").toString();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private String registerAndGetToken(String email) throws Exception {
        String response = mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Reader","email":"%s","password":"password123","password_confirmation":"password123"}
                                """.formatted(email)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    private String awaitStatus(String token, String documentId) throws Exception {
        String[] status = new String[1];
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100)).until(() -> {
            String response = mockMvc.perform(get("/v1/documents/" + documentId).header("Authorization", "Bearer " + token))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            status[0] = JsonPath.read(response, "$.document.status");
            return !status[0].equals("PENDING");
        });
        return status[0];
    }

    @Test
    void importingPlainTextEndsUpReadyWithADownloadablePackage() throws Exception {
        String token = registerAndGetToken("import-txt@example.com");
        MockMultipartFile file =
                new MockMultipartFile("file", "diary.txt", "text/plain", "Dear diary,\n\nToday was good.".getBytes(StandardCharsets.UTF_8));

        String importResponse = mockMvc.perform(multipart("/v1/documents/import").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.document.status").value("PENDING"))
                .andExpect(jsonPath("$.document.title").value("diary"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String documentId = JsonPath.read(importResponse, "$.document.id");

        assertThat(awaitStatus(token, documentId)).isEqualTo("READY");

        mockMvc.perform(request(HttpMethod.HEAD, "/v1/documents/" + documentId + "/package").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Package-Sha256"))
                .andExpect(header().exists("X-Package-Updated-At-Ms"))
                .andExpect(header().exists("Content-Length"));

        byte[] packageBytes = mockMvc.perform(get("/v1/documents/" + documentId + "/package").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        Map<String, String> entries = readZip(packageBytes);
        assertThat(entries).containsKey("manifest.json");
        assertThat(entries.get("manifest.json")).contains("\"sections\"");
        assertThat(entries).containsKey("sections/section-0.html");
        assertThat(entries.get("sections/section-0.html")).contains("Today was good.");
    }

    @Test
    void epubTitleFromMetadataOverridesTheFilenameFallback() throws Exception {
        String token = registerAndGetToken("import-epub@example.com");
        byte[] epub = buildMinimalEpub();
        MockMultipartFile file = new MockMultipartFile("file", "source.epub", "application/epub+zip", epub);

        String importResponse = mockMvc.perform(multipart("/v1/documents/import").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String documentId = JsonPath.read(importResponse, "$.document.id");

        assertThat(awaitStatus(token, documentId)).isEqualTo("READY");

        mockMvc.perform(get("/v1/documents/" + documentId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.document.title").value("EPUB Title"))
                .andExpect(jsonPath("$.document.char_count").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void malformedEpubEndsUpFailedWithAnErrorMessage() throws Exception {
        String token = registerAndGetToken("import-bad-epub@example.com");
        MockMultipartFile file =
                new MockMultipartFile("file", "broken.epub", "application/epub+zip", "not actually a zip".getBytes(StandardCharsets.UTF_8));

        String importResponse = mockMvc.perform(multipart("/v1/documents/import").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String documentId = JsonPath.read(importResponse, "$.document.id");

        assertThat(awaitStatus(token, documentId)).isEqualTo("FAILED");

        mockMvc.perform(get("/v1/documents/" + documentId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.document.import_error").exists());

        mockMvc.perform(request(HttpMethod.HEAD, "/v1/documents/" + documentId + "/package").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void importRejectsUnsupportedFileExtensions() throws Exception {
        String token = registerAndGetToken("import-unsupported@example.com");
        MockMultipartFile file = new MockMultipartFile("file", "book.pdf", "application/pdf", "irrelevant".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/v1/documents/import").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void directPackageUploadValidatesTheZipAndUpdatesTheDocument() throws Exception {
        String token = registerAndGetToken("package-upload@example.com");
        String documentId = createSyncedDocument(token);

        MockMultipartFile invalidZip =
                new MockMultipartFile("package", "package.zip", "application/zip", "not a zip".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart(HttpMethod.PUT, "/v1/documents/" + documentId + "/package")
                        .file(invalidZip)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());

        byte[] validZip = buildZip(Map.of("manifest.json", "{\"version\":1,\"sections\":[]}"));
        MockMultipartFile validPackage = new MockMultipartFile("package", "package.zip", "application/zip", validZip);
        mockMvc.perform(multipart(HttpMethod.PUT, "/v1/documents/" + documentId + "/package")
                        .file(validPackage)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.package_sha256").exists())
                .andExpect(jsonPath("$.document.package_size_bytes").value(validZip.length));
    }

    private String createSyncedDocument(String token) throws Exception {
        String id = java.util.UUID.randomUUID().toString();
        mockMvc.perform(post("/v1/documents/sync")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"documents":[{
                          "id":"%s","title":"Locally Imported","source_format":"PLAIN_TEXT","imported_at":1000,
                          "char_count":10,"last_read_char_offset":0,"last_read_block_index":0,
                          "last_read_block_char_offset":0,"updated_at_ms":1000,"deleted":false
                        }]}
                        """.formatted(id)));
        return id;
    }

    private static byte[] buildMinimalEpub() {
        return buildZip(Map.of(
                "META-INF/container.xml",
                        """
                        <?xml version="1.0"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                          <rootfiles>
                            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                          </rootfiles>
                        </container>
                        """,
                "OEBPS/content.opf",
                        """
                        <?xml version="1.0"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>EPUB Title</dc:title>
                          </metadata>
                          <manifest>
                            <item id="ch1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                          </manifest>
                          <spine>
                            <itemref idref="ch1"/>
                          </spine>
                        </package>
                        """,
                "OEBPS/chapter1.xhtml",
                        """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <html xmlns="http://www.w3.org/1999/xhtml">
                        <body><h1>Chapter One</h1><p>Once upon a time.</p></body>
                        </html>
                        """));
    }

    private static byte[] buildZip(Map<String, String> entries) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, String> entry : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, String> readZip(byte[] zipBytes) throws IOException {
        Map<String, String> entries = new java.util.LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
