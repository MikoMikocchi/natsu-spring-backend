package io.mikoshift.natsu.dto.response;

import java.time.Instant;

public record DeviceSessionResponse(Long id, String name, Instant createdAt, boolean current) {}
