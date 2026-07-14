package io.mikoshift.natsu.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mikoshift.natsu.dto.response.DeviceSessionResponse;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.exception.NotFoundException;
import io.mikoshift.natsu.security.oauth2.NatsuOAuth2Claims;
import io.mikoshift.natsu.security.oauth2.OAuth2AuthorizationSupport;
import io.mikoshift.natsu.security.oauth2.OAuth2AuthorizationSupport.AuthorizationSession;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;

@ExtendWith(MockitoExtension.class)
class DeviceSessionServiceTest {

    @Mock
    private OAuth2AuthorizationSupport authorizationSupport;

    @Mock
    private OAuth2AuthorizationService authorizationService;

    private DeviceSessionService deviceSessionService;
    private User user;

    @BeforeEach
    void setUp() {
        deviceSessionService = new DeviceSessionService(authorizationSupport, authorizationService);
        user = new User();
        user.setId(1L);
        user.setEmail("reader@example.com");
    }

    @Test
    void listMarksOnlyTheCurrentAuthorizationAsCurrent() {
        AuthorizationSession current = new AuthorizationSession("auth-1", user.getEmail(), Instant.now());
        AuthorizationSession other = new AuthorizationSession("auth-2", user.getEmail(), Instant.now().minusSeconds(3600));
        when(authorizationSupport.findActiveSessionsForUser(user)).thenReturn(List.of(current, other));

        OAuth2Authorization currentAuthorization = OAuth2Authorization.withRegisteredClient(
                        org.springframework.security.oauth2.server.authorization.client.RegisteredClient.withId("id")
                                .clientId("natsu-mobile")
                                .clientName("test")
                                .clientAuthenticationMethod(
                                        org.springframework.security.oauth2.core.ClientAuthenticationMethod.NONE)
                                .authorizationGrantType(
                                        org.springframework.security.oauth2.core.AuthorizationGrantType.REFRESH_TOKEN)
                                .build())
                .id("auth-1")
                .principalName(user.getEmail())
                .authorizationGrantType(
                        org.springframework.security.oauth2.core.AuthorizationGrantType.REFRESH_TOKEN)
                .attribute(NatsuOAuth2Claims.DEVICE_NAME, "This iPhone")
                .build();
        OAuth2Authorization otherAuthorization = OAuth2Authorization.withRegisteredClient(
                        org.springframework.security.oauth2.server.authorization.client.RegisteredClient.withId("id2")
                                .clientId("natsu-mobile")
                                .clientName("test")
                                .clientAuthenticationMethod(
                                        org.springframework.security.oauth2.core.ClientAuthenticationMethod.NONE)
                                .authorizationGrantType(
                                        org.springframework.security.oauth2.core.AuthorizationGrantType.REFRESH_TOKEN)
                                .build())
                .id("auth-2")
                .principalName(user.getEmail())
                .authorizationGrantType(
                        org.springframework.security.oauth2.core.AuthorizationGrantType.REFRESH_TOKEN)
                .attribute(NatsuOAuth2Claims.DEVICE_NAME, "Old iPad")
                .build();
        when(authorizationService.findById("auth-1")).thenReturn(currentAuthorization);
        when(authorizationService.findById("auth-2")).thenReturn(otherAuthorization);

        List<DeviceSessionResponse> sessions = deviceSessionService.list(user, "auth-1");

        assertThat(sessions).hasSize(2);
        assertThat(sessions.stream().filter(DeviceSessionResponse::current).count()).isEqualTo(1);
        assertThat(sessions.stream()
                        .filter(DeviceSessionResponse::current)
                        .findFirst()
                        .orElseThrow()
                        .id())
                .isEqualTo("auth-1");
    }

    @Test
    void listReturnsEmptyWhenTheUserHasNoActiveSessions() {
        when(authorizationSupport.findActiveSessionsForUser(user)).thenReturn(List.of());

        List<DeviceSessionResponse> sessions = deviceSessionService.list(user, "auth-1");

        assertThat(sessions).isEmpty();
    }

    @Test
    void revokeRemovesTheOwnedAuthorization() {
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(
                        org.springframework.security.oauth2.server.authorization.client.RegisteredClient.withId("id")
                                .clientId("natsu-mobile")
                                .clientName("test")
                                .clientAuthenticationMethod(
                                        org.springframework.security.oauth2.core.ClientAuthenticationMethod.NONE)
                                .authorizationGrantType(
                                        org.springframework.security.oauth2.core.AuthorizationGrantType.REFRESH_TOKEN)
                                .build())
                .id("auth-5")
                .principalName(user.getEmail())
                .authorizationGrantType(
                        org.springframework.security.oauth2.core.AuthorizationGrantType.REFRESH_TOKEN)
                .build();
        when(authorizationService.findById("auth-5")).thenReturn(authorization);

        deviceSessionService.revoke(user, "auth-5");

        verify(authorizationService).remove(authorization);
    }

    @Test
    void revokeThrowsWhenTheAuthorizationDoesNotBelongToTheUser() {
        when(authorizationService.findById("auth-99")).thenReturn(null);

        assertThatThrownBy(() -> deviceSessionService.revoke(user, "auth-99")).isInstanceOf(NotFoundException.class);
    }
}
