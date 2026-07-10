package io.mikoshift.natsu.backend.service.dictionary;

/**
 * One candidate dictionary form recovered from a surface (conjugated) form, with the rule(s) that
 * produced it.
 */
public record Deinflection(
    String candidate, String ruleTagOut, String ruleName, String description) {}
