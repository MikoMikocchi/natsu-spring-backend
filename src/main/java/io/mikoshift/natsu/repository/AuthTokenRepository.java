package io.mikoshift.natsu.repository;

import io.mikoshift.natsu.entity.AuthToken;
import io.mikoshift.natsu.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByAccessTokenAndRevokedAtIsNull(String accessToken);

    Optional<AuthToken> findByRefreshTokenAndRevokedAtIsNull(String refreshToken);

    Optional<AuthToken> findByPreviousRefreshTokenAndRevokedAtIsNull(String previousRefreshToken);

    List<AuthToken> findAllByUserAndRevokedAtIsNullOrderByCreatedAtDesc(User user);

    Optional<AuthToken> findByIdAndUser(Long id, User user);
}
