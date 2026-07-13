package io.mikoshift.natsu.service.auth;

import io.mikoshift.natsu.config.NatsuProperties;
import io.mikoshift.natsu.dto.request.ChangePasswordRequest;
import io.mikoshift.natsu.dto.request.ResetPasswordRequest;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.exception.ValidationException;
import io.mikoshift.natsu.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Password change/forgot/reset. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordService {

    private static final Duration RESET_TOKEN_TTL = Duration.ofHours(2);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final JavaMailSender mailSender;
    private final NatsuProperties properties;
    private final Clock clock;

    @Transactional
    public void changePassword(User user, ChangePasswordRequest request) {
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ValidationException.of("current_password", "is invalid");
        }
        if (!request.password().equals(request.passwordConfirmation())) {
            throw ValidationException.of("password_confirmation", "doesn't match Password");
        }
        // user arrives detached (loaded by the auth filter in an earlier, already-closed
        // transaction), so mutating it here does nothing without an explicit save.
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);
    }

    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            String rawToken = generateToken();
            user.setResetPasswordToken(hashToken(rawToken));
            user.setResetPasswordSentAt(clock.instant());
            // Deferred until the transaction actually commits: sending inline here would hold
            // the DB connection open for the full SMTP round-trip, and -- worse -- could mail out
            // a working-looking reset link whose token never made it to the database if the
            // commit subsequently failed. registerSynchronization (rather than calling another
            // @Transactional method on this bean) is used because self-invocation bypasses the
            // proxy and silently runs in the same transaction anyway.
            String userEmail = user.getEmail();
            String userName = user.getName();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendResetEmail(userEmail, userName, rawToken);
                }
            });
        });
    }

    /**
     * Best-effort send: a mail transport failure (e.g. no local SMTP catcher running) is logged and
     * swallowed rather than propagated. The reset token has already been committed regardless, and
     * letting this bubble up would turn an SMTP outage into a signal that distinguishes "account
     * exists" (500) from "account doesn't exist" (200) -- the opposite of what {@link
     * #forgotPassword} is trying to guarantee. The controller always reports success either way.
     */
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
                """.formatted(userName, resetUrl, RESET_TOKEN_TTL.toHours()));
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
                || user.getResetPasswordSentAt().plus(RESET_TOKEN_TTL).isBefore(clock.instant())) {
            throw ValidationException.of("token", "has expired");
        }
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setResetPasswordToken(null);
        user.setResetPasswordSentAt(null);
        tokenService.revokeAll(user);
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
