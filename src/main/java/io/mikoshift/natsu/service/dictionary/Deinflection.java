package io.mikoshift.natsu.service.dictionary;

import java.util.List;

/**
 * One candidate dictionary form recovered from a surface (conjugated) form, with the rule that
 * produced it. {@code conditionsOut} is the grammatical category the candidate is expected to
 * carry, used to reject false-positive matches against a dictionary entry's rule_tags via
 * {@link ConditionHierarchy#conditionsMatch}.
 */
public record Deinflection(String candidate, List<String> conditionsOut, String ruleName, String description) {}
