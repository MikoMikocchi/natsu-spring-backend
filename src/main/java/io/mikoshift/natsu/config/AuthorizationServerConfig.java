package io.mikoshift.natsu.config;

import io.mikoshift.natsu.security.RateLimitFilter;
import io.mikoshift.natsu.security.oauth2.DeviceNameRequestFilter;
import io.mikoshift.natsu.security.oauth2.OAuth2TokenRateLimitFilter;
import io.mikoshift.natsu.security.oauth2.PasswordGrantAuthenticationConverter;
import io.mikoshift.natsu.security.oauth2.PasswordGrantAuthenticationProvider;
import io.mikoshift.natsu.security.oauth2.PublicClientAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@RequiredArgsConstructor
public class AuthorizationServerConfig {

    private final RateLimitFilter rateLimitFilter;
    private final OAuth2TokenRateLimitFilter oauth2TokenRateLimitFilter;
    private final DeviceNameRequestFilter deviceNameRequestFilter;
    private final PublicClientAuthenticationFilter publicClientAuthenticationFilter;

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            RegisteredClientRepository registeredClientRepository,
            OAuth2AuthorizationService authorizationService,
            OAuth2AuthorizationConsentService authorizationConsentService,
            AuthorizationServerSettings authorizationServerSettings,
            OAuth2TokenGenerator<?> tokenGenerator,
            PasswordGrantAuthenticationConverter passwordGrantAuthenticationConverter,
            PasswordGrantAuthenticationProvider passwordGrantAuthenticationProvider)
            throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();

        RequestMatcher userInfoMatcher = request -> "/userinfo".equals(request.getRequestURI());

        http.securityMatcher(new AndRequestMatcher(
                        authorizationServerConfigurer.getEndpointsMatcher(),
                        new NegatedRequestMatcher(userInfoMatcher)))
                .with(
                        authorizationServerConfigurer,
                        authorizationServer -> authorizationServer
                                .registeredClientRepository(registeredClientRepository)
                                .authorizationService(authorizationService)
                                .authorizationConsentService(authorizationConsentService)
                                .authorizationServerSettings(authorizationServerSettings)
                                .tokenGenerator(tokenGenerator)
                                .tokenEndpoint(tokenEndpoint -> tokenEndpoint
                                        .accessTokenRequestConverter(passwordGrantAuthenticationConverter)
                                        .authenticationProvider(passwordGrantAuthenticationProvider)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/oauth2/token", "/oauth2/revoke", "/oauth2/jwks", "/.well-known/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(oauth2TokenRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(deviceNameRequestFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(publicClientAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
