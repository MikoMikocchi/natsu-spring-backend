package io.mikoshift.natsu.backend.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.mikoshift.natsu.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises each rate limit dimension with tight, test-specific capacities. Runs in its own Spring
 * context (distinct property values from the other integration tests) since MockMvc requests all
 * share one fake remote address, and a category's per-IP bucket therefore persists across every
 * test method that hits that category for the life of the context -- each category below is
 * exercised by exactly one test method so bucket state never needs to be shared or reset between
 * tests.
 */
@TestPropertySource(
    properties = {
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

  @Autowired private MockMvc mockMvc;

  @Test
  void loginIsThrottledPerEmailIndependentlyOfPerIpBucket() throws Exception {
    String email = "per-email-throttle@example.com";

    // Per-email capacity is 2, under the per-IP capacity of 4, so repeated attempts for the
    // same email trip the email bucket first even though the IP bucket has room left.
    for (int i = 0; i < 2; i++) {
      mockMvc
          .perform(
              post("/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                                    {"email":"%s","password":"wrong-password"}
                                    """
                          .formatted(email)))
          .andExpect(status().isUnauthorized());
    }

    mockMvc
        .perform(
            post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"email":"%s","password":"wrong-password"}
                                """
                        .formatted(email)))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.errors.base").exists());

    // A different email from the same IP is unaffected by the exhausted email bucket -- proves
    // the email throttle is independent of the (still-open) per-IP bucket. This is the 4th of
    // 5 calls the login IP bucket sees in this test, so it still succeeds (as far as rate
    // limiting is concerned; the credentials are still wrong).
    mockMvc
        .perform(
            post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"email":"someone-else@example.com","password":"wrong-password"}
                                """))
        .andExpect(status().isUnauthorized());

    // A 5th distinct-email call exactly fills the per-IP bucket (capacity 5)...
    mockMvc
        .perform(
            post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"email":"yet-another@example.com","password":"wrong-password"}
                                """))
        .andExpect(status().isUnauthorized());
    // ...so a 6th overflows it, proving per-IP login throttling still works independently of
    // the per-email dimension.
    mockMvc
        .perform(
            post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"email":"per-ip-overflow@example.com","password":"wrong-password"}
                                """))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.errors.base").exists());
  }

  @Test
  void registerIsThrottledPerIp() throws Exception {
    for (int i = 0; i < 2; i++) {
      mockMvc
          .perform(
              post("/v1/auth/register")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                                    {"name":"Reader","email":"register-throttle-%d@example.com","password":"password123","password_confirmation":"password123"}
                                    """
                          .formatted(i)))
          .andExpect(status().isCreated());
    }

    mockMvc
        .perform(
            post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"name":"Reader","email":"register-throttle-overflow@example.com","password":"password123","password_confirmation":"password123"}
                                """))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.errors.base").exists());
  }

  @Test
  void passwordResetIsThrottledPerIp() throws Exception {
    for (int i = 0; i < 2; i++) {
      mockMvc
          .perform(
              post("/v1/auth/password/forgot")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                                    {"email":"forgot-%d@example.com"}
                                    """
                          .formatted(i)))
          .andExpect(status().isOk());
    }

    mockMvc
        .perform(
            post("/v1/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"email":"forgot-overflow@example.com"}
                                """))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.errors.base").exists());
  }

  @Test
  void refreshIsThrottledPerRefreshTokenAndPerIp() throws Exception {
    // Per-refresh-token capacity is 2, under the per-IP capacity of 4, so repeated attempts
    // with the same bogus refresh token trip the per-token bucket before the per-IP one. (The
    // token doesn't need to be valid -- the per-token throttle check runs before token
    // validation, same as Rack::Attack's throttle running before the controller action.)
    String repeatedToken = "bogus-refresh-token-repeated";
    mockMvc
        .perform(
            post("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"refresh_token":"%s"}
                                """
                        .formatted(repeatedToken)))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"refresh_token":"%s"}
                                """
                        .formatted(repeatedToken)))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"refresh_token":"%s"}
                                """
                        .formatted(repeatedToken)))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.errors.base").exists());

    // One more call with a distinct bogus token fills the remaining per-IP budget (capacity 4,
    // 3 hits already taken above)...
    mockMvc
        .perform(
            post("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"refresh_token":"bogus-refresh-token-distinct-1"}
                                """))
        .andExpect(status().isUnauthorized());
    // ...so the next distinct-token call overflows the per-IP bucket, proving per-IP refresh
    // throttling still works independently of the per-token dimension.
    mockMvc
        .perform(
            post("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"refresh_token":"bogus-refresh-token-distinct-2"}
                                """))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.errors.base").exists());
  }
}
