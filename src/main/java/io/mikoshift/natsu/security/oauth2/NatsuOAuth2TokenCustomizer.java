package io.mikoshift.natsu.security.oauth2;

import io.mikoshift.natsu.security.NatsuUserDetails;
import io.mikoshift.natsu.repository.UserRepository;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NatsuOAuth2TokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final UserRepository userRepository;

    @Override
    public void customize(JwtEncodingContext context) {
        if (context.getAuthorization() != null) {
            context.getClaims().claim(NatsuOAuth2Claims.SID, context.getAuthorization().getId());
            String deviceName = context.getAuthorization().getAttribute(NatsuOAuth2Claims.DEVICE_NAME);
            if (deviceName != null) {
                context.getClaims().claim(NatsuOAuth2Claims.DEVICE_NAME, deviceName);
            }
        }

        NatsuUserDetails userDetails = resolveUserDetails(context);
        if (userDetails != null) {
            context.getClaims().subject(userDetails.getUser().getId().toString());
            context.getClaims().claim("email", userDetails.getUser().getEmail());
            context.getClaims().claim("name", userDetails.getUser().getName());
        }

        if (context.getAuthorizedScopes().contains(OidcParameterNames.ID_TOKEN)) {
            context.getClaims().claim(OidcParameterNames.ID_TOKEN, true);
        }
    }

    private NatsuUserDetails resolveUserDetails(JwtEncodingContext context) {
        Object principal = context.getPrincipal().getPrincipal();
        if (principal instanceof NatsuUserDetails userDetails) {
            return userDetails;
        }
        if (context.getAuthorization() != null) {
            return userRepository
                    .findByEmailIgnoreCase(context.getAuthorization().getPrincipalName())
                    .map(NatsuUserDetails::new)
                    .orElse(null);
        }
        return null;
    }
}
