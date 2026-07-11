package io.mikoshift.natsu.backend.service.dictionary;

/**
 * A single suffix-substitution rule: a surface form ending in {@code suffixIn} deinflects to a
 * candidate ending in {@code suffixOut}. {@code ruleTagOut} is the part-of-speech tag the resulting
 * dictionary-form candidate is expected to carry (a dictionary_terms.rule_tags value), used to
 * reject false-positive candidates that happen to match no real dictionary entry's tag.
 */
public record DeinflectionRule(
        String suffixIn, String suffixOut, String ruleTagOut, String ruleName, String description) {}
