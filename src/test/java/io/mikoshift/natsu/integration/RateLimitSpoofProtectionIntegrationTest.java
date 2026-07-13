package io.mikoshift.natsu.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.mikoshift.natsu.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies that spoofed {@code X-Forwarded-For} values cannot bypass per-IP rate limiting when no
 * trusted proxy CIDRs are configured.
 */
@TestPropertySource(
        properties = {
            "natsu.rate-limit.register.capacity=2",
            "natsu.rate-limit.register.window-seconds=60"
        })
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitSpoofProtectionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void spoofedForwardedForCannotBypassPerIpRegisterThrottle() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/v1/auth/register")
                            .header("X-Forwarded-For", "203.0.113.%d".formatted(10 + i))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Reader","email":"spoof-throttle-%d@example.com","password":"password123","password_confirmation":"password123"}
                                    """.formatted(i)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/v1/auth/register")
                        .header("X-Forwarded-For", "203.0.113.99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Reader","email":"spoof-throttle-overflow@example.com","password":"password123","password_confirmation":"password123"}
                                """))
                .andExpect(status().isTooManyRequests());
    }
}
