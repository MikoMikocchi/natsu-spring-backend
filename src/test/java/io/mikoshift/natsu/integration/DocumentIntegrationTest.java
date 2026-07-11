package io.mikoshift.natsu.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.mikoshift.natsu.TestcontainersConfiguration;
import java.util.UUID;
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
class DocumentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    private String syncDoc(String token, String id, String title, long updatedAtMs, boolean deleted) throws Exception {
        return mockMvc.perform(post("/v1/documents/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documents":[{
                                  "id":"%s","title":"%s","source_format":"EPUB","imported_at":1000,
                                  "char_count":500,"last_read_char_offset":0,"last_read_section_id":null,
                                  "last_read_block_index":0,"last_read_block_char_offset":0,
                                  "updated_at_ms":%d,"deleted":%b
                                }]}
                                """.formatted(id, title, updatedAtMs, deleted)))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void syncCreatesUpdatesAndSoftDeletesWithConflictResolution() throws Exception {
        String token = registerAndGetToken("docs@example.com");
        String id = UUID.randomUUID().toString();

        // Creating via sync doesn't require any prior state.
        String created = syncDoc(token, id, "First Title", 1000, false);
        assertThat((String) JsonPath.read(created, "$.documents[0].title")).isEqualTo("First Title");
        assertThat((Integer) JsonPath.read(created, "$.documents[0].updated_at_ms"))
                .isEqualTo(1000);

        // A stale update (older timestamp) is a no-op.
        String stale = syncDoc(token, id, "Stale Title", 500, false);
        assertThat((String) JsonPath.read(stale, "$.documents[0].title")).isEqualTo("First Title");

        // A newer update is applied.
        String updated = syncDoc(token, id, "Second Title", 2000, false);
        assertThat((String) JsonPath.read(updated, "$.documents[0].title")).isEqualTo("Second Title");

        // Fetching the single document reflects the latest state.
        mockMvc.perform(get("/v1/documents/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.title").value("Second Title"))
                .andExpect(jsonPath("$.document.deleted").value(false));

        // Soft-deleting via sync is just another timestamp-merged update.
        syncDoc(token, id, "Second Title", 3000, true);
        mockMvc.perform(get("/v1/documents/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.deleted").value(true));
    }

    @Test
    void indexReturnsOnlyChangesSinceGivenTimestampIncludingDeletions() throws Exception {
        String token = registerAndGetToken("docs-since@example.com");
        String oldId = UUID.randomUUID().toString();
        String newId = UUID.randomUUID().toString();
        String deletedId = UUID.randomUUID().toString();

        syncDoc(token, oldId, "Old Doc", 1000, false);
        syncDoc(token, newId, "New Doc", 5000, false);
        syncDoc(token, deletedId, "Deleted Doc", 6000, false);
        syncDoc(token, deletedId, "Deleted Doc", 7000, true);

        String response = mockMvc.perform(get("/v1/documents")
                        .header("Authorization", "Bearer " + token)
                        .param("since", "4000"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        java.util.List<String> ids = JsonPath.read(response, "$.documents[*].id");
        assertThat(ids).containsExactlyInAnyOrder(newId, deletedId);
    }

    @Test
    void unknownDocumentReturns404WithErrorShape() throws Exception {
        String token = registerAndGetToken("docs-404@example.com");
        mockMvc.perform(get("/v1/documents/" + UUID.randomUUID()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors.base").exists());
    }

    @Test
    void documentsAreScopedPerUser() throws Exception {
        String tokenA = registerAndGetToken("docs-a@example.com");
        String tokenB = registerAndGetToken("docs-b@example.com");
        String id = UUID.randomUUID().toString();

        syncDoc(tokenA, id, "Owned By A", 1000, false);

        mockMvc.perform(get("/v1/documents/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchMatchesTitleAndReturnsSnippet() throws Exception {
        String token = registerAndGetToken("docs-search@example.com");
        syncDoc(token, UUID.randomUUID().toString(), "The Great Gatsby", 1000, false);
        syncDoc(token, UUID.randomUUID().toString(), "Unrelated Book", 1000, false);

        mockMvc.perform(get("/v1/documents/search")
                        .header("Authorization", "Bearer " + token)
                        .param("q", "gatsby"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].title").value("The Great Gatsby"))
                .andExpect(jsonPath("$.results[0].matches[0].snippet")
                        .value(org.hamcrest.Matchers.containsStringIgnoringCase("gatsby")));
    }

    @Test
    void syncRejectsBatchesLargerThan100() throws Exception {
        String token = registerAndGetToken("docs-batch@example.com");
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            if (i > 0) {
                items.append(",");
            }
            items.append("""
                    {"id":"%s","title":"Doc %d","source_format":"EPUB","imported_at":1000,
                     "char_count":1,"last_read_char_offset":0,"last_read_block_index":0,
                     "last_read_block_char_offset":0,"updated_at_ms":1000,"deleted":false}
                    """.formatted(UUID.randomUUID(), i));
        }
        mockMvc.perform(post("/v1/documents/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documents\":[" + items + "]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.documents").exists());
    }
}
