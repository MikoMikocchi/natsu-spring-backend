package io.mikoshift.natsu.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.mikoshift.natsu.TestcontainersConfiguration;
import io.mikoshift.natsu.security.oauth2.NatsuJwtAuthenticationConverter;
import io.mikoshift.natsu.security.oauth2.NatsuOAuth2Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@TestPropertySource(
        properties = {
            "natsu.rate-limit.login.capacity=1000000",
            "natsu.rate-limit.login-email.capacity=1000000",
            "natsu.rate-limit.register.capacity=1000000",
            "natsu.rate-limit.refresh.capacity=1000000",
            "natsu.rate-limit.refresh-token.capacity=1000000",
            "natsu.book-import-recovery.enabled=false"
        })
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthorizationServerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private AuthenticationManager jwtAuthenticationManager;

    @Autowired
    private OAuth2AuthorizationService authorizationService;

    @Autowired
    private NatsuJwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void passwordGrantIssuesJwtWithSidClaim() throws Exception {
        String email = "jwt-claims@example.com";
        OAuth2TestSupport.register(mockMvc, email);

        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("client_id", OAuth2TestSupport.CLIENT_ID)
                        .param("username", email)
                        .param("password", OAuth2TestSupport.DEFAULT_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.refresh_token").exists())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").isNumber())
                .andReturn();

        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.access_token");
        Jwt jwt = jwtDecoder.decode(accessToken);
        assertThat(jwt.getClaimAsString(NatsuOAuth2Claims.SID)).isNotBlank();
        assertThat(jwt.getSubject()).isNotBlank();
        assertThat(jwt.getClaimAsString("email")).isEqualTo(email);

        OAuth2Authorization authorization =
                authorizationService.findById(jwt.getClaimAsString(NatsuOAuth2Claims.SID));
        assertThat(authorization).isNotNull();
        assertThat(jwtAuthenticationConverter.convert(jwt)).isNotNull();
        assertThat(jwtAuthenticationManager.authenticate(new BearerTokenAuthenticationToken(accessToken)))
                .isNotNull();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        mockMvc.perform(get("/v1/auth/sessions").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/userinfo").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void refreshRotatesTokensAndRevokeInvalidatesAccess() throws Exception {
        String email = "refresh-revoke@example.com";
        OAuth2TestSupport.register(mockMvc, email);
        OAuth2TestSupport.TokenPair pair = OAuth2TestSupport.login(mockMvc, email, OAuth2TestSupport.DEFAULT_PASSWORD, "phone");

        MvcResult refreshResult = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", OAuth2TestSupport.CLIENT_ID)
                        .param("refresh_token", pair.refreshToken()))
                .andExpect(status().isOk())
                .andReturn();
        String body = refreshResult.getResponse().getContentAsString();
        String rotatedAccess = JsonPath.read(body, "$.access_token");
        String rotatedRefresh = JsonPath.read(body, "$.refresh_token");
        assertThat(rotatedAccess).isNotEqualTo(pair.accessToken());
        assertThat(rotatedRefresh).isNotEqualTo(pair.refreshToken());

        mockMvc.perform(get("/userinfo").header("Authorization", "Bearer " + rotatedAccess))
                .andExpect(status().isOk());

        mockMvc.perform(post("/oauth2/revoke")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", OAuth2TestSupport.CLIENT_ID)
                        .param("token", rotatedRefresh)
                        .param("token_type_hint", "refresh_token"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/userinfo").header("Authorization", "Bearer " + rotatedAccess))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidCredentialsReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("client_id", OAuth2TestSupport.CLIENT_ID)
                        .param("username", "nobody@example.com")
                        .param("password", "wrong"))
                .andExpect(status().isUnauthorized());
    }
}
