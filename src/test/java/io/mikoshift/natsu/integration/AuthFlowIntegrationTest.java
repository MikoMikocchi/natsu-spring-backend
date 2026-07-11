package io.mikoshift.natsu.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.mikoshift.natsu.TestcontainersConfiguration;
import io.mikoshift.natsu.config.NatsuProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
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

// All integration tests share one cached Spring context, so they also share one RateLimiter
// instance and its bucket state -- without this override, auth calls from earlier test classes
// in the same run would eat into this class's rate limit budget.
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
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NatsuProperties natsuProperties;

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

    @Test
    void registerLoginUseTokenListSessionsRevokeRefreshLogout() throws Exception {
        String email = "reader@example.com";

        // Register creates a user + first device session and returns a usable token pair.
        String registerResponse = mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "device-a")
                        .content("""
                                {"name":"Reader","email":"%s","password":"password123","password_confirmation":"password123"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.refresh_token").exists())
                .andExpect(jsonPath("$.server_time_ms").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String firstToken = JsonPath.read(registerResponse, "$.token");

        // Logging in from a second "device" issues an independent session.
        String loginResponse = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "device-b")
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String secondToken = JsonPath.read(loginResponse, "$.token");
        String secondRefreshToken = JsonPath.read(loginResponse, "$.refresh_token");

        // A valid bearer token can fetch the current user.
        mockMvc.perform(get("/v1/auth/user").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email));

        // Missing/invalid tokens are rejected with the API's standard error shape.
        mockMvc.perform(get("/v1/auth/user"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors.base").exists());
        mockMvc.perform(get("/v1/auth/user").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());

        // Two device sessions are listed, each correctly flagged as "current" relative to its own
        // token.
        String sessionsAsDeviceA = mockMvc.perform(
                        get("/v1/auth/sessions").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Integer> deviceBSessionIds = JsonPath.read(sessionsAsDeviceA, "$[?(@.name == 'device-b')].id");
        assertThat(deviceBSessionIds).hasSize(1);
        Integer deviceBSessionId = deviceBSessionIds.get(0);

        // Revoking device-b's session immediately invalidates its access token.
        mockMvc.perform(delete("/v1/auth/sessions/" + deviceBSessionId).header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/v1/auth/user").header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isUnauthorized());

        // A revoked session's refresh token can no longer be exchanged either.
        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refresh_token":"%s"}
                                """.formatted(secondRefreshToken)))
                .andExpect(status().isUnauthorized());

        // Refreshing device-a's still-active session rotates it to a new access/refresh pair.
        String firstRefreshToken = JsonPath.read(registerResponse, "$.refresh_token");
        String refreshResponse = mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refresh_token":"%s"}
                                """.formatted(firstRefreshToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String rotatedToken = JsonPath.read(refreshResponse, "$.token");
        assertThat(rotatedToken).isNotEqualTo(firstToken);

        // The old access token issued before rotation is now dead...
        mockMvc.perform(get("/v1/auth/user").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isUnauthorized());
        // ...while the newly rotated one works.
        mockMvc.perform(get("/v1/auth/user").header("Authorization", "Bearer " + rotatedToken))
                .andExpect(status().isOk());

        // Logout revokes the session tied to the presented token.
        mockMvc.perform(post("/v1/auth/logout").header("Authorization", "Bearer " + rotatedToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/v1/auth/user").header("Authorization", "Bearer " + rotatedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationRejectsDuplicateEmailAndMismatchedPasswordConfirmation() throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"A","email":"dup@example.com","password":"password123","password_confirmation":"nope"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.password_confirmation").exists());

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"A","email":"dup@example.com","password":"password123","password_confirmation":"password123"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"B","email":"dup@example.com","password":"password123","password_confirmation":"password123"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void deleteAccountRequiresCorrectPassword() throws Exception {
        String response = mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Del","email":"delete-me@example.com","password":"password123","password_confirmation":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = JsonPath.read(response, "$.token");

        mockMvc.perform(delete("/v1/auth/account")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/v1/auth/account")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"password123"}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/auth/user").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletingAccountAlsoRemovesPackageFilesFromDisk() throws Exception {
        String response = mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Purge","email":"purge-me@example.com","password":"password123","password_confirmation":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = JsonPath.read(response, "$.token");

        String documentId = UUID.randomUUID().toString();
        mockMvc.perform(post("/v1/documents/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documents":[{
                                  "id":"%s","title":"Locally Imported","source_format":"PLAIN_TEXT","imported_at":1000,
                                  "char_count":10,"last_read_char_offset":0,"last_read_block_index":0,
                                  "last_read_block_char_offset":0,"updated_at_ms":1000,"deleted":false
                                }]}
                                """.formatted(documentId)))
                .andExpect(status().isOk());

        byte[] zip = buildZip("manifest.json");
        MockMultipartFile packageFile = new MockMultipartFile("package", "package.zip", "application/zip", zip);
        mockMvc.perform(multipart(HttpMethod.PUT, "/v1/documents/" + documentId + "/package")
                        .file(packageFile)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Path packagePath = Path.of(natsuProperties.storageRoot(), "packages", documentId + ".zip");
        assertThat(Files.exists(packagePath)).isTrue();

        mockMvc.perform(delete("/v1/auth/account")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"password123"}
                                """))
                .andExpect(status().isNoContent());

        assertThat(Files.exists(packagePath)).isFalse();
    }

    private static byte[] buildZip(String... entryNames) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String entryName : entryNames) {
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write("content".getBytes());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test
    void changedPasswordActuallyPersistsAndOldPasswordStopsWorking() throws Exception {
        String response = mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Changer","email":"change-password@example.com","password":"password123","password_confirmation":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = JsonPath.read(response, "$.token");

        mockMvc.perform(patch("/v1/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"current_password":"password123","password":"newpassword456","password_confirmation":"newpassword456"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"change-password@example.com","password":"password123"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"change-password@example.com","password":"newpassword456"}
                                """))
                .andExpect(status().isOk());
    }
}
