package io.mikoshift.natsu.service.auth;

import io.mikoshift.natsu.dto.response.DeviceSessionResponse;
import io.mikoshift.natsu.entity.AuthToken;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.exception.NotFoundException;
import io.mikoshift.natsu.repository.AuthTokenRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceSessionService {

    private final AuthTokenRepository authTokenRepository;

    @Transactional(readOnly = true)
    public List<DeviceSessionResponse> list(User user, AuthToken currentToken) {
        return authTokenRepository.findAllByUserAndRevokedAtIsNullOrderByCreatedAtDesc(user).stream()
                .map(token -> new DeviceSessionResponse(
                        token.getId(),
                        token.getName(),
                        token.getCreatedAt(),
                        token.getId().equals(currentToken.getId())))
                .toList();
    }

    @Transactional
    public void revoke(User user, Long tokenId) {
        AuthToken token = authTokenRepository
                .findByIdAndUser(tokenId, user)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        token.setRevokedAt(Instant.now());
    }
}
