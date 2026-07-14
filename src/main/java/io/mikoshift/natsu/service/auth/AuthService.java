package io.mikoshift.natsu.service.auth;

import io.mikoshift.natsu.dto.request.RegisterRequest;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.exception.ValidationException;
import io.mikoshift.natsu.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(RegisterRequest request) {
        if (!request.password().equals(request.passwordConfirmation())) {
            throw ValidationException.of("password_confirmation", "doesn't match Password");
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw ValidationException.of("email", "has already been taken");
        }

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return userRepository.save(user);
    }
}
