package io.mikoshift.natsu.service.dictionary;

import io.mikoshift.natsu.config.CacheConfig;
import io.mikoshift.natsu.dto.response.DictionaryLookupResultResponse;
import io.mikoshift.natsu.dto.response.DictionarySenseResponse;
import io.mikoshift.natsu.entity.DictionaryTerm;
import io.mikoshift.natsu.entity.User;
import io.mikoshift.natsu.repository.DictionaryTermRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Candidates are generated (direct match, kana-folded variant, every deinflection candidate and its
 * kana-folded variant) and looked up against the dictionary_terms table; results are grouped by
 * (expression, reading), ranked direct-before-deinflected and then by score, capped at 5.
 */
@Service
@RequiredArgsConstructor
public class DictionaryLookupService {

    private static final int MAX_RESULTS = 5;

    private final DictionaryTermRepository dictionaryTermRepository;
    private final DictionaryEnablementService dictionaryEnablementService;
    private final JapaneseDeinflector deinflector;
    private final ObjectMapper objectMapper;

    @Cacheable(
            cacheNames = CacheConfig.DICT_LOOKUP_CACHE,
            key = "#user.id + ':' + #user.dictCacheVersion + ':' + #query.toLowerCase()")
    @Transactional(readOnly = true)
    public List<DictionaryLookupResultResponse> lookup(User user, String query) {
        Set<UUID> disabledDictionaryIds = dictionaryEnablementService.disabledDictionaryIds(user);

        List<Candidate> candidates = buildCandidates(query);
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<String> words = candidates.stream().map(Candidate::word).toList();
        Map<String, List<DictionaryTerm>> termsByWord =
                indexTermsByWord(words, dictionaryTermRepository.findByWords(words));

        Map<String, GroupedResult> grouped = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            for (DictionaryTerm term : termsByWord.getOrDefault(candidate.word(), List.of())) {
                if (disabledDictionaryIds.contains(term.getDictionary().getId())) {
                    continue;
                }
                if (candidate.conditionsOut() != null
                        && !matchesRuleTags(candidate.conditionsOut(), term.getRuleTags())) {
                    continue;
                }
                String groupKey = term.getExpression() + "::" + term.getReading();
                grouped.computeIfAbsent(
                                groupKey, key -> new GroupedResult(term.getExpression(), term.getReading(), candidate))
                        .addSense(term);
            }
        }

        return grouped.values().stream()
                .sorted(Comparator.<GroupedResult>comparingInt(GroupedResult::priority)
                        .thenComparing(
                                Comparator.comparingInt(GroupedResult::maxScore).reversed()))
                .limit(MAX_RESULTS)
                .map(GroupedResult::toResponse)
                .toList();
    }

    private static Map<String, List<DictionaryTerm>> indexTermsByWord(List<String> words, List<DictionaryTerm> terms) {
        Set<String> wordSet = Set.copyOf(words);
        Map<String, List<DictionaryTerm>> termsByWord = new HashMap<>();
        for (DictionaryTerm term : terms) {
            if (wordSet.contains(term.getExpression())) {
                termsByWord
                        .computeIfAbsent(term.getExpression(), ignored -> new ArrayList<>())
                        .add(term);
            }
            if (wordSet.contains(term.getReading()) && !term.getReading().equals(term.getExpression())) {
                termsByWord
                        .computeIfAbsent(term.getReading(), ignored -> new ArrayList<>())
                        .add(term);
            }
        }
        return termsByWord;
    }

    private List<Candidate> buildCandidates(String query) {
        List<Candidate> candidates = new ArrayList<>();
        addCandidate(candidates, query, null, MatchKind.DIRECT, null, null, 0);
        addCandidate(candidates, KanaUtils.foldedVariant(query), null, MatchKind.DIRECT, null, null, 0);

        for (Deinflection deinflection : deinflector.deinflect(query)) {
            addCandidate(
                    candidates,
                    deinflection.candidate(),
                    deinflection.conditionsOut(),
                    MatchKind.DEINFLECTION,
                    deinflection.ruleName(),
                    deinflection.description(),
                    1);
            addCandidate(
                    candidates,
                    KanaUtils.foldedVariant(deinflection.candidate()),
                    deinflection.conditionsOut(),
                    MatchKind.DEINFLECTION,
                    deinflection.ruleName(),
                    deinflection.description(),
                    1);
        }
        return candidates;
    }

    private static void addCandidate(
            List<Candidate> candidates,
            String word,
            List<String> conditionsOut,
            MatchKind matchKind,
            String ruleName,
            String description,
            int priority) {
        if (word == null
                || word.isBlank()
                || candidates.stream().anyMatch(c -> c.word().equals(word))) {
            return;
        }
        candidates.add(new Candidate(word, conditionsOut, matchKind, ruleName, description, priority));
    }

    /**
     * Accepts when either side carries no part-of-speech constraint: blank rule_tags on the
     * dictionary entry, or an empty conditionsOut from a passthrough transform (e.g. sentence-final
     * の/か).
     */
    private static boolean matchesRuleTags(List<String> conditionsOut, String ruleTags) {
        if (conditionsOut == null || conditionsOut.isEmpty()) {
            return true;
        }
        if (ruleTags == null || ruleTags.isBlank()) {
            return true;
        }
        return ConditionHierarchy.conditionsMatch(conditionsOut, List.of(ruleTags.split("\\s+")));
    }

    private record Candidate(
            String word,
            List<String> conditionsOut,
            MatchKind matchKind,
            String ruleName,
            String description,
            int priority) {}

    private final class GroupedResult {
        private final String word;
        private final String reading;
        private final Candidate matchedBy;
        private final List<DictionarySenseResponse> senses = new ArrayList<>();
        private int maxScore = 0;

        GroupedResult(String word, String reading, Candidate matchedBy) {
            this.word = word;
            this.reading = reading;
            this.matchedBy = matchedBy;
        }

        void addSense(DictionaryTerm term) {
            List<String> definitions = parseGlosses(term.getGlossesJson());
            List<String> partsOfSpeech = term.getRuleTags().isBlank()
                    ? List.of()
                    : List.of(term.getRuleTags().split("\\s+"));
            senses.add(new DictionarySenseResponse(
                    definitions, partsOfSpeech, term.getDictionary().getTitle()));
            maxScore = Math.max(maxScore, term.getScore());
        }

        int priority() {
            return matchedBy.priority();
        }

        int maxScore() {
            return maxScore;
        }

        DictionaryLookupResultResponse toResponse() {
            return new DictionaryLookupResultResponse(
                    word, reading, matchedBy.matchKind(), matchedBy.ruleName(), matchedBy.description(), senses);
        }

        @SuppressWarnings("unchecked")
        private List<String> parseGlosses(String glossesJson) {
            try {
                return objectMapper.readValue(glossesJson, List.class);
            } catch (RuntimeException e) {
                return List.of();
            }
        }
    }
}
