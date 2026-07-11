package io.mikoshift.natsu.dto.response;

import io.mikoshift.natsu.entity.ReaderSetting;
import io.mikoshift.natsu.entity.ReaderSetting.FuriganaMode;
import io.mikoshift.natsu.entity.ReaderSetting.Theme;

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
