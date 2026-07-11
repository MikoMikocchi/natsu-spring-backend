package io.mikoshift.natsu.service.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mikoshift.natsu.dto.request.ReaderSettingUpdateRequest;
import io.mikoshift.natsu.entity.ReaderSetting;
import io.mikoshift.natsu.entity.ReaderSetting.FuriganaMode;
import io.mikoshift.natsu.entity.ReaderSetting.Theme;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.repository.ReaderSettingRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReaderSettingServiceTest {

    @Mock
    private ReaderSettingRepository readerSettingRepository;

    private ReaderSettingService settingService;
    private User user;

    @BeforeEach
    void setUp() {
        settingService = new ReaderSettingService(readerSettingRepository);
        user = new User();
        user.setId(1L);
    }

    @Test
    void getOrCreateReturnsExistingSettingsWithoutCreatingANewOne() {
        ReaderSetting existing = new ReaderSetting();
        existing.setUser(user);
        when(readerSettingRepository.findById(1L)).thenReturn(Optional.of(existing));

        ReaderSetting result = settingService.getOrCreate(user);

        assertThat(result).isSameAs(existing);
        verify(readerSettingRepository).acquireCreationLock(1L);
        verify(readerSettingRepository, never()).save(any(ReaderSetting.class));
    }

    @Test
    void getOrCreateCreatesAndPersistsDefaultsWhenNoneExistYet() {
        when(readerSettingRepository.findById(1L)).thenReturn(Optional.empty());
        when(readerSettingRepository.save(any(ReaderSetting.class))).thenAnswer(inv -> inv.getArgument(0));

        ReaderSetting result = settingService.getOrCreate(user);

        assertThat(result.getUser()).isSameAs(user);
        assertThat(result.getFontSizeSp()).isEqualTo(16.0);
        verify(readerSettingRepository).acquireCreationLock(1L);
        verify(readerSettingRepository).save(result);
    }

    @Test
    void ignoresAnIncomingUpdateOlderThanTheStoredVersion() {
        // A device catching up after being offline pushes a stale snapshot; the newer server-side
        // state must win.
        ReaderSetting stored = new ReaderSetting();
        stored.setUser(user);
        stored.setFontSizeSp(20.0);
        stored.setUpdatedAtMs(9000L);
        when(readerSettingRepository.findById(1L)).thenReturn(Optional.of(stored));

        ReaderSettingUpdateRequest staleRequest =
                new ReaderSettingUpdateRequest(30.0, 2.0, Theme.DARK, FuriganaMode.ALWAYS, 8000L);

        ReaderSetting result = settingService.update(user, staleRequest);

        assertThat(result).isSameAs(stored);
        assertThat(result.getFontSizeSp()).isEqualTo(20.0);
        assertThat(result.getTheme()).isNotEqualTo(Theme.DARK);
    }

    @Test
    void appliesAnUpdateWithTheSameTimestampAsStored() {
        // Equal timestamps are not treated as "older", matching the same convention used for
        // document sync -- an idempotent retry of the same push still applies.
        ReaderSetting stored = new ReaderSetting();
        stored.setUser(user);
        stored.setUpdatedAtMs(5000L);
        when(readerSettingRepository.findById(1L)).thenReturn(Optional.of(stored));

        ReaderSettingUpdateRequest sameTimestampRequest = new ReaderSettingUpdateRequest(24.0, null, null, null, 5000L);

        ReaderSetting result = settingService.update(user, sameTimestampRequest);

        assertThat(result.getFontSizeSp()).isEqualTo(24.0);
    }

    @Test
    void appliesOnlyTheFieldsPresentInAPartialUpdate() {
        ReaderSetting stored = new ReaderSetting();
        stored.setUser(user);
        stored.setFontSizeSp(16.0);
        stored.setLineSpacingMultiplier(1.8);
        stored.setTheme(Theme.LIGHT);
        stored.setFuriganaMode(FuriganaMode.OFF);
        stored.setUpdatedAtMs(1000L);
        when(readerSettingRepository.findById(1L)).thenReturn(Optional.of(stored));

        // Only theme is being changed; every other field is left null in the request.
        ReaderSettingUpdateRequest partialRequest =
                new ReaderSettingUpdateRequest(null, null, Theme.SEPIA, null, 2000L);

        ReaderSetting result = settingService.update(user, partialRequest);

        assertThat(result.getTheme()).isEqualTo(Theme.SEPIA);
        assertThat(result.getFontSizeSp()).isEqualTo(16.0);
        assertThat(result.getLineSpacingMultiplier()).isEqualTo(1.8);
        assertThat(result.getFuriganaMode()).isEqualTo(FuriganaMode.OFF);
        assertThat(result.getUpdatedAtMs()).isEqualTo(2000L);
    }

    @Test
    void createsDefaultSettingsBeforeApplyingAnUpdateWhenNoneExistYet() {
        when(readerSettingRepository.findById(1L)).thenReturn(Optional.empty());
        when(readerSettingRepository.save(any(ReaderSetting.class))).thenAnswer(inv -> inv.getArgument(0));

        ReaderSettingUpdateRequest request = new ReaderSettingUpdateRequest(22.0, null, Theme.DARK, null, 1000L);

        ReaderSetting result = settingService.update(user, request);

        assertThat(result.getFontSizeSp()).isEqualTo(22.0);
        assertThat(result.getTheme()).isEqualTo(Theme.DARK);
        assertThat(result.getUpdatedAtMs()).isEqualTo(1000L);
    }
}
