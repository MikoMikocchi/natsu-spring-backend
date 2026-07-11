package io.mikoshift.natsu.backend.service.settings;

import io.mikoshift.natsu.backend.dto.request.ReaderSettingUpdateRequest;
import io.mikoshift.natsu.backend.entity.ReaderSetting;
import io.mikoshift.natsu.backend.entity.User;
import io.mikoshift.natsu.backend.repository.ReaderSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReaderSettingService {

    private final ReaderSettingRepository readerSettingRepository;

    @Transactional
    public ReaderSetting getOrCreate(User user) {
        return readerSettingRepository.findById(user.getId()).orElseGet(() -> {
            ReaderSetting settings = new ReaderSetting();
            settings.setUser(user);
            return readerSettingRepository.save(settings);
        });
    }

    /**
     * Applies the requested changes only if {@code request.updatedAtMs()} is not older than what's
     * already stored; an older update is a no-op and the current stored state is returned as-is, so a
     * client that was offline and is catching up can't clobber a newer value written elsewhere.
     */
    @Transactional
    public ReaderSetting update(User user, ReaderSettingUpdateRequest request) {
        ReaderSetting settings = getOrCreate(user);
        if (request.updatedAtMs() < settings.getUpdatedAtMs()) {
            return settings;
        }

        if (request.fontSizeSp() != null) {
            settings.setFontSizeSp(request.fontSizeSp());
        }
        if (request.lineSpacingMultiplier() != null) {
            settings.setLineSpacingMultiplier(request.lineSpacingMultiplier());
        }
        if (request.theme() != null) {
            settings.setTheme(request.theme());
        }
        if (request.furiganaMode() != null) {
            settings.setFuriganaMode(request.furiganaMode());
        }
        settings.setUpdatedAtMs(request.updatedAtMs());
        return settings;
    }
}
