package io.mikoshift.natsu.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public final class DocumentSyncTestSupport {

    private DocumentSyncTestSupport() {}

    public static MockHttpServletRequestBuilder syncPost(String token, String idempotencyKey, String body) {
        return post("/v1/documents/sync")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    public static String singleDocumentPayload(
            String id, String title, long updatedAtMs, boolean deleted, String itemIdempotencyKey) {
        return """
                {"documents":[{
                  "id":"%s","title":"%s","source_format":"EPUB","imported_at":1000,
                  "char_count":500,"last_read_char_offset":0,"last_read_section_id":null,
                  "last_read_block_index":0,"last_read_block_char_offset":0,
                  "updated_at_ms":%d,"deleted":%b,"idempotency_key":"%s"
                }]}
                """
                .formatted(id, title, updatedAtMs, deleted, itemIdempotencyKey);
    }

    public static String freshIdempotencyKey() {
        return UUID.randomUUID().toString();
    }
}
