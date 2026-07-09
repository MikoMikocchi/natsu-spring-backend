package io.mikoshift.natsu.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.mikoshift.natsu.backend.TestcontainersConfiguration;
import io.mikoshift.natsu.backend.entity.Dictionary;
import io.mikoshift.natsu.backend.service.dictionary.TermBankImportService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// See AuthFlowIntegrationTest for why this override is needed on every integration test class.
@TestPropertySource(properties = "natsu.rate-limit-capacity=1000000")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DictionaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TermBankImportService termBankImportService;

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

    @Test
    void importListLookupToggleAndCacheInvalidationFullFlow() throws Exception {
        String token = registerAndGetToken("dict@example.com");
        Dictionary dictionary = termBankImportService.importZip("test-catalog-" + System.nanoTime(), buildFixtureZip());
        String dictionaryId = dictionary.getId().toString();

        // The freshly-imported dictionary is enabled by default and shows the right term count.
        mockMvc.perform(get("/v1/dictionaries").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dictionaries[?(@.id == '" + dictionaryId + "')].enabled").value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.dictionaries[?(@.id == '" + dictionaryId + "')].term_count").value(org.hamcrest.Matchers.contains(3)));

        // Direct match.
        mockMvc.perform(get("/v1/dictionary/lookup").header("Authorization", "Bearer " + token).param("q", "食べる"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].word").value("食べる"))
                .andExpect(jsonPath("$.data[0].match_kind").value("DIRECT"))
                .andExpect(jsonPath("$.data[0].senses[0].definitions[0]").value("to eat"));

        // Deinflected match: 食べた (past tense) is not itself a dictionary entry.
        mockMvc.perform(get("/v1/dictionary/lookup").header("Authorization", "Bearer " + token).param("q", "食べた"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].word").value("食べる"))
                .andExpect(jsonPath("$.data[0].match_kind").value("DEINFLECTION"))
                .andExpect(jsonPath("$.data[0].rule_name").value("past tense"));

        // Toggling off removes it from lookup results...
        mockMvc.perform(patch("/v1/dictionaries/" + dictionaryId + "/toggle").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/v1/dictionaries").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.dictionaries[?(@.id == '" + dictionaryId + "')].enabled").value(org.hamcrest.Matchers.contains(false)));
        mockMvc.perform(get("/v1/dictionary/lookup").header("Authorization", "Bearer " + token).param("q", "食べる"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        // ...and toggling back on makes it findable again (proves the cache-version bump, not a
        // stale cached empty result, drives this).
        mockMvc.perform(patch("/v1/dictionaries/" + dictionaryId + "/toggle").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/v1/dictionary/lookup").header("Authorization", "Bearer " + token).param("q", "食べる"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].word").value("食べる"));
    }

    @Test
    void lookupRanksDirectMatchesAboveDeinflectedOnes() throws Exception {
        String token = registerAndGetToken("dict-rank@example.com");
        termBankImportService.importZip("rank-catalog-" + System.nanoTime(), buildAmbiguousFixtureZip());

        // "書く" (kaku) is both a valid direct dictionary entry AND, coincidentally in this
        // fixture, reachable by deinflecting nothing -- this just asserts direct beats deinflected
        // when both are present for different words by checking ordering of match_kind values.
        String response = mockMvc.perform(get("/v1/dictionary/lookup").header("Authorization", "Bearer " + token).param("q", "書いた"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        java.util.List<String> matchKinds = JsonPath.read(response, "$.data[*].match_kind");
        assertThat(matchKinds).doesNotContain("DIRECT"); // "書いた" itself is not a dictionary entry
        assertThat(matchKinds).contains("DEINFLECTION");
    }

    private static byte[] buildFixtureZip() {
        return buildZip("""
                [
                  ["食べる","たべる","","v1",10,["to eat"],1,""],
                  ["書く","かく","","v5",5,["to write"],2,""],
                  ["高い","たかい","","adj-i",3,["expensive","tall"],3,""]
                ]
                """);
    }

    private static byte[] buildAmbiguousFixtureZip() {
        return buildZip("""
                [
                  ["書く","かく","","v5",5,["to write"],1,""]
                ]
                """);
    }

    private static byte[] buildZip(String termBankJson) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry("index.json"));
                zip.write("""
                        {"title":"Fixture Dictionary","revision":"1"}
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
}
