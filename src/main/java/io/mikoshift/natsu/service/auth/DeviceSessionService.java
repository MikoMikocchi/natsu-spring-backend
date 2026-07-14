package io.mikoshift.natsu.service.auth;

import io.mikoshift.natsu.dto.response.DeviceSessionResponse;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.exception.NotFoundException;
import io.mikoshift.natsu.security.oauth2.OAuth2AuthorizationSupport;
import io.mikoshift.natsu.security.oauth2.OAuth2AuthorizationSupport.AuthorizationSession;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceSessionService {

    private final OAuth2AuthorizationSupport authorizationSupport;
    private final OAuth2AuthorizationService authorizationService;

    @Transactional(readOnly = true)
    public List<DeviceSessionResponse> list(User user, String currentAuthorizationId) {
        return authorizationSupport.findActiveSessionsForUser(user).stream()
                .map(session -> toResponse(session, currentAuthorizationId))
                .toList();
    }

    @Transactional
    public void revoke(User user, String authorizationId) {
        OAuth2Authorization authorization = authorizationService.findById(authorizationId);
        if (authorization == null || !user.getEmail().equalsIgnoreCase(authorization.getPrincipalName())) {
            throw new NotFoundException("Session not found");
        }
        authorizationService.remove(authorization);
    }

    private DeviceSessionResponse toResponse(AuthorizationSession session, String currentAuthorizationId) {
        OAuth2Authorization authorization = authorizationService.findById(session.id());
        String deviceName = authorization != null ? authorizationSupport.deviceName(authorization) : "Unknown device";
        return new DeviceSessionResponse(
                session.id(),
                deviceName,
                session.createdAt(),
                session.id().equals(currentAuthorizationId));
    }
}
