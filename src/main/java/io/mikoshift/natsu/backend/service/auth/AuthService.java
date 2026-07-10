package io.mikoshift.natsu.backend.service.auth;

import io.mikoshift.natsu.backend.dto.request.LoginRequest;
import io.mikoshift.natsu.backend.dto.request.RegisterRequest;
import io.mikoshift.natsu.backend.entity.AuthToken;
import io.mikoshift.natsu.backend.entity.User;
import io.mikoshift.natsu.backend.exception.InvalidCredentialsException;
import io.mikoshift.natsu.backend.exception.ValidationException;
import io.mikoshift.natsu.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokenService;

  @Transactional
  public AuthResult register(RegisterRequest request, String deviceName) {
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
    user = userRepository.save(user);

    return new AuthResult(tokenService.issue(user, deviceName), user);
  }

  @Transactional
  public AuthResult login(LoginRequest request, String deviceName) {
    User user =
        userRepository
            .findByEmailIgnoreCase(request.email())
            .orElseThrow(InvalidCredentialsException::new);
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }
    return new AuthResult(tokenService.issue(user, deviceName), user);
  }

  @Transactional
  public AuthResult refresh(String refreshToken) {
    AuthToken token = tokenService.rotate(refreshToken);
    return new AuthResult(token, token.getUser());
  }

  @Transactional
  public void logout(AuthToken currentToken) {
    tokenService.revoke(currentToken);
  }

  public record AuthResult(AuthToken token, User user) {}
}
