package io.mikoshift.natsu.backend.dto.request;

import io.mikoshift.natsu.backend.entity.ReaderSetting.FuriganaMode;
import io.mikoshift.natsu.backend.entity.ReaderSetting.Theme;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * All fields except {@code updatedAtMs} are optional (partial update); only the fields present are
 * changed, and the whole update is dropped if {@code updatedAtMs} is older than what's already
 * stored.
 */
public record ReaderSettingUpdateRequest(
    @DecimalMin("10.0") @DecimalMax("40.0") Double fontSizeSp,
    @DecimalMin("1.0") @DecimalMax("3.0") Double lineSpacingMultiplier,
    Theme theme,
    FuriganaMode furiganaMode,
    @NotNull @PositiveOrZero Long updatedAtMs) {}
