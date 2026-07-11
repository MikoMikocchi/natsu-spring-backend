package io.mikoshift.natsu.controller.v1;

import io.mikoshift.natsu.dto.request.ChangePasswordRequest;
import io.mikoshift.natsu.dto.request.ForgotPasswordRequest;
import io.mikoshift.natsu.dto.request.ResetPasswordRequest;
import io.mikoshift.natsu.dto.response.MessageResponse;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.service.auth.PasswordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth/password")
@RequiredArgsConstructor
public class PasswordController {

    private final PasswordService passwordService;

    @PatchMapping
    MessageResponse change(@AuthenticationPrincipal User user, @Valid @RequestBody ChangePasswordRequest request) {
        passwordService.changePassword(user, request);
        return new MessageResponse("Password updated successfully");
    }

    @PostMapping("/forgot")
    MessageResponse forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordService.forgotPassword(request.email());
        return new MessageResponse("If an account exists for that email, password reset instructions have been sent");
    }

    @PostMapping("/reset")
    MessageResponse reset(@Valid @RequestBody ResetPasswordRequest request) {
        passwordService.resetPassword(request);
        return new MessageResponse("Password reset successfully");
    }
}
