package io.mikoshift.natsu.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jayway.jsonpath.JsonPath;
import io.mikoshift.natsu.TestcontainersConfiguration;
import io.mikoshift.natsu.entity.Dictionary;
import io.mikoshift.natsu.service.dictionary.TermBankImportService;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Single end-to-end walkthrough of the entire application: auth, settings, dictionaries,
 * documents (import/sync/search/packages), multi-device sessions, password reset, token lifecycle,
 * and account deletion.
 */
@TestPropertySource(
        properties = {
            "natsu.rate-limit.login.capacity=1000000",
            "natsu.rate-limit.login-email.capacity=1000000",
            "natsu.rate-limit.register.capacity=1000000",
            "natsu.rate-limit.password-reset.capacity=1000000",
            "natsu.rate-limit.refresh.capacity=1000000",
            "natsu.rate-limit.refresh-token.capacity=1000000",
            "natsu.password-reset-url-template=https://natsu.example/reset-password?token={token}",
            "natsu.mail-from=noreply@natsu.example",
            "spring.mail.host=localhost",
            "natsu.book-import-recovery.enabled=false"
        })
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FullAppFlowIntegrationTest {

    private static final Pattern RESET_TOKEN_PATTERN = Pattern.compile("token=([A-Za-z0-9_-]+)");
    private static final GreenMail greenMail = new GreenMail(ServerSetupTest.SMTP);

    static {
        greenMail.start();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TermBankImportService termBankImportService;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.port", () -> greenMail.getSmtp().getPort());
        registry.add("natsu.storage-root", () -> {
            try {
                return Files.createTempDirectory("natsu-e2e-storage").toString();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @AfterAll
    static void stopGreenMail() {
        greenMail.stop();
    }

    @Test
    void fullUserJourneyFromRegistrationThroughAccountDeletion() throws Exception {
        String email = "e2e-" + System.nanoTime() + "@example.com";
        String initialPassword = OAuth2TestSupport.DEFAULT_PASSWORD;
        String resetPassword = "resetpass789";

        // --- 1. Register & login ---
        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "e2e-phone")
                        .content("""
                                {"name":"E2E Reader","email":"%s","password":"%s","password_confirmation":"%s"}
                                """
                                .formatted(email, initialPassword, initialPassword)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value(email));

        OAuth2TestSupport.TokenPair tokens =
                OAuth2TestSupport.login(mockMvc, email, initialPassword, "e2e-phone");

        mockMvc.perform(get("/userinfo").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        // --- 2. Reader settings ---
        mockMvc.perform(get("/v1/settings/reader").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.theme").value("LIGHT"))
                .andExpect(jsonPath("$.settings.font_size_sp").value(16.0));

        mockMvc.perform(patch("/v1/settings/reader")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"theme":"DARK","font_size_sp":18.0,"updated_at_ms":1000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.theme").value("DARK"));

        // --- 3. Dictionary ---
        Dictionary dictionary =
                termBankImportService.importZip("e2e-catalog-" + System.nanoTime(), buildDictionaryFixtureZip());
        String dictionaryId = dictionary.getId().toString();

        mockMvc.perform(get("/v1/dictionaries").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dictionaries[?(@.id == '" + dictionaryId + "')].enabled")
                        .value(org.hamcrest.Matchers.contains(true)));

        mockMvc.perform(get("/v1/dictionary/lookup")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .param("q", "食べる"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].word").value("食べる"))
                .andExpect(jsonPath("$.data[0].match_kind").value("DIRECT"));

        mockMvc.perform(patch("/v1/dictionaries/" + dictionaryId + "/toggle")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/dictionary/lookup")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .param("q", "食べる"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(patch("/v1/dictionaries/" + dictionaryId + "/toggle")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isNoContent());

        // --- 4. Import a book, download its package, sync reading progress ---
        String uniqueTitle = "E2E Novel " + System.nanoTime();
        MockMultipartFile bookFile = new MockMultipartFile(
                "file",
                "novel.txt",
                "text/plain",
                ("Title: " + uniqueTitle + "\n\nThe hero ate breakfast.").getBytes(StandardCharsets.UTF_8));

        String importResponse = mockMvc.perform(multipart("/v1/documents/import")
                        .file(bookFile)
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.document.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String documentId = JsonPath.read(importResponse, "$.document.id");

        awaitImportReady(tokens.accessToken(), documentId);

        String showResponse = mockMvc.perform(
                        get("/v1/documents/" + documentId).header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long documentUpdatedAtMs = ((Number) JsonPath.read(showResponse, "$.document.updated_at_ms")).longValue();
        String documentTitle = JsonPath.read(showResponse, "$.document.title");

        mockMvc.perform(request(HttpMethod.HEAD, "/v1/documents/" + documentId + "/package")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Package-Sha256"));

        byte[] packageBytes = mockMvc.perform(
                        get("/v1/documents/" + documentId + "/package")
                                .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        Map<String, String> packageEntries = readZip(packageBytes);
        assertThat(packageEntries).containsKey("manifest.json");
        assertThat(packageEntries.values().stream().anyMatch(content -> content.contains("ate breakfast")))
                .isTrue();

        mockMvc.perform(post("/v1/documents/sync")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documents":[{
                                  "id":"%s","title":"%s","source_format":"PLAIN_TEXT","imported_at":1000,
                                  "char_count":500,"last_read_char_offset":42,"last_read_section_id":"section-0",
                                  "last_read_block_index":1,"last_read_block_char_offset":5,
                                  "updated_at_ms":%d,"deleted":false
                                }]}
                                """
                                .formatted(documentId, uniqueTitle, documentUpdatedAtMs + 1000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[0].last_read_char_offset").value(42));

        mockMvc.perform(get("/v1/documents/" + documentId).header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.last_read_char_offset").value(42))
                .andExpect(jsonPath("$.document.title").value(uniqueTitle));

        mockMvc.perform(get("/v1/documents/search")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .param("q", uniqueTitle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].title").value(uniqueTitle));

        mockMvc.perform(get("/v1/documents")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .param("since", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[?(@.id == '" + documentId + "')]").exists());

        assertThat(documentTitle).isEqualTo("novel");

        // --- 5. Second device session ---
        OAuth2TestSupport.TokenPair secondDevice =
                OAuth2TestSupport.login(mockMvc, email, initialPassword, "e2e-tablet");

        mockMvc.perform(get("/v1/auth/sessions").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // --- 6. Password reset via email (revokes all sessions) ---
        greenMail.reset();

        mockMvc.perform(post("/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(greenMail.getReceivedMessages()).hasSize(1));

        String resetToken = extractResetToken(greenMail.getReceivedMessages()[0]);

        mockMvc.perform(post("/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s","password_confirmation":"%s"}
                                """
                                .formatted(resetToken, resetPassword, resetPassword)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/userinfo").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/userinfo").header("Authorization", "Bearer " + secondDevice.accessToken()))
                .andExpect(status().isUnauthorized());

        OAuth2TestSupport.TokenPair afterReset =
                OAuth2TestSupport.login(mockMvc, email, resetPassword, "e2e-phone");

        // --- 7. Refresh & revoke ---
        MvcResult refreshResult = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", OAuth2TestSupport.CLIENT_ID)
                        .param("refresh_token", afterReset.refreshToken()))
                .andExpect(status().isOk())
                .andReturn();
        SecurityContextHolder.clearContext();

        String refreshedAccess = JsonPath.read(refreshResult.getResponse().getContentAsString(), "$.access_token");
        String refreshedRefresh = JsonPath.read(refreshResult.getResponse().getContentAsString(), "$.refresh_token");
        assertThat(refreshedAccess).isNotEqualTo(afterReset.accessToken());

        mockMvc.perform(get("/userinfo").header("Authorization", "Bearer " + refreshedAccess))
                .andExpect(status().isOk());

        mockMvc.perform(post("/oauth2/revoke")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", OAuth2TestSupport.CLIENT_ID)
                        .param("token", refreshedRefresh)
                        .param("token_type_hint", "refresh_token"))
                .andExpect(status().isOk());
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/userinfo").header("Authorization", "Bearer " + refreshedAccess))
                .andExpect(status().isUnauthorized());

        // --- 8. Re-login and delete account ---
        String finalToken = OAuth2TestSupport.obtainAccessToken(mockMvc, email, resetPassword);

        mockMvc.perform(get("/v1/documents/" + documentId).header("Authorization", "Bearer " + finalToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/v1/auth/account")
                        .header("Authorization", "Bearer " + finalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"%s"}
                                """.formatted(resetPassword)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/userinfo").header("Authorization", "Bearer " + finalToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, resetPassword)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors.base").exists());
    }

    private void awaitImportReady(String token, String documentId) {
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    String response = mockMvc.perform(
                                    get("/v1/documents/" + documentId).header("Authorization", "Bearer " + token))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
                    assertThat((String) JsonPath.read(response, "$.document.status")).isEqualTo("READY");
                });
    }

    private static String extractResetToken(MimeMessage message) throws Exception {
        String body = (String) message.getContent();
        Matcher matcher = RESET_TOKEN_PATTERN.matcher(body);
        assertThat(matcher.find()).as("reset token in email body").isTrue();
        return matcher.group(1);
    }

    private static byte[] buildDictionaryFixtureZip() {
        return buildZip("""
                [
                  ["食べる","たべる","","v1",10,["to eat"],1,""]
                ]
                """);
    }

    private static byte[] buildZip(String termBankJson) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry("index.json"));
                zip.write("""
                        {"title":"E2E Dictionary","revision":"1"}
                        """.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();

                zip.putNextEntry(new ZipEntry("term_bank_1.json"));
                zip.write(termBankJson.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
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
