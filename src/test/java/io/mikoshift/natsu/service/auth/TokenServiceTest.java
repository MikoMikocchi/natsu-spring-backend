package io.mikoshift.natsu.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mikoshift.natsu.config.NatsuProperties;
import io.mikoshift.natsu.entity.AuthToken;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.exception.InvalidRefreshTokenException;
import io.mikoshift.natsu.repository.AuthTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private AuthTokenRepository authTokenRepository;

    @Mock
    private NatsuProperties natsuProperties;

    private TokenService tokenService;
    private Clock clock;
    private User user;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        lenient()
                .when(natsuProperties.auth())
                .thenReturn(new NatsuProperties.Auth(
                        Duration.ofHours(1), Duration.ofDays(365), Duration.ofSeconds(30)));
        tokenService = new TokenService(authTokenRepository, natsuProperties, clock);
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
        assertThat(token.getAccessTokenExpiresAt()).isAfter(clock.instant());
        assertThat(token.getRefreshTokenExpiresAt()).isAfter(token.getAccessTokenExpiresAt());
        Duration accessLifetime = Duration.between(clock.instant(), token.getAccessTokenExpiresAt());
        assertThat(accessLifetime).isLessThanOrEqualTo(Duration.ofHours(1).plusSeconds(5));
    }

    @Test
    void resolveAccessTokenReturnsEmptyWhenTokenIsExpired() {
        AuthToken expired = new AuthToken();
        expired.setAccessTokenExpiresAt(clock.instant().minus(1, ChronoUnit.MINUTES));
        when(authTokenRepository.findByAccessTokenAndRevokedAtIsNull("expired-token"))
                .thenReturn(Optional.of(expired));

        Optional<AuthToken> resolved = tokenService.resolveAccessToken("expired-token");

        assertThat(resolved).isEmpty();
    }

    @Test
    void resolveAccessTokenReturnsTokenWhenStillValid() {
        AuthToken valid = new AuthToken();
        valid.setAccessTokenExpiresAt(clock.instant().plus(1, ChronoUnit.DAYS));
        when(authTokenRepository.findByAccessTokenAndRevokedAtIsNull("valid-token"))
                .thenReturn(Optional.of(valid));

        Optional<AuthToken> resolved = tokenService.resolveAccessToken("valid-token");

        assertThat(resolved).contains(valid);
    }

    @Test
    void rotateIssuesNewPairAndRemembersThePreviousRefreshToken() {
        AuthToken current = new AuthToken();
        current.setRefreshToken("old-refresh");
        current.setRefreshTokenExpiresAt(clock.instant().plus(1, ChronoUnit.DAYS));
        when(authTokenRepository.findByRefreshTokenAndRevokedAtIsNull("old-refresh"))
                .thenReturn(Optional.of(current));
        when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthToken rotated = tokenService.rotate("old-refresh");

        assertThat(rotated.getPreviousRefreshToken()).isEqualTo("old-refresh");
        assertThat(rotated.getRefreshToken()).isNotEqualTo("old-refresh");
        assertThat(rotated.getPreviousRefreshTokenExpiresAt()).isAfter(clock.instant());
        verify(authTokenRepository).save(current);
    }

    @Test
    void rotateRejectsAnExpiredRefreshToken() {
        AuthToken current = new AuthToken();
        current.setRefreshToken("old-refresh");
        current.setRefreshTokenExpiresAt(clock.instant().minus(1, ChronoUnit.MINUTES));
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
        alreadyRotated.setPreviousRefreshTokenExpiresAt(clock.instant().plus(10, ChronoUnit.SECONDS));
        alreadyRotated.setRefreshTokenExpiresAt(clock.instant().plus(1, ChronoUnit.DAYS));
        when(authTokenRepository.findByRefreshTokenAndRevokedAtIsNull("stale-refresh"))
                .thenReturn(Optional.empty());
        when(authTokenRepository.findByPreviousRefreshTokenAndRevokedAtIsNull("stale-refresh"))
                .thenReturn(Optional.of(alreadyRotated));

        AuthToken result = tokenService.rotate("stale-refresh");

        assertThat(result).isSameAs(alreadyRotated);
        assertThat(result.getRefreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void rotateRevokesAllSessionsWhenThePreviousRefreshTokenIsReusedAfterTheGraceWindow() {
        // Presenting the previous refresh token once its short grace window has elapsed can no
        // longer be explained by a benign client retry racing the rotation; it signals the token
        // was likely stolen and used ahead of its legitimate owner, so every session for the user
        // is killed instead of silently handing back the current pair.
        AuthToken alreadyRotated = new AuthToken();
        alreadyRotated.setUser(user);
        alreadyRotated.setRefreshToken("new-refresh");
        alreadyRotated.setPreviousRefreshToken("stale-refresh");
        alreadyRotated.setPreviousRefreshTokenExpiresAt(clock.instant().minus(1, ChronoUnit.SECONDS));
        alreadyRotated.setRefreshTokenExpiresAt(clock.instant().plus(1, ChronoUnit.DAYS));
        when(authTokenRepository.findByRefreshTokenAndRevokedAtIsNull("stale-refresh"))
                .thenReturn(Optional.empty());
        when(authTokenRepository.findByPreviousRefreshTokenAndRevokedAtIsNull("stale-refresh"))
                .thenReturn(Optional.of(alreadyRotated));
        when(authTokenRepository.findAllByUserAndRevokedAtIsNullOrderByCreatedAtDesc(user))
                .thenReturn(List.of(alreadyRotated));

        assertThatThrownBy(() -> tokenService.rotate("stale-refresh")).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(alreadyRotated.getRevokedAt()).isNotNull();
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
