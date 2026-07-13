package io.mikoshift.natsu.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.mikoshift.natsu.dto.response.DeviceSessionResponse;
import io.mikoshift.natsu.entity.AuthToken;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.exception.NotFoundException;
import io.mikoshift.natsu.repository.AuthTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceSessionServiceTest {

    @Mock
    private AuthTokenRepository authTokenRepository;

    private DeviceSessionService deviceSessionService;
    private User user;

    @BeforeEach
    void setUp() {
        deviceSessionService = new DeviceSessionService(authTokenRepository, Clock.systemUTC());
        user = new User();
        user.setId(1L);
    }

    @Test
    void listMarksOnlyTheCurrentTokenAsCurrent() {
        AuthToken currentToken = new AuthToken();
        currentToken.setId(1L);
        currentToken.setName("This iPhone");
        currentToken.setCreatedAt(Instant.now());

        AuthToken otherToken = new AuthToken();
        otherToken.setId(2L);
        otherToken.setName("Old iPad");
        otherToken.setCreatedAt(Instant.now().minusSeconds(3600));

        when(authTokenRepository.findAllByUserAndRevokedAtIsNullOrderByCreatedAtDesc(user))
                .thenReturn(List.of(currentToken, otherToken));

        List<DeviceSessionResponse> sessions = deviceSessionService.list(user, currentToken);

        assertThat(sessions).hasSize(2);
        DeviceSessionResponse currentResponse =
                sessions.stream().filter(s -> s.id().equals(1L)).findFirst().orElseThrow();
        DeviceSessionResponse otherResponse =
                sessions.stream().filter(s -> s.id().equals(2L)).findFirst().orElseThrow();
        assertThat(currentResponse.current()).isTrue();
        assertThat(otherResponse.current()).isFalse();
    }

    @Test
    void listReturnsEmptyWhenTheUserHasNoActiveSessions() {
        AuthToken currentToken = new AuthToken();
        currentToken.setId(1L);
        when(authTokenRepository.findAllByUserAndRevokedAtIsNullOrderByCreatedAtDesc(user))
                .thenReturn(List.of());

        List<DeviceSessionResponse> sessions = deviceSessionService.list(user, currentToken);

        assertThat(sessions).isEmpty();
    }

    @Test
    void revokeStampsRevokedAtOnTheOwnedToken() {
        AuthToken token = new AuthToken();
        token.setId(5L);
        when(authTokenRepository.findByIdAndUser(5L, user)).thenReturn(Optional.of(token));

        deviceSessionService.revoke(user, 5L);

        assertThat(token.getRevokedAt()).isNotNull();
    }

    @Test
    void revokeThrowsWhenTheTokenDoesNotBelongToTheUser() {
        when(authTokenRepository.findByIdAndUser(99L, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceSessionService.revoke(user, 99L)).isInstanceOf(NotFoundException.class);
    }
}
