package io.mikoshift.natsu.controller.v1;

import io.mikoshift.natsu.config.NatsuProperties;
import io.mikoshift.natsu.dto.request.DeleteAccountRequest;
import io.mikoshift.natsu.dto.request.LoginRequest;
import io.mikoshift.natsu.dto.request.RefreshRequest;
import io.mikoshift.natsu.dto.request.RegisterRequest;
import io.mikoshift.natsu.dto.response.AuthResponse;
import io.mikoshift.natsu.dto.response.UserResponse;
import io.mikoshift.natsu.dto.response.UserShowResponse;
import io.mikoshift.natsu.entity.AuthToken;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.exception.RateLimitExceededException;
import io.mikoshift.natsu.security.RateLimiter;
import io.mikoshift.natsu.service.ServerTimeService;
import io.mikoshift.natsu.service.auth.AccountDeletionService;
import io.mikoshift.natsu.service.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AccountDeletionService accountDeletionService;
    private final ServerTimeService serverTimeService;
    private final RateLimiter rateLimiter;
    private final NatsuProperties natsuProperties;

    @PostMapping("/register")
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        AuthService.AuthResult result = authService.register(request, deviceName(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(toAuthResponse(result));
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        checkRateLimit(
                "login-email",
                request.email().trim().toLowerCase(),
                natsuProperties.rateLimit().loginEmail());
        return toAuthResponse(authService.login(request, deviceName(httpRequest)));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(Authentication authentication) {
        authService.logout((AuthToken) authentication.getCredentials());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        checkRateLimit(
                "refresh-token",
                request.refreshToken(),
                natsuProperties.rateLimit().refreshToken());
        return toAuthResponse(authService.refresh(request.refreshToken()));
    }

    @GetMapping("/user")
    UserShowResponse currentUser(@AuthenticationPrincipal User user) {
        return new UserShowResponse(UserResponse.from(user), serverTimeService.nowMs());
    }

    @DeleteMapping("/account")
    ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal User user, @Valid @RequestBody DeleteAccountRequest request) {
        accountDeletionService.deleteAccount(user, request.password());
        return ResponseEntity.noContent().build();
    }

    private AuthResponse toAuthResponse(AuthService.AuthResult result) {
        return new AuthResponse(
                result.token().getAccessToken(),
                result.token().getRefreshToken(),
                UserResponse.from(result.user()),
                serverTimeService.nowMs());
    }

    private void checkRateLimit(String category, String key, NatsuProperties.RateLimit.Bucket config) {
        if (!rateLimiter.tryConsume(category, key, config)) {
            throw new RateLimitExceededException(config.windowSeconds());
        }
    }

    private static String deviceName(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }
        return userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
    }
}
