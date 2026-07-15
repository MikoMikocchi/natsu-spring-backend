package io.mikoshift.natsu.service.auth;

import io.mikoshift.natsu.config.NatsuProperties;
import io.mikoshift.natsu.dto.request.ChangePasswordRequest;
import io.mikoshift.natsu.dto.request.ResetPasswordRequest;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.exception.ValidationException;
import io.mikoshift.natsu.repository.UserRepository;
import io.mikoshift.natsu.security.oauth2.OAuth2AuthorizationSupport;
import io.mikoshift.natsu.util.TransactionHooks;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OAuth2AuthorizationSupport authorizationSupport;
    private final JavaMailSender mailSender;
    private final NatsuProperties properties;
    private final Clock clock;

    @Transactional
    public void changePassword(User authenticatedUser, ChangePasswordRequest request) {
        User user = userRepository
                .findById(authenticatedUser.getId())
                .orElseThrow(() -> ValidationException.of("base", "User not found"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ValidationException.of("current_password", "is invalid");
        }
        if (!request.password().equals(request.passwordConfirmation())) {
            throw ValidationException.of("password_confirmation", "doesn't match Password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        authorizationSupport.revokeAllForUser(user);
    }

    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            String rawToken = generateToken();
            user.setResetPasswordToken(hashToken(rawToken));
            user.setResetPasswordSentAt(clock.instant());
            String userEmail = user.getEmail();
            String userName = user.getName();
            TransactionHooks.afterCommit(() -> sendResetEmail(userEmail, userName, rawToken));
        });
    }

    private void sendResetEmail(String userEmail, String userName, String rawToken) {
        String resetUrl = properties.passwordResetUrlTemplate().replace("{token}", rawToken);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(userEmail);
        message.setSubject("Reset your Natsu password");
        message.setText("""
                Hello %s,

                Someone requested a password reset for your Natsu account.

                Reset your password by opening the link below:
                %s

                This link will expire in %d hours.

                If you did not request a password reset, you can safely ignore this email.

                — The Natsu Team
                """.formatted(
                        userName, resetUrl, properties.auth().resetTokenTtl().toHours()));
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("Failed to send password reset email to {}", userEmail, e);
        }
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.password().equals(request.passwordConfirmation())) {
            throw ValidationException.of("password_confirmation", "doesn't match Password");
        }
        User user = userRepository
                .findByResetPasswordToken(hashToken(request.token()))
                .orElseThrow(() -> ValidationException.of("token", "is invalid"));
        if (user.getResetPasswordSentAt() == null
                || user.getResetPasswordSentAt()
                        .plus(properties.auth().resetTokenTtl())
                        .isBefore(clock.instant())) {
            throw ValidationException.of("token", "has expired");
        }
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setResetPasswordToken(null);
        user.setResetPasswordSentAt(null);
        authorizationSupport.revokeAllForUser(user);
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
