package io.mikoshift.natsu.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(@NotBlank String password) {}
