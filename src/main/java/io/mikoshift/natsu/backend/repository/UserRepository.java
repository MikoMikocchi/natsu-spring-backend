package io.mikoshift.natsu.backend.repository;

import io.mikoshift.natsu.backend.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);

  Optional<User> findByResetPasswordToken(String resetPasswordToken);
}
