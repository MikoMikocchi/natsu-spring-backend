package io.mikoshift.natsu.backend.service.auth;

import io.mikoshift.natsu.backend.entity.AuthToken;
import io.mikoshift.natsu.backend.entity.User;
import io.mikoshift.natsu.backend.exception.InvalidRefreshTokenException;
import io.mikoshift.natsu.backend.repository.AuthTokenRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, rotates, resolves and revokes opaque bearer tokens backed by {@link AuthToken} rows
 * (opaque access/refresh token pair stored in the database, without JWT).
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofDays(30);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(365);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthTokenRepository authTokenRepository;

    @Transactional
    public AuthToken issue(User user, String deviceName) {
        Instant now = Instant.now();
        AuthToken token = new AuthToken();
        token.setUser(user);
        token.setAccessToken(generateOpaqueToken());
        token.setRefreshToken(generateOpaqueToken());
        token.setAccessTokenExpiresAt(now.plus(ACCESS_TOKEN_TTL));
        token.setRefreshTokenExpiresAt(now.plus(REFRESH_TOKEN_TTL));
        token.setName(deviceName);
        return authTokenRepository.save(token);
    }

    @Transactional(readOnly = true)
    public Optional<AuthToken> resolveAccessToken(String accessToken) {
        return authTokenRepository
                .findByAccessTokenAndRevokedAtIsNull(accessToken)
                .filter(token -> token.getAccessTokenExpiresAt().isAfter(Instant.now()));
    }

    /**
     * Rotates the access/refresh pair for the session identified by {@code presentedRefreshToken}. If
     * the presented token is the *previous* refresh token (a client retrying a request that raced a
     * prior rotation), the already-rotated current pair is returned instead of rotating again,
     * avoiding invalidating the client's now-current session.
     */
    @Transactional
    public AuthToken rotate(String presentedRefreshToken) {
        Optional<AuthToken> current = authTokenRepository.findByRefreshTokenAndRevokedAtIsNull(presentedRefreshToken);
        if (current.isPresent()) {
            return doRotate(current.get());
        }
        return authTokenRepository
                .findByPreviousRefreshTokenAndRevokedAtIsNull(presentedRefreshToken)
                .filter(token -> token.getRefreshTokenExpiresAt().isAfter(Instant.now()))
                .orElseThrow(InvalidRefreshTokenException::new);
    }

    private AuthToken doRotate(AuthToken token) {
        if (token.getRefreshTokenExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }
        Instant now = Instant.now();
        token.setPreviousRefreshToken(token.getRefreshToken());
        token.setAccessToken(generateOpaqueToken());
        token.setRefreshToken(generateOpaqueToken());
        token.setAccessTokenExpiresAt(now.plus(ACCESS_TOKEN_TTL));
        token.setRefreshTokenExpiresAt(now.plus(REFRESH_TOKEN_TTL));
        return authTokenRepository.save(token);
    }

    @Transactional
    public void revoke(AuthToken token) {
        token.setRevokedAt(Instant.now());
        authTokenRepository.save(token);
    }

    @Transactional
    public void revokeAll(User user) {
        Instant now = Instant.now();
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
