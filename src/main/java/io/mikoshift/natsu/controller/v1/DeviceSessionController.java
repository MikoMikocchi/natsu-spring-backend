package io.mikoshift.natsu.controller.v1;

import io.mikoshift.natsu.dto.response.DeviceSessionResponse;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.security.oauth2.SessionAuthenticationDetails;
import io.mikoshift.natsu.service.auth.DeviceSessionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth/sessions")
@RequiredArgsConstructor
public class DeviceSessionController {

    private final DeviceSessionService deviceSessionService;

    @GetMapping
    List<DeviceSessionResponse> list(@AuthenticationPrincipal User user, Authentication authentication) {
        return deviceSessionService.list(user, currentAuthorizationId(authentication));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> revoke(@AuthenticationPrincipal User user, @PathVariable String id) {
        deviceSessionService.revoke(user, id);
        return ResponseEntity.noContent().build();
    }

    private static String currentAuthorizationId(Authentication authentication) {
        if (authentication.getDetails() instanceof SessionAuthenticationDetails details) {
            return details.sessionId();
        }
        return null;
    }
}
