package io.mikoshift.natsu.dto.response;

public record RegisterResponse(UserResponse user, long serverTimeMs) {}
