package io.mikoshift.natsu.backend.controller.v1;

import io.mikoshift.natsu.backend.dto.response.DeviceSessionResponse;
import io.mikoshift.natsu.backend.entity.AuthToken;
import io.mikoshift.natsu.backend.entity.User;
import io.mikoshift.natsu.backend.service.auth.DeviceSessionService;
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
  List<DeviceSessionResponse> list(
      @AuthenticationPrincipal User user, Authentication authentication) {
    return deviceSessionService.list(user, (AuthToken) authentication.getCredentials());
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> revoke(@AuthenticationPrincipal User user, @PathVariable Long id) {
    deviceSessionService.revoke(user, id);
    return ResponseEntity.noContent().build();
  }
}
