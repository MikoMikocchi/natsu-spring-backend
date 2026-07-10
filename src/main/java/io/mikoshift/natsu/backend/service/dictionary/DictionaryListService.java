package io.mikoshift.natsu.backend.service.dictionary;

import io.mikoshift.natsu.backend.entity.Dictionary;
import io.mikoshift.natsu.backend.entity.User;
import io.mikoshift.natsu.backend.repository.DictionaryRepository;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DictionaryListService {

  private static final int MAX_PER_PAGE = 100;

  private final DictionaryRepository dictionaryRepository;
  private final DictionaryEnablementService dictionaryEnablementService;

  @Transactional(readOnly = true)
  public Page<Dictionary> list(int page, int perPage) {
    int boundedPerPage = Math.min(Math.max(perPage, 1), MAX_PER_PAGE);
    int boundedPage = Math.max(page, 1);
    return dictionaryRepository.findAll(PageRequest.of(boundedPage - 1, boundedPerPage));
  }

  @Transactional(readOnly = true)
  public boolean isEnabled(User user, Dictionary dictionary) {
    Set<UUID> disabled = dictionaryEnablementService.disabledDictionaryIds(user);
    return !disabled.contains(dictionary.getId());
  }
}
