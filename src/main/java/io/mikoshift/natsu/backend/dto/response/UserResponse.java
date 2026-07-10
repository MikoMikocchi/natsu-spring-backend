package io.mikoshift.natsu.backend.dto.response;

import io.mikoshift.natsu.backend.entity.User;
import java.time.Instant;

public record UserResponse(Long id, String name, String email, Instant createdAt) {

  public static UserResponse from(User user) {
    return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
  }
}
