package io.mikoshift.natsu.backend.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mikoshift.natsu.backend.entity.AuthToken;
import io.mikoshift.natsu.backend.entity.User;
import io.mikoshift.natsu.backend.exception.InvalidRefreshTokenException;
import io.mikoshift.natsu.backend.repository.AuthTokenRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private AuthTokenRepository authTokenRepository;

    private TokenService tokenService;
    private User user;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(authTokenRepository);
        user = new User();
        user.setId(1L);
    }

    @Test
    void issueGeneratesDistinctAccessAndRefreshTokensWithExpiries() {
        when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthToken token = tokenService.issue(user, "iPhone");

        assertThat(token.getUser()).isSameAs(user);
        assertThat(token.getName()).isEqualTo("iPhone");
        assertThat(token.getAccessToken()).isNotBlank();
        assertThat(token.getRefreshToken()).isNotBlank();
        assertThat(token.getAccessToken()).isNotEqualTo(token.getRefreshToken());
        assertThat(token.getAccessTokenExpiresAt()).isAfter(Instant.now());
        assertThat(token.getRefreshTokenExpiresAt()).isAfter(token.getAccessTokenExpiresAt());
    }

    @Test
    void resolveAccessTokenReturnsEmptyWhenTokenIsExpired() {
        AuthToken expired = new AuthToken();
        expired.setAccessTokenExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(authTokenRepository.findByAccessTokenAndRevokedAtIsNull("expired-token"))
                .thenReturn(Optional.of(expired));

        Optional<AuthToken> resolved = tokenService.resolveAccessToken("expired-token");

        assertThat(resolved).isEmpty();
    }

    @Test
    void resolveAccessTokenReturnsTokenWhenStillValid() {
        AuthToken valid = new AuthToken();
        valid.setAccessTokenExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(authTokenRepository.findByAccessTokenAndRevokedAtIsNull("valid-token"))
                .thenReturn(Optional.of(valid));

        Optional<AuthToken> resolved = tokenService.resolveAccessToken("valid-token");

        assertThat(resolved).contains(valid);
    }

    @Test
    void rotateIssuesNewPairAndRemembersThePreviousRefreshToken() {
        AuthToken current = new AuthToken();
        current.setRefreshToken("old-refresh");
        current.setRefreshTokenExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(authTokenRepository.findByRefreshTokenAndRevokedAtIsNull("old-refresh"))
                .thenReturn(Optional.of(current));
        when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthToken rotated = tokenService.rotate("old-refresh");

        assertThat(rotated.getPreviousRefreshToken()).isEqualTo("old-refresh");
        assertThat(rotated.getRefreshToken()).isNotEqualTo("old-refresh");
        verify(authTokenRepository).save(current);
    }

    @Test
    void rotateRejectsAnExpiredRefreshToken() {
        AuthToken current = new AuthToken();
        current.setRefreshToken("old-refresh");
        current.setRefreshTokenExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(authTokenRepository.findByRefreshTokenAndRevokedAtIsNull("old-refresh"))
                .thenReturn(Optional.of(current));

        assertThatThrownBy(() -> tokenService.rotate("old-refresh")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotateReturnsTheCurrentPairWhenPresentedWithThePreviousRefreshTokenAgain() {
        // A client retried a request that raced a prior rotation and is presenting the now-stale
        // refresh token; the already-rotated current pair should be handed back rather than rotating
        // again (which would otherwise invalidate the client's now-current session).
        AuthToken alreadyRotated = new AuthToken();
        alreadyRotated.setRefreshToken("new-refresh");
        alreadyRotated.setPreviousRefreshToken("stale-refresh");
        alreadyRotated.setRefreshTokenExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(authTokenRepository.findByRefreshTokenAndRevokedAtIsNull("stale-refresh"))
                .thenReturn(Optional.empty());
        when(authTokenRepository.findByPreviousRefreshTokenAndRevokedAtIsNull("stale-refresh"))
                .thenReturn(Optional.of(alreadyRotated));

        AuthToken result = tokenService.rotate("stale-refresh");

        assertThat(result).isSameAs(alreadyRotated);
        assertThat(result.getRefreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void rotateRejectsATokenThatMatchesNeitherCurrentNorPreviousRefreshToken() {
        when(authTokenRepository.findByRefreshTokenAndRevokedAtIsNull("unknown"))
                .thenReturn(Optional.empty());
        when(authTokenRepository.findByPreviousRefreshTokenAndRevokedAtIsNull("unknown"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.rotate("unknown")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void revokeStampsRevokedAtAndPersists() {
        AuthToken token = new AuthToken();
        when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(inv -> inv.getArgument(0));

        tokenService.revoke(token);

        assertThat(token.getRevokedAt()).isNotNull();
        verify(authTokenRepository).save(token);
    }

    @Test
    void revokeAllStampsEveryActiveSessionForTheUser() {
        AuthToken first = new AuthToken();
        AuthToken second = new AuthToken();
        when(authTokenRepository.findAllByUserAndRevokedAtIsNullOrderByCreatedAtDesc(user))
                .thenReturn(List.of(first, second));

        tokenService.revokeAll(user);

        assertThat(first.getRevokedAt()).isNotNull();
        assertThat(second.getRevokedAt()).isNotNull();
    }
}
