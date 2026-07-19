package io.mikoshift.natsu.service.auth;

import io.mikoshift.natsu.config.NatsuProperties;
import io.mikoshift.natsu.dto.request.LoginRequest;
import io.mikoshift.natsu.dto.response.TokenResponse;
import io.mikoshift.natsu.exception.InvalidCredentialsException;
import io.mikoshift.natsu.exception.TooManyRequestsException;
import io.mikoshift.natsu.security.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginService {

    private final AuthenticationManager authenticationManager;
    private final TokenIssuanceService tokenIssuanceService;
    private final RateLimiter rateLimiter;
    private final NatsuProperties natsuProperties;

    public TokenResponse login(LoginRequest request, String deviceName) {
        String email = request.email().trim().toLowerCase();
        NatsuProperties.RateLimit.Bucket emailBucket =
                natsuProperties.rateLimit().loginEmail();
        if (!rateLimiter.tryConsume("login-email", email, emailBucket)) {
            throw new TooManyRequestsException(emailBucket.windowSeconds());
        }

        Authentication userAuthentication;
        try {
            userAuthentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException();
        }

        return tokenIssuanceService.issueTokens(userAuthentication, deviceName);
    }
}
