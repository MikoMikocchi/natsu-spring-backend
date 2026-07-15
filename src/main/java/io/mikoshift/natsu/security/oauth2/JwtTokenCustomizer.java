package io.mikoshift.natsu.security.oauth2;

import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.repository.UserRepository;
import io.mikoshift.natsu.security.AppUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final UserRepository userRepository;

    @Override
    public void customize(JwtEncodingContext context) {
        if (context.getAuthorization() != null) {
            context.getClaims()
                    .claim(OAuth2Claims.SID, context.getAuthorization().getId());
            String deviceName = context.getAuthorization().getAttribute(OAuth2Claims.DEVICE_NAME);
            if (deviceName != null) {
                context.getClaims().claim(OAuth2Claims.DEVICE_NAME, deviceName);
            }
        }

        User user = resolveUser(context);
        if (user != null) {
            context.getClaims().subject(user.getId().toString());
            context.getClaims().claim("email", user.getEmail());
            context.getClaims().claim("name", user.getName());
        }

        if (context.getAuthorizedScopes().contains(OidcParameterNames.ID_TOKEN)) {
            context.getClaims().claim(OidcParameterNames.ID_TOKEN, true);
        }
    }

    private User resolveUser(JwtEncodingContext context) {
        Object principal = context.getPrincipal().getPrincipal();
        if (principal instanceof AppUserDetails userDetails) {
            return userDetails.getUser();
        }
        if (context.getAuthorization() != null) {
            return userRepository
                    .findByEmailIgnoreCase(context.getAuthorization().getPrincipalName())
                    .orElse(null);
        }
        return null;
    }
}
