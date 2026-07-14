package io.mikoshift.natsu.security.oauth2;

import io.mikoshift.natsu.security.NatsuUserDetails;
import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
public class PasswordGrantAuthenticationProvider implements org.springframework.security.authentication.AuthenticationProvider {

    private final AuthenticationManager authenticationManager;
    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;
    private final RegisteredClientRepository registeredClientRepository;

    public PasswordGrantAuthenticationProvider(
            @Lazy AuthenticationManager authenticationManager,
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            RegisteredClientRepository registeredClientRepository) {
        this.authenticationManager = authenticationManager;
        this.authorizationService = authorizationService;
        this.tokenGenerator = tokenGenerator;
        this.registeredClientRepository = registeredClientRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        PasswordGrantAuthenticationToken passwordGrant = (PasswordGrantAuthenticationToken) authentication;

        RegisteredClient registeredClient = registeredClientRepository.findByClientId(passwordGrant.getClientId());
        if (registeredClient == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }

        OAuth2ClientAuthenticationToken clientPrincipal = clientPrincipal(passwordGrant, registeredClient);
        if (!registeredClient.getAuthorizationGrantTypes().contains(NatsuAuthorizationGrantTypes.PASSWORD)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }

        Authentication userAuthentication;
        try {
            userAuthentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(passwordGrant.getUsername(), passwordGrant.getPassword()));
        } catch (AuthenticationException ex) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }

        Set<String> authorizedScopes = new LinkedHashSet<>(registeredClient.getScopes());

        Authentication principalForStorage = UsernamePasswordAuthenticationToken.authenticated(
                userDetails(userAuthentication).getUsername(),
                null,
                userAuthentication.getAuthorities());

        OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.withRegisteredClient(registeredClient)
                .id(UUID.randomUUID().toString())
                .principalName(userDetails(userAuthentication).getUsername())
                .authorizationGrantType(NatsuAuthorizationGrantTypes.PASSWORD)
                .authorizedScopes(authorizedScopes)
                .attribute(Principal.class.getName(), principalForStorage)
                .attribute(NatsuOAuth2Claims.DEVICE_NAME, passwordGrant.getDeviceName());

        OAuth2Authorization preliminaryAuthorization = authorizationBuilder.build();

        OAuth2TokenContext accessTokenContext = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(userAuthentication)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorizedScopes(authorizedScopes)
                .authorization(preliminaryAuthorization)
                .tokenType(org.springframework.security.oauth2.server.authorization.OAuth2TokenType.ACCESS_TOKEN)
                .authorizationGrantType(NatsuAuthorizationGrantTypes.PASSWORD)
                .authorizationGrant(passwordGrant)
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
                    metadata -> metadata.put(
                            OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claimAccessor.getClaims()));
        } else {
            authorizationBuilder.accessToken(accessToken);
        }

        OAuth2TokenContext refreshTokenContext = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(userAuthentication)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorizedScopes(authorizedScopes)
                .authorization(preliminaryAuthorization)
                .tokenType(org.springframework.security.oauth2.server.authorization.OAuth2TokenType.REFRESH_TOKEN)
                .authorizationGrantType(NatsuAuthorizationGrantTypes.PASSWORD)
                .authorizationGrant(passwordGrant)
                .build();

        OAuth2Token generatedRefreshToken = tokenGenerator.generate(refreshTokenContext);
        OAuth2RefreshToken refreshToken = null;
        if (generatedRefreshToken != null) {
            refreshToken = new OAuth2RefreshToken(
                    generatedRefreshToken.getTokenValue(),
                    generatedRefreshToken.getIssuedAt(),
                    generatedRefreshToken.getExpiresAt());
            authorizationBuilder.refreshToken(refreshToken);
        }

        OAuth2Authorization authorization = authorizationBuilder.build();
        authorizationService.save(authorization);

        return new OAuth2AccessTokenAuthenticationToken(registeredClient, clientPrincipal, accessToken, refreshToken);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return PasswordGrantAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private static NatsuUserDetails userDetails(Authentication userAuthentication) {
        return (NatsuUserDetails) userAuthentication.getPrincipal();
    }

    private static OAuth2ClientAuthenticationToken clientPrincipal(
            PasswordGrantAuthenticationToken grant, RegisteredClient registeredClient) {
        Authentication principal = (Authentication) grant.getPrincipal();
        if (principal instanceof OAuth2ClientAuthenticationToken clientAuth && clientAuth.isAuthenticated()) {
            return clientAuth;
        }
        return new OAuth2ClientAuthenticationToken(registeredClient, ClientAuthenticationMethod.NONE, null);
    }
}
