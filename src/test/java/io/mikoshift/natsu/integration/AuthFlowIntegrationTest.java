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
import org.springframework.test.web.servlet.MvcResult;

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

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "device-a")
                        .content("""
                                {"name":"Reader","email":"%s","password":"password123","password_confirmation":"password123"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.server_time_ms").exists());

        OAuth2TestSupport.TokenPair firstPair = OAuth2TestSupport.login(mockMvc, email, "password123", "device-a");
        String firstToken = firstPair.accessToken();
        String firstRefreshToken = firstPair.refreshToken();

        OAuth2TestSupport.TokenPair secondPair = OAuth2TestSupport.login(mockMvc, email, "password123", "device-b");
        String secondToken = secondPair.accessToken();
        String secondRefreshToken = secondPair.refreshToken();

        mockMvc.perform(get("/userinfo").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(get("/userinfo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors.base").exists());
        mockMvc.perform(get("/userinfo").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());

        String sessionsAsDeviceA = mockMvc.perform(
                        get("/v1/auth/sessions").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> deviceBSessionIds = JsonPath.read(sessionsAsDeviceA, "$[?(@.name == 'device-b')].id");
        assertThat(deviceBSessionIds).hasSize(1);
        String deviceBSessionId = deviceBSessionIds.get(0);

        mockMvc.perform(delete("/v1/auth/sessions/" + deviceBSessionId).header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/userinfo").header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", OAuth2TestSupport.CLIENT_ID)
                        .param("refresh_token", secondRefreshToken))
                .andExpect(status().isUnauthorized());

        MvcResult refreshResult = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", OAuth2TestSupport.CLIENT_ID)
                        .param("refresh_token", firstRefreshToken))
                .andExpect(status().isOk())
                .andReturn();
        String refreshBody = refreshResult.getResponse().getContentAsString();
        String rotatedToken = JsonPath.read(refreshBody, "$.access_token");
        String rotatedRefreshToken = JsonPath.read(refreshBody, "$.refresh_token");
        assertThat(rotatedToken).isNotEqualTo(firstToken);

        mockMvc.perform(get("/userinfo").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/userinfo").header("Authorization", "Bearer " + rotatedToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/oauth2/revoke")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", OAuth2TestSupport.CLIENT_ID)
                        .param("token", rotatedRefreshToken)
                        .param("token_type_hint", "refresh_token"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/userinfo").header("Authorization", "Bearer " + rotatedToken))
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
        String email = "delete-me@example.com";
        OAuth2TestSupport.register(mockMvc, email);
        String token = OAuth2TestSupport.obtainAccessToken(mockMvc, email, "password123");

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

        mockMvc.perform(get("/userinfo").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletingAccountAlsoRemovesPackageFilesFromDisk() throws Exception {
        String email = "purge-me@example.com";
        OAuth2TestSupport.register(mockMvc, email);
        String token = OAuth2TestSupport.obtainAccessToken(mockMvc, email, "password123");

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
        String email = "change-password@example.com";
        OAuth2TestSupport.register(mockMvc, email);
        String token = OAuth2TestSupport.obtainAccessToken(mockMvc, email, "password123");

        mockMvc.perform(patch("/v1/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"current_password":"password123","password":"newpassword456","password_confirmation":"newpassword456"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("client_id", OAuth2TestSupport.CLIENT_ID)
                        .param("username", email)
                        .param("password", "password123"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("client_id", OAuth2TestSupport.CLIENT_ID)
                        .param("username", email)
                        .param("password", "newpassword456"))
                .andExpect(status().isOk());
    }
}
