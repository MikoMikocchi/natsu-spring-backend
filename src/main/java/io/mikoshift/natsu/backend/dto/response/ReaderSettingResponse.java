package io.mikoshift.natsu.backend.dto.response;

import io.mikoshift.natsu.backend.entity.ReaderSetting;
import io.mikoshift.natsu.backend.entity.ReaderSetting.FuriganaMode;
import io.mikoshift.natsu.backend.entity.ReaderSetting.Theme;

public record ReaderSettingResponse(
        double fontSizeSp, double lineSpacingMultiplier, Theme theme, FuriganaMode furiganaMode, long updatedAtMs) {

    public static ReaderSettingResponse from(ReaderSetting settings) {
        return new ReaderSettingResponse(
                settings.getFontSizeSp(),
                settings.getLineSpacingMultiplier(),
                settings.getTheme(),
                settings.getFuriganaMode(),
                settings.getUpdatedAtMs());
    }
}
