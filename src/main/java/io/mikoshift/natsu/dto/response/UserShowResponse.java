package io.mikoshift.natsu.dto.response;

public record UserShowResponse(UserResponse user, long serverTimeMs) {}
