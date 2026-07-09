package io.mikoshift.natsu.backend.dto.response;

public record UserShowResponse(UserResponse user, long serverTimeMs) {}
