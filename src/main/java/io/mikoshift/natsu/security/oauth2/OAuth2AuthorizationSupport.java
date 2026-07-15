package io.mikoshift.natsu.security.oauth2;

import io.mikoshift.natsu.entity.User;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OAuth2AuthorizationSupport {

    private final OAuth2AuthorizationService authorizationService;
    private final JdbcTemplate jdbcTemplate;

    public boolean isActive(String authorizationId, String accessTokenValue) {
        OAuth2Authorization authorization = authorizationService.findById(authorizationId);
        if (authorization == null) {
            return false;
        }

        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getAccessToken();
        if (accessToken == null
                || accessToken.isInvalidated()
                || accessToken.isExpired()
                || !accessToken.getToken().getTokenValue().equals(accessTokenValue)) {
            return false;
        }

        OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = authorization.getRefreshToken();
        return refreshToken == null || (!refreshToken.isInvalidated() && !refreshToken.isExpired());
    }

    public List<AuthorizationSession> findActiveSessionsForUser(User user) {
        return jdbcTemplate.query(
                """
                select id, access_token_issued_at
                from oauth2_authorization
                where principal_name = ?
                  and access_token_value is not null
                order by access_token_issued_at desc
                """,
                (rs, rowNum) -> new AuthorizationSession(
                        rs.getString("id"),
                        user.getEmail(),
                        rs.getTimestamp("access_token_issued_at").toInstant()),
                user.getEmail());
    }

    public void revokeAllForUser(User user) {
        List<String> ids = jdbcTemplate.queryForList(
                "select id from oauth2_authorization where principal_name = ?", String.class, user.getEmail());
        for (String id : ids) {
            OAuth2Authorization authorization = authorizationService.findById(id);
            if (authorization != null) {
                authorizationService.remove(authorization);
            }
        }
    }

    public String deviceName(OAuth2Authorization authorization) {
        String deviceName = authorization.getAttribute(NatsuOAuth2Claims.DEVICE_NAME);
        return deviceName != null ? deviceName : "Unknown device";
    }

    public Instant createdAt(OAuth2Authorization authorization) {
        if (authorization.getAccessToken() != null
                && authorization.getAccessToken().getToken().getIssuedAt() != null) {
            return authorization.getAccessToken().getToken().getIssuedAt();
        }
        return Instant.EPOCH;
    }

    public record AuthorizationSession(String id, String principalName, Instant createdAt) {}
}
