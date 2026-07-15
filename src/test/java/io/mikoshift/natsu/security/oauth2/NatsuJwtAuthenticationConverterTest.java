package io.mikoshift.natsu.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

@ExtendWith(MockitoExtension.class)
class NatsuJwtAuthenticationConverterTest {

    @Mock
    private OAuth2AuthorizationSupport authorizationSupport;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NatsuJwtAuthenticationConverter converter;

    @Test
    void convertsActiveJwtToUserPrincipal() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "42")
                .claim(NatsuOAuth2Claims.SID, "auth-1")
                .build();

        User user = new User();
        user.setId(42L);
        user.setEmail("reader@example.com");

        org.mockito.Mockito.when(authorizationSupport.isActive("auth-1", "token"))
                .thenReturn(true);
        org.mockito.Mockito.when(userRepository.findById(42L)).thenReturn(java.util.Optional.of(user));

        UsernamePasswordAuthenticationToken authentication =
                (UsernamePasswordAuthenticationToken) converter.convert(jwt);

        assertThat(authentication.getPrincipal()).isEqualTo(user);
        assertThat(authentication.getDetails()).isEqualTo(new NatsuAuthenticationDetails("auth-1"));
    }

    @Test
    void rejectsRevokedSession() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "42")
                .claim(NatsuOAuth2Claims.SID, "auth-1")
                .build();

        org.mockito.Mockito.when(authorizationSupport.isActive("auth-1", "token"))
                .thenReturn(false);

        assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class);
    }
}
