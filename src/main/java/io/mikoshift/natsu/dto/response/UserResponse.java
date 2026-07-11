package io.mikoshift.natsu.dto.response;

import io.mikoshift.natsu.entity.User;
import java.time.Instant;

public record UserResponse(Long id, String name, String email, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
    }
}
