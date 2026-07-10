package io.mikoshift.natsu.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import io.mikoshift.natsu.backend.TestcontainersConfiguration;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies that "forgot password" actually sends an email (rather than merely logging a token),
 * using an in-memory GreenMail SMTP server standing in for Mailpit/a real mail provider -- same
 * spirit as asserting against {@code ActionMailer::Base.deliveries} on the sibling Rails backend.
 */
// See AuthFlowIntegrationTest for why this override is needed on every integration test class.
@TestPropertySource(
    properties = {
      "natsu.rate-limit.login.capacity=1000000",
      "natsu.rate-limit.login-email.capacity=1000000",
      "natsu.rate-limit.register.capacity=1000000",
      "natsu.rate-limit.password-reset.capacity=1000000",
      "natsu.rate-limit.refresh.capacity=1000000",
      "natsu.rate-limit.refresh-token.capacity=1000000",
      "natsu.password-reset-url-template=https://natsu.example/reset-password?token={token}",
      "natsu.mail-from=noreply@natsu.example",
      "spring.mail.host=localhost",
      "natsu.book-import-recovery.enabled=false"
    })
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PasswordResetEmailIntegrationTest {

  // Started eagerly in a static initializer (rather than via @RegisterExtension) because
  // @DynamicPropertySource methods run while the ApplicationContext is being built, which
  // happens before JUnit5 extensions would normally be started -- the dynamic SMTP port needs
  // to be known first.
  private static final GreenMail greenMail = new GreenMail(ServerSetupTest.SMTP);

  static {
    greenMail.start();
  }

  @Autowired private MockMvc mockMvc;

  @DynamicPropertySource
  static void mailPort(DynamicPropertyRegistry registry) {
    registry.add("spring.mail.port", () -> greenMail.getSmtp().getPort());
  }

  @AfterAll
  static void stopGreenMail() {
    greenMail.stop();
  }

  @Test
  void forgotPasswordSendsResetEmailContainingTheLink() throws Exception {
    String email = "reset-me@example.com";
    mockMvc
        .perform(
            post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"name":"Reset Me","email":"%s","password":"password123","password_confirmation":"password123"}
                                """
                        .formatted(email)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/v1/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"email":"%s"}
                                """
                        .formatted(email)))
        .andExpect(status().isOk());

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(greenMail.getReceivedMessages()).hasSize(1));

    MimeMessage message = greenMail.getReceivedMessages()[0];
    assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly(email);
    assertThat(message.getFrom())
        .extracting(Object::toString)
        .containsExactly("noreply@natsu.example");
    assertThat(message.getSubject()).isEqualTo("Reset your Natsu password");

    String body = (String) message.getContent();
    assertThat(body).contains("https://natsu.example/reset-password?token=");
    assertThat(body).doesNotContain("{token}");
  }

  @Test
  void forgotPasswordSendsNoEmailForUnknownAddress() throws Exception {
    String email = "nobody-here@example.com";
    mockMvc
        .perform(
            post("/v1/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"email":"%s"}
                                """
                        .formatted(email)))
        .andExpect(status().isOk());

    // Give any errant send a moment to land before asserting the negative. Filtered by
    // recipient (rather than an empty-mailbox check) so this test doesn't depend on whether
    // it runs before or after forgotPasswordSendsResetEmailContainingTheLink.
    Thread.sleep(200);
    assertThat(greenMail.getReceivedMessages())
        .noneMatch(message -> containsRecipient(message, email));
  }

  private static boolean containsRecipient(MimeMessage message, String email) {
    try {
      return message.getAllRecipients() != null
          && Arrays.stream(message.getAllRecipients())
              .anyMatch(recipient -> recipient.toString().equalsIgnoreCase(email));
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }
}
