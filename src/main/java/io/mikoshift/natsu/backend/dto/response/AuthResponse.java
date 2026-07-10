package io.mikoshift.natsu.backend.dto.response;

public record AuthResponse(
    String token, String refreshToken, UserResponse user, long serverTimeMs) {}
