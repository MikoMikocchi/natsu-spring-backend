package io.mikoshift.natsu.backend.controller.v1;

import io.mikoshift.natsu.backend.dto.response.DictionaryIndexResponse;
import io.mikoshift.natsu.backend.dto.response.DictionaryLookupResponse;
import io.mikoshift.natsu.backend.dto.response.DictionaryResponse;
import io.mikoshift.natsu.backend.dto.response.PaginationResponse;
import io.mikoshift.natsu.backend.entity.Dictionary;
import io.mikoshift.natsu.backend.entity.User;
import io.mikoshift.natsu.backend.service.ServerTimeService;
import io.mikoshift.natsu.backend.service.dictionary.DictionaryListService;
import io.mikoshift.natsu.backend.service.dictionary.DictionaryLookupService;
import io.mikoshift.natsu.backend.service.dictionary.DictionaryToggleService;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
public class DictionaryController {

  private final DictionaryListService dictionaryListService;
  private final DictionaryToggleService dictionaryToggleService;
  private final DictionaryLookupService dictionaryLookupService;
  private final ServerTimeService serverTimeService;

  @GetMapping("/v1/dictionaries")
  DictionaryIndexResponse index(
      @AuthenticationPrincipal User user,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "50") int perPage) {
    Page<Dictionary> result = dictionaryListService.list(page, perPage);
    var dictionaries =
        result.getContent().stream()
            .map(
                dictionary ->
                    DictionaryResponse.from(
                        dictionary, dictionaryListService.isEnabled(user, dictionary)))
            .toList();
    return new DictionaryIndexResponse(
        dictionaries, PaginationResponse.from(result), serverTimeService.nowMs());
  }

  @PatchMapping("/v1/dictionaries/{id}/toggle")
  ResponseEntity<Void> toggle(@AuthenticationPrincipal User user, @PathVariable UUID id) {
    dictionaryToggleService.toggle(user, id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/v1/dictionary/lookup")
  DictionaryLookupResponse lookup(
      @AuthenticationPrincipal User user, @RequestParam @NotBlank String q) {
    return new DictionaryLookupResponse(
        dictionaryLookupService.lookup(user, q), serverTimeService.nowMs());
  }
}
