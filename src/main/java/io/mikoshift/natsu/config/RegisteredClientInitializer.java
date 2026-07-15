package io.mikoshift.natsu.config;

import io.mikoshift.natsu.security.oauth2.CustomAuthorizationGrantTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RegisteredClientInitializer implements ApplicationRunner {

    private static final String REGISTERED_CLIENT_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

    private final RegisteredClientRepository registeredClientRepository;
    private final NatsuProperties natsuProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (registeredClientRepository.findByClientId(natsuProperties.oauth2().clientId()) != null) {
            return;
        }

        RegisteredClient registeredClient = RegisteredClient.withId(REGISTERED_CLIENT_ID)
                .clientId(natsuProperties.oauth2().clientId())
                .clientName("Natsu Mobile")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(CustomAuthorizationGrantTypes.PASSWORD)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope("profile")
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .requireProofKey(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(natsuProperties.auth().accessTokenTtl())
                        .refreshTokenTimeToLive(natsuProperties.auth().refreshTokenTtl())
                        .reuseRefreshTokens(false)
                        .build())
                .build();

        registeredClientRepository.save(registeredClient);
        log.info(
                "Seeded OAuth2 registered client '{}'", natsuProperties.oauth2().clientId());
    }
}
