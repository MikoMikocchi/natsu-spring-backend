package io.mikoshift.natsu.security.oauth2;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates public mobile clients via {@code client_id} on token/revoke endpoints. */
@Component
public class PublicClientAuthenticationFilter extends OncePerRequestFilter {

    private final RegisteredClientRepository registeredClientRepository;

    public PublicClientAuthenticationFilter(RegisteredClientRepository registeredClientRepository) {
        this.registeredClientRepository = registeredClientRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (("/oauth2/token".equals(request.getRequestURI()) || "/oauth2/revoke".equals(request.getRequestURI()))
                && "POST".equalsIgnoreCase(request.getMethod())) {
            String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
            if (StringUtils.hasText(clientId) && !StringUtils.hasText(request.getParameter(OAuth2ParameterNames.CLIENT_SECRET))) {
                RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
                if (registeredClient != null
                        && registeredClient
                                .getClientAuthenticationMethods()
                                .contains(ClientAuthenticationMethod.NONE)) {
                    SecurityContextHolder.getContext()
                            .setAuthentication(new OAuth2ClientAuthenticationToken(
                                    registeredClient, ClientAuthenticationMethod.NONE, null));
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
