package io.mikoshift.natsu.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

public final class OAuth2TestSupport {

    public static final String CLIENT_ID = "natsu-mobile";
    public static final String DEFAULT_PASSWORD = "password123";

    private OAuth2TestSupport() {}

    public static void register(MockMvc mockMvc, String email) throws Exception {
        register(mockMvc, email, "Reader", DEFAULT_PASSWORD);
    }

    public static void register(MockMvc mockMvc, String email, String name, String password) throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","email":"%s","password":"%s","password_confirmation":"%s"}
                                """.formatted(name, email, password, password)))
                .andExpect(status().isCreated());
    }

    public static TokenPair login(MockMvc mockMvc, String email, String password, String userAgent) throws Exception {
        var request = post("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "password")
                .param("client_id", CLIENT_ID)
                .param("username", email)
                .param("password", password)
                .header("User-Agent", userAgent);
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        SecurityContextHolder.clearContext();
        String body = result.getResponse().getContentAsString();
        return new TokenPair(JsonPath.read(body, "$.access_token"), JsonPath.read(body, "$.refresh_token"));
    }

    public static String obtainAccessToken(MockMvc mockMvc, String email, String password) throws Exception {
        return obtainAccessToken(mockMvc, email, password, null);
    }

    public static String obtainAccessToken(MockMvc mockMvc, String email, String password, String userAgent)
            throws Exception {
        var request = post("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "password")
                .param("client_id", CLIENT_ID)
                .param("username", email)
                .param("password", password);
        if (userAgent != null) {
            request = request.header("User-Agent", userAgent);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        SecurityContextHolder.clearContext();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.access_token");
    }

    public static String obtainRefreshToken(MockMvc mockMvc, String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("client_id", CLIENT_ID)
                        .param("username", email)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn();
        SecurityContextHolder.clearContext();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.refresh_token");
    }

    public static TokenPair registerAndLogin(MockMvc mockMvc, String email) throws Exception {
        register(mockMvc, email);
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("client_id", CLIENT_ID)
                        .param("username", email)
                        .param("password", DEFAULT_PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        SecurityContextHolder.clearContext();
        String body = result.getResponse().getContentAsString();
        return new TokenPair(JsonPath.read(body, "$.access_token"), JsonPath.read(body, "$.refresh_token"));
    }

    public static String refreshTokens(MockMvc mockMvc, String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", CLIENT_ID)
                        .param("refresh_token", refreshToken))
                .andExpect(status().isOk())
                .andReturn();
        SecurityContextHolder.clearContext();
        String body = result.getResponse().getContentAsString();
        return JsonPath.read(body, "$.refresh_token");
    }

    public static String refreshAccessToken(MockMvc mockMvc, String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", CLIENT_ID)
                        .param("refresh_token", refreshToken))
                .andExpect(status().isOk())
                .andReturn();
        SecurityContextHolder.clearContext();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.access_token");
    }

    public record TokenPair(String accessToken, String refreshToken) {}
}
