package io.mikoshift.natsu.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.mikoshift.natsu.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
class ReaderSettingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String registerAndGetToken(String email) throws Exception {
        return OAuth2TestSupport.registerAndLogin(mockMvc, email).accessToken();
    }

    @Test
    void firstReadCreatesDefaultsAndSubsequentPatchesAreConflictResolvedByTimestamp() throws Exception {
        String token = registerAndGetToken("settings@example.com");

        // No row exists yet; GET creates and returns the defaults.
        mockMvc.perform(get("/v1/settings/reader").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.font_size_sp").value(16.0))
                .andExpect(jsonPath("$.settings.theme").value("LIGHT"))
                .andExpect(jsonPath("$.settings.updated_at_ms").value(0));

        // A newer update is applied in full.
        mockMvc.perform(patch("/v1/settings/reader")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"theme":"DARK","font_size_sp":20.0,"updated_at_ms":1000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.theme").value("DARK"))
                .andExpect(jsonPath("$.settings.font_size_sp").value(20.0))
                .andExpect(jsonPath("$.settings.updated_at_ms").value(1000));

        // A partial update only touches the fields present in the request.
        mockMvc.perform(patch("/v1/settings/reader")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"furigana_mode":"ALWAYS","updated_at_ms":2000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.furigana_mode").value("ALWAYS"))
                .andExpect(jsonPath("$.settings.theme").value("DARK"))
                .andExpect(jsonPath("$.settings.updated_at_ms").value(2000));

        // A stale update (older timestamp) is silently ignored; stored state comes back unchanged.
        mockMvc.perform(patch("/v1/settings/reader")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"theme":"SEPIA","updated_at_ms":500}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.theme").value("DARK"))
                .andExpect(jsonPath("$.settings.updated_at_ms").value(2000));

        // An update at exactly the stored timestamp is treated as current and applied.
        mockMvc.perform(patch("/v1/settings/reader")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"theme":"SEPIA","updated_at_ms":2000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.theme").value("SEPIA"));
    }

    @Test
    void parallelFirstReadsBothSucceedWithoutConflict() throws Exception {
        String token = registerAndGetToken("settings-race@example.com");

        List<Integer> statuses = readInParallel(token, 8);

        assertThat(statuses).containsOnly(200);
    }

    @Test
    void outOfRangeValuesAreRejected() throws Exception {
        String token = registerAndGetToken("settings-invalid@example.com");

        mockMvc.perform(patch("/v1/settings/reader")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"font_size_sp":5.0,"updated_at_ms":1000}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.font_size_sp").exists());
    }

    @Test
    void settingsAreScopedPerUser() throws Exception {
        String tokenA = registerAndGetToken("settings-a@example.com");
        String tokenB = registerAndGetToken("settings-b@example.com");

        mockMvc.perform(patch("/v1/settings/reader")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"theme":"DARK","updated_at_ms":1000}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/settings/reader").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.theme").value("LIGHT"));
    }

    private List<Integer> readInParallel(String token, int requestCount) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(requestCount)) {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < requestCount; i++) {
                tasks.add(() -> mockMvc.perform(get("/v1/settings/reader").header("Authorization", "Bearer " + token))
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
}
