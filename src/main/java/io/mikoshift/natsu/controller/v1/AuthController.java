package io.mikoshift.natsu.controller.v1;

import io.mikoshift.natsu.dto.request.DeleteAccountRequest;
import io.mikoshift.natsu.dto.request.LoginRequest;
import io.mikoshift.natsu.dto.request.RegisterRequest;
import io.mikoshift.natsu.dto.response.RegisterResponse;
import io.mikoshift.natsu.dto.response.TokenResponse;
import io.mikoshift.natsu.dto.response.UserResponse;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.service.auth.AccountDeletionService;
import io.mikoshift.natsu.service.auth.AuthService;
import io.mikoshift.natsu.service.auth.LoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginService loginService;
    private final AccountDeletionService accountDeletionService;
    private final Clock clock;

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return loginService.login(request, deviceName(httpRequest));
    }

    @PostMapping("/register")
    ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse(UserResponse.from(user), clock.millis()));
    }

    @DeleteMapping("/account")
    ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal User user, @Valid @RequestBody DeleteAccountRequest request) {
        accountDeletionService.deleteAccount(user, request.password());
        return ResponseEntity.noContent().build();
    }

    private static String deviceName(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }
        return userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
    }
}
