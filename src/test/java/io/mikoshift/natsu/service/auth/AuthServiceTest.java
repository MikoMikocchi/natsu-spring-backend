package io.mikoshift.natsu.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mikoshift.natsu.dto.request.LoginRequest;
import io.mikoshift.natsu.dto.request.RegisterRequest;
import io.mikoshift.natsu.entity.AuthToken;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.exception.InvalidCredentialsException;
import io.mikoshift.natsu.exception.ValidationException;
import io.mikoshift.natsu.repository.UserRepository;
import io.mikoshift.natsu.service.auth.AuthService.AuthResult;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, tokenService);
    }

    @Test
    void registerCreatesAUserAndIssuesATokenOnSuccess() {
        RegisterRequest request = new RegisterRequest("Aiko", "aiko@example.com", "password1", "password1");
        when(userRepository.existsByEmailIgnoreCase("aiko@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password1")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        AuthToken issuedToken = new AuthToken();
        when(tokenService.issue(any(User.class), eq("Pixel 8"))).thenReturn(issuedToken);

        AuthResult result = authService.register(request, "Pixel 8");

        assertThat(result.token()).isSameAs(issuedToken);
        assertThat(result.user().getEmail()).isEqualTo("aiko@example.com");
        assertThat(result.user().getPasswordHash()).isEqualTo("hashed");
    }

    @Test
    void registerRejectsAMismatchedPasswordConfirmation() {
        RegisterRequest request = new RegisterRequest("Aiko", "aiko@example.com", "password1", "different");

        assertThatThrownBy(() -> authService.register(request, "Pixel 8")).isInstanceOf(ValidationException.class);
        verify(userRepository, never()).existsByEmailIgnoreCase(any());
    }

    @Test
    void registerRejectsAnEmailThatIsAlreadyTaken() {
        RegisterRequest request = new RegisterRequest("Aiko", "aiko@example.com", "password1", "password1");
        when(userRepository.existsByEmailIgnoreCase("aiko@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request, "Pixel 8")).isInstanceOf(ValidationException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginSucceedsWithCorrectCredentials() {
        User user = new User();
        user.setEmail("aiko@example.com");
        user.setPasswordHash("hashed");
        when(userRepository.findByEmailIgnoreCase("aiko@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password1", "hashed")).thenReturn(true);
        AuthToken issuedToken = new AuthToken();
        when(tokenService.issue(user, "Pixel 8")).thenReturn(issuedToken);

        AuthResult result = authService.login(new LoginRequest("aiko@example.com", "password1"), "Pixel 8");

        assertThat(result.token()).isSameAs(issuedToken);
        assertThat(result.user()).isSameAs(user);
    }

    @Test
    void loginRejectsAnUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "password1"), "Pixel 8"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginRejectsAnIncorrectPassword() {
        User user = new User();
        user.setEmail("aiko@example.com");
        user.setPasswordHash("hashed");
        when(userRepository.findByEmailIgnoreCase("aiko@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("aiko@example.com", "wrong"), "Pixel 8"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(tokenService, never()).issue(any(), any());
    }

    @Test
    void refreshRotatesTheTokenAndReturnsItsOwner() {
        User user = new User();
        AuthToken rotated = new AuthToken();
        rotated.setUser(user);
        when(tokenService.rotate("refresh-token")).thenReturn(rotated);

        AuthResult result = authService.refresh("refresh-token");

        assertThat(result.token()).isSameAs(rotated);
        assertThat(result.user()).isSameAs(user);
    }

    @Test
    void logoutRevokesTheCurrentToken() {
        AuthToken token = new AuthToken();

        authService.logout(token);

        verify(tokenService).revoke(token);
    }
}
