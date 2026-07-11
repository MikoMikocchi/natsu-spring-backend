package io.mikoshift.natsu.dto.response;

public record AuthResponse(String token, String refreshToken, UserResponse user, long serverTimeMs) {}
