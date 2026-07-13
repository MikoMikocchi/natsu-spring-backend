package io.mikoshift.natsu.service.auth;

import io.mikoshift.natsu.config.NatsuProperties;
import io.mikoshift.natsu.entity.AuthToken;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.exception.InvalidRefreshTokenException;
import io.mikoshift.natsu.repository.AuthTokenRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, rotates, resolves and revokes opaque bearer tokens backed by {@link AuthToken} rows
 * (opaque access/refresh token pair stored in the database, without JWT).
 *
 * <p>Access tokens are intentionally short-lived ({@link NatsuProperties.Auth#accessTokenTtl()})
 * so clients refresh regularly and {@link #rotate} can run rotation plus reuse detection. A stolen
 * access token otherwise never hits {@code /refresh}, leaving the refresh-token machinery idle.
 * Immediate revocation still works on every API call via {@link #resolveAccessToken}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthTokenRepository authTokenRepository;
    private final NatsuProperties natsuProperties;
    private final Clock clock;

    @Transactional
    public AuthToken issue(User user, String deviceName) {
        Instant now = clock.instant();
        AuthToken token = new AuthToken();
        token.setUser(user);
        token.setAccessToken(generateOpaqueToken());
        token.setRefreshToken(generateOpaqueToken());
        token.setAccessTokenExpiresAt(now.plus(natsuProperties.auth().accessTokenTtl()));
        token.setRefreshTokenExpiresAt(now.plus(natsuProperties.auth().refreshTokenTtl()));
        token.setName(deviceName);
        return authTokenRepository.save(token);
    }

    @Transactional(readOnly = true)
    public Optional<AuthToken> resolveAccessToken(String accessToken) {
        return authTokenRepository
                .findByAccessTokenAndRevokedAtIsNull(accessToken)
                .filter(token -> token.getAccessTokenExpiresAt().isAfter(clock.instant()));
    }

    /**
     * Rotates the access/refresh pair for the session identified by {@code presentedRefreshToken}. If
     * the presented token is the *previous* refresh token, presented within the configured grace
     * window ({@link NatsuProperties.Auth#refreshTokenGraceWindow()}) of the rotation that
     * superseded it, this is treated as a client
     * retrying a request that raced that rotation: the already-rotated current pair is returned
     * instead of rotating again, avoiding invalidating the client's now-current session.
     *
     * <p>Presenting the previous token *after* the grace window is refresh-token reuse: either the
     * client waited far longer than any request retry would, or someone else obtained the token
     * ahead of its legitimate holder. Since that can't be told apart from theft, every session for
     * the user is revoked rather than silently handing out the current pair.
     */
    @Transactional
    public AuthToken rotate(String presentedRefreshToken) {
        Optional<AuthToken> current = authTokenRepository.findByRefreshTokenAndRevokedAtIsNull(presentedRefreshToken);
        if (current.isPresent()) {
            return doRotate(current.get());
        }

        AuthToken previous = authTokenRepository
                .findByPreviousRefreshTokenAndRevokedAtIsNull(presentedRefreshToken)
                .orElseThrow(InvalidRefreshTokenException::new);
        if (previous.getPreviousRefreshTokenExpiresAt() == null
                || previous.getPreviousRefreshTokenExpiresAt().isBefore(clock.instant())) {
            log.warn("Refresh token reuse detected for user {}; revoking all sessions", previous.getUser().getId());
            revokeAll(previous.getUser());
            throw new InvalidRefreshTokenException();
        }
        return previous;
    }

    private AuthToken doRotate(AuthToken token) {
        if (token.getRefreshTokenExpiresAt().isBefore(clock.instant())) {
            throw new InvalidRefreshTokenException();
        }
        Instant now = clock.instant();
        token.setPreviousRefreshToken(token.getRefreshToken());
        token.setPreviousRefreshTokenExpiresAt(now.plus(natsuProperties.auth().refreshTokenGraceWindow()));
        token.setAccessToken(generateOpaqueToken());
        token.setRefreshToken(generateOpaqueToken());
        token.setAccessTokenExpiresAt(now.plus(natsuProperties.auth().accessTokenTtl()));
        token.setRefreshTokenExpiresAt(now.plus(natsuProperties.auth().refreshTokenTtl()));
        return authTokenRepository.save(token);
    }

    @Transactional
    public void revoke(AuthToken token) {
        token.setRevokedAt(clock.instant());
        authTokenRepository.save(token);
    }

    @Transactional
    public void revokeAll(User user) {
        Instant now = clock.instant();
        authTokenRepository
                .findAllByUserAndRevokedAtIsNullOrderByCreatedAtDesc(user)
                .forEach(token -> token.setRevokedAt(now));
    }

    private static String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
