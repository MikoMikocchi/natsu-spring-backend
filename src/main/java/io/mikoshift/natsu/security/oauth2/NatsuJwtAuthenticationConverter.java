package io.mikoshift.natsu.security.oauth2;

import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NatsuJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final OAuth2AuthorizationSupport authorizationSupport;
    private final UserRepository userRepository;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String sid = jwt.getClaimAsString(NatsuOAuth2Claims.SID);
        if (sid == null || !authorizationSupport.isActive(sid, jwt.getTokenValue())) {
            throw new InvalidBearerTokenException("Session revoked or invalid");
        }

        Long userId = Long.parseLong(jwt.getSubject());
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new InvalidBearerTokenException("User not found for token subject"));

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, jwt, List.of());
        authentication.setDetails(sid);
        return authentication;
    }
}
