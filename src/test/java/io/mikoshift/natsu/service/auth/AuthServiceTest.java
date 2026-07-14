package io.mikoshift.natsu.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mikoshift.natsu.dto.request.RegisterRequest;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.exception.ValidationException;
import io.mikoshift.natsu.repository.UserRepository;
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

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder);
    }

    @Test
    void registerCreatesAUserOnSuccess() {
        RegisterRequest request = new RegisterRequest("Aiko", "aiko@example.com", "password1", "password1");
        when(userRepository.existsByEmailIgnoreCase("aiko@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password1")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = authService.register(request);

        assertThat(user.getEmail()).isEqualTo("aiko@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("hashed");
    }

    @Test
    void registerRejectsAMismatchedPasswordConfirmation() {
        RegisterRequest request = new RegisterRequest("Aiko", "aiko@example.com", "password1", "different");

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(ValidationException.class);
        verify(userRepository, never()).existsByEmailIgnoreCase(any());
    }

    @Test
    void registerRejectsAnEmailThatIsAlreadyTaken() {
        RegisterRequest request = new RegisterRequest("Aiko", "aiko@example.com", "password1", "password1");
        when(userRepository.existsByEmailIgnoreCase("aiko@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(ValidationException.class);
        verify(userRepository, never()).save(any());
    }
}
