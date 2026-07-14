package io.mikoshift.natsu.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

@TestPropertySource(
        properties = {
            "natsu.trusted-proxy-cidrs=127.0.0.1/32",
            "natsu.rate-limit.login.capacity=5",
            "natsu.rate-limit.login.window-seconds=60",
            "natsu.rate-limit.login-email.capacity=2",
            "natsu.rate-limit.login-email.window-seconds=60",
            "natsu.rate-limit.register.capacity=2",
            "natsu.rate-limit.register.window-seconds=60",
            "natsu.rate-limit.password-reset.capacity=2",
            "natsu.rate-limit.password-reset.window-seconds=60",
            "natsu.rate-limit.refresh.capacity=4",
            "natsu.rate-limit.refresh.window-seconds=60",
            "natsu.rate-limit.refresh-token.capacity=2",
            "natsu.rate-limit.refresh-token.window-seconds=60"
        })
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginIsThrottledPerEmailIndependentlyOfPerIpBucket() throws Exception {
        String email = "per-email-throttle@example.com";

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/oauth2/token")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("grant_type", "password")
                            .param("client_id", OAuth2TestSupport.CLIENT_ID)
                            .param("username", email)
                            .param("password", "wrong-password"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("client_id", OAuth2TestSupport.CLIENT_ID)
                        .param("username", email)
                        .param("password", "wrong-password"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errors.base").exists());

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("client_id", OAuth2TestSupport.CLIENT_ID)
                        .param("username", "someone-else@example.com")
                        .param("password", "wrong-password"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("client_id", OAuth2TestSupport.CLIENT_ID)
                        .param("username", "yet-another@example.com")
                        .param("password", "wrong-password"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("client_id", OAuth2TestSupport.CLIENT_ID)
                        .param("username", "per-ip-overflow@example.com")
                        .param("password", "wrong-password"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errors.base").exists());
    }

    @Test
    void registerIsThrottledPerIp() throws Exception {
        String clientIp = "203.0.113.10";

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/v1/auth/register")
                            .header("X-Forwarded-For", clientIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Reader","email":"register-throttle-%d@example.com","password":"password123","password_confirmation":"password123"}
                                    """.formatted(i)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/v1/auth/register")
                        .header("X-Forwarded-For", clientIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Reader","email":"register-throttle-overflow@example.com","password":"password123","password_confirmation":"password123"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errors.base").exists());

        mockMvc.perform(post("/v1/auth/register")
                        .header("X-Forwarded-For", "203.0.113.11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Reader","email":"register-throttle-other-client@example.com","password":"password123","password_confirmation":"password123"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void passwordResetIsThrottledPerIp() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/v1/auth/password/forgot")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"forgot-%d@example.com"}
                                    """.formatted(i)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"forgot-overflow@example.com"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errors.base").exists());
    }

    @Test
    void refreshIsThrottledPerRefreshTokenAndPerIp() throws Exception {
        String repeatedToken = "bogus-refresh-token-repeated";
        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", OAuth2TestSupport.CLIENT_ID)
                        .param("refresh_token", repeatedToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", OAuth2TestSupport.CLIENT_ID)
                        .param("refresh_token", repeatedToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", OAuth2TestSupport.CLIENT_ID)
                        .param("refresh_token", repeatedToken))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errors.base").exists());

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", OAuth2TestSupport.CLIENT_ID)
                        .param("refresh_token", "bogus-refresh-token-distinct-1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", OAuth2TestSupport.CLIENT_ID)
                        .param("refresh_token", "bogus-refresh-token-distinct-2"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errors.base").exists());
    }
}
