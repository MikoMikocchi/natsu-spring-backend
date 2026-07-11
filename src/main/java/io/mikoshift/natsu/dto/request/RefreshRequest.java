package io.mikoshift.natsu.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Generated refresh tokens are 32 random bytes, base64url-encoded without padding (43 chars,
// see TokenService.generateOpaqueToken) -- 512 leaves generous headroom above that without
// letting a caller use this field to grow the per-token rate limit bucket map unbounded.
public record RefreshRequest(@NotBlank @Size(max = 512) String refreshToken) {}
