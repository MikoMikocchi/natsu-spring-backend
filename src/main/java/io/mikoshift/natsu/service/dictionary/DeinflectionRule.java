package io.mikoshift.natsu.service.dictionary;

import java.util.List;

/**
 * A single suffix-substitution rule: a word ending in {@code suffix} deinflects to a candidate
 * ending in {@code replacement}. {@code conditionsIn} is the grammatical category the word must
 * already carry for the rule to apply to a chained (non-surface) candidate -- an empty list means
 * the rule only fires on the original surface form. {@code conditionsOut} is the category of the
 * result, both for gating further chained rules and for matching against a dictionary entry's
 * rule_tags via {@link ConditionHierarchy#conditionsMatch}.
 */
record DeinflectionRule(
        String suffix, String replacement, List<String> conditionsIn, List<String> conditionsOut, String ruleName) {}
