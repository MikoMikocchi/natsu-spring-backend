package io.mikoshift.natsu.service.auth;

import io.mikoshift.natsu.config.NatsuProperties;
import io.mikoshift.natsu.dto.response.TokenResponse;
import io.mikoshift.natsu.security.AppUserDetails;
import io.mikoshift.natsu.security.oauth2.CustomAuthorizationGrantTypes;
import io.mikoshift.natsu.security.oauth2.OAuth2Claims;
import java.security.Principal;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenIssuanceService {

    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;
    private final RegisteredClientRepository registeredClientRepository;
    private final AuthorizationServerSettings authorizationServerSettings;
    private final NatsuProperties natsuProperties;

    public TokenResponse issueTokens(Authentication userAuthentication, String deviceName) {
        RegisteredClient registeredClient = registeredClientRepository.findByClientId(
                natsuProperties.oauth2().clientId());
        if (registeredClient == null) {
            throw new IllegalStateException("Registered OAuth2 client is not configured");
        }

        AppUserDetails userDetails = (AppUserDetails) userAuthentication.getPrincipal();
        Set<String> authorizedScopes = new LinkedHashSet<>(registeredClient.getScopes());
        Authentication principalForStorage = authenticatedPrincipal(userDetails, userAuthentication.getAuthorities());
        Authentication authorizationGrant = principalForStorage;

        AuthorizationServerContextHolder.setContext(new SimpleAuthorizationServerContext(authorizationServerSettings));
        try {
            OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.withRegisteredClient(
                            registeredClient)
                    .id(UUID.randomUUID().toString())
                    .principalName(userDetails.getUsername())
                    .authorizationGrantType(CustomAuthorizationGrantTypes.FIRST_PARTY)
                    .authorizedScopes(authorizedScopes)
                    .attribute(Principal.class.getName(), principalForStorage)
                    .attribute(OAuth2Claims.DEVICE_NAME, deviceName);

            OAuth2Authorization preliminaryAuthorization = authorizationBuilder.build();

            OAuth2AccessToken accessToken = generateAccessToken(
                    registeredClient,
                    userAuthentication,
                    authorizedScopes,
                    preliminaryAuthorization,
                    authorizationGrant,
                    authorizationBuilder);
            OAuth2RefreshToken refreshToken = generateRefreshToken(
                    registeredClient,
                    userAuthentication,
                    authorizedScopes,
                    preliminaryAuthorization,
                    authorizationGrant,
                    authorizationBuilder);

            authorizationService.save(authorizationBuilder.build());

            long expiresIn = Duration.between(accessToken.getIssuedAt(), accessToken.getExpiresAt())
                    .getSeconds();
            return new TokenResponse(
                    accessToken.getTokenValue(),
                    refreshToken != null ? refreshToken.getTokenValue() : null,
                    OAuth2AccessToken.TokenType.BEARER.getValue(),
                    expiresIn);
        } finally {
            AuthorizationServerContextHolder.resetContext();
        }
    }

    private OAuth2AccessToken generateAccessToken(
            RegisteredClient registeredClient,
            Authentication userAuthentication,
            Set<String> authorizedScopes,
            OAuth2Authorization preliminaryAuthorization,
            Authentication authorizationGrant,
            OAuth2Authorization.Builder authorizationBuilder) {
        OAuth2TokenContext accessTokenContext = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(userAuthentication)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorizedScopes(authorizedScopes)
                .authorization(preliminaryAuthorization)
                .tokenType(org.springframework.security.oauth2.server.authorization.OAuth2TokenType.ACCESS_TOKEN)
                .authorizationGrantType(CustomAuthorizationGrantTypes.FIRST_PARTY)
                .authorizationGrant(authorizationGrant)
                .build();

        OAuth2Token generatedAccessToken = tokenGenerator.generate(accessTokenContext);
        if (generatedAccessToken == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, "Failed to generate access token", null));
        }

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                generatedAccessToken.getTokenValue(),
                generatedAccessToken.getIssuedAt(),
                generatedAccessToken.getExpiresAt(),
                authorizedScopes);
        if (generatedAccessToken instanceof ClaimAccessor claimAccessor) {
            authorizationBuilder.token(
                    accessToken,
                    metadata ->
                            metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claimAccessor.getClaims()));
        } else {
            authorizationBuilder.accessToken(accessToken);
        }
        return accessToken;
    }

    private OAuth2RefreshToken generateRefreshToken(
            RegisteredClient registeredClient,
            Authentication userAuthentication,
            Set<String> authorizedScopes,
            OAuth2Authorization preliminaryAuthorization,
            Authentication authorizationGrant,
            OAuth2Authorization.Builder authorizationBuilder) {
        OAuth2TokenContext refreshTokenContext = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(userAuthentication)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorizedScopes(authorizedScopes)
                .authorization(preliminaryAuthorization)
                .tokenType(org.springframework.security.oauth2.server.authorization.OAuth2TokenType.REFRESH_TOKEN)
                .authorizationGrantType(CustomAuthorizationGrantTypes.FIRST_PARTY)
                .authorizationGrant(authorizationGrant)
                .build();

        OAuth2Token generatedRefreshToken = tokenGenerator.generate(refreshTokenContext);
        if (generatedRefreshToken == null) {
            return null;
        }

        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
                generatedRefreshToken.getTokenValue(),
                generatedRefreshToken.getIssuedAt(),
                generatedRefreshToken.getExpiresAt());
        authorizationBuilder.refreshToken(refreshToken);
        return refreshToken;
    }

    private static Authentication authenticatedPrincipal(
            AppUserDetails userDetails, java.util.Collection<? extends GrantedAuthority> authorities) {
        return org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                userDetails.getUsername(), null, authorities);
    }

    private record SimpleAuthorizationServerContext(AuthorizationServerSettings settings)
            implements AuthorizationServerContext {

        @Override
        public String getIssuer() {
            return settings.getIssuer();
        }

        @Override
        public AuthorizationServerSettings getAuthorizationServerSettings() {
            return settings;
        }
    }
}
