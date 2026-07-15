package io.mikoshift.natsu.controller.v1;

import io.mikoshift.natsu.dto.request.ReaderSettingUpdateRequest;
import io.mikoshift.natsu.dto.response.ReaderSettingResponse;
import io.mikoshift.natsu.dto.response.ReaderSettingShowResponse;
import io.mikoshift.natsu.entity.ReaderSetting;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.service.settings.ReaderSettingService;
import jakarta.validation.Valid;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/settings/reader")
@RequiredArgsConstructor
public class ReaderSettingController {

    private final ReaderSettingService readerSettingService;
    private final Clock clock;

    @GetMapping
    ReaderSettingShowResponse show(@AuthenticationPrincipal User user) {
        return toResponse(readerSettingService.getOrCreate(user));
    }

    @PatchMapping
    ReaderSettingShowResponse update(
            @AuthenticationPrincipal User user, @Valid @RequestBody ReaderSettingUpdateRequest request) {
        return toResponse(readerSettingService.update(user, request));
    }

    private ReaderSettingShowResponse toResponse(ReaderSetting settings) {
        return new ReaderSettingShowResponse(ReaderSettingResponse.from(settings), clock.millis());
    }
}
