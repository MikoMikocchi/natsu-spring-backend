package io.mikoshift.natsu.backend.service.auth;

import io.mikoshift.natsu.backend.dto.request.ChangePasswordRequest;
import io.mikoshift.natsu.backend.dto.request.ResetPasswordRequest;
import io.mikoshift.natsu.backend.entity.User;
import io.mikoshift.natsu.backend.exception.ValidationException;
import io.mikoshift.natsu.backend.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Password change/forgot/reset. There is no transactional email pipeline in v1 — the reset token
 * is logged instead of emailed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordService {

    private static final Duration RESET_TOKEN_TTL = Duration.ofHours(2);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

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
            user.setResetPasswordSentAt(Instant.now());
            log.info("Password reset requested for {} (dev-mode, not emailed): token={}", user.getEmail(), rawToken);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.password().equals(request.passwordConfirmation())) {
            throw ValidationException.of("password_confirmation", "doesn't match Password");
        }
        User user = userRepository.findByResetPasswordToken(hashToken(request.token()))
                .orElseThrow(() -> ValidationException.of("token", "is invalid"));
        if (user.getResetPasswordSentAt() == null
                || user.getResetPasswordSentAt().plus(RESET_TOKEN_TTL).isBefore(Instant.now())) {
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
