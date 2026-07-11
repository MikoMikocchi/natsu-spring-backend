package io.mikoshift.natsu.service.dictionary;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Grammatical condition hierarchy for the deinflector. Maps a (possibly broad) part-of-speech
 * category to every leaf category it covers, so a generic deinflection tag ("v5") is recognized as
 * compatible with a dictionary entry's more specific rule tag ("v5k"), and vice versa.
 */
final class ConditionHierarchy {

    private static final Map<String, List<String>> DESCENDANTS = Map.ofEntries(
            Map.entry(
                    "v",
                    List.of(
                            "v1", "v1-s", "v5", "v5u", "v5u-s", "v5k", "v5k-s", "v5g", "v5s", "v5t", "v5n", "v5b",
                            "v5m", "v5r", "v5r-i", "v5aru", "vk", "vz", "vs", "vs-i", "vs-s")),
            Map.entry(
                    "v5",
                    List.of(
                            "v5", "v5u", "v5u-s", "v5k", "v5k-s", "v5g", "v5s", "v5t", "v5n", "v5b", "v5m", "v5r",
                            "v5r-i", "v5aru")),
            Map.entry("v1", List.of("v1", "v1-s")),
            Map.entry("vs", List.of("vs", "vs-i", "vs-s")),
            Map.entry("v5k", List.of("v5k", "v5k-s")),
            Map.entry("v5u", List.of("v5u", "v5u-s")),
            Map.entry("v5r", List.of("v5r", "v5r-i")),
            Map.entry("adj", List.of("adj-i", "adj-na", "adj-no", "adj-pn", "adj-t")),
            Map.entry("adj-na", List.of("adj-na", "adj-no", "adj-t")));

    private ConditionHierarchy() {}

    static Set<String> expand(List<String> conditions) {
        Set<String> expanded = new LinkedHashSet<>();
        for (String condition : conditions) {
            expanded.addAll(DESCENDANTS.getOrDefault(condition, List.of(condition)));
        }
        return expanded;
    }

    /**
     * True when a candidate carrying {@code current} conditions (or {@code null} for the original
     * surface form) may feed into a rule that requires {@code conditionsIn}.
     */
    static boolean conditionInMatches(List<String> current, List<String> conditionsIn) {
        if (current == null) {
            return true;
        }
        if (conditionsIn == null || conditionsIn.isEmpty()) {
            return false;
        }
        Set<String> remaining = expand(current);
        remaining.removeAll(expand(conditionsIn));
        return remaining.isEmpty();
    }

    /** True when a deinflection's grammatical category is compatible with a dictionary entry's rule tags. */
    static boolean conditionsMatch(List<String> conditionsOut, List<String> ruleTags) {
        Set<String> expandedOut = expand(conditionsOut);
        expandedOut.retainAll(expand(ruleTags));
        return !expandedOut.isEmpty();
    }
}
