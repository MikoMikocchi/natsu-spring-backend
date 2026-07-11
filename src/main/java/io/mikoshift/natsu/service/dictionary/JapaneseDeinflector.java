package io.mikoshift.natsu.service.dictionary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * A deliberately-scoped-down deinflection rule table -- ichidan and godan verb past/te/negative/
 * polite forms, plus i-adjective past/negative -- not the ~150-rule table a full engine (e.g.
 * Yomitan's) covers. Extending coverage is just adding rows to {@link #RULES}. Recursion is capped
 * at 2 levels (deinflect once, then try deinflecting each result again) to keep candidate
 * generation bounded rather than combinatorial.
 */
@Component
public class JapaneseDeinflector {

    private static final int MAX_DEPTH = 2;

    private static final List<DeinflectionRule> RULES = List.of(
            // Ichidan verbs (v1): conjugations attach directly to the stem.
            new DeinflectionRule("た", "る", "v1", "past tense", "past tense of an ichidan verb"),
            new DeinflectionRule("て", "る", "v1", "te-form", "te-form of an ichidan verb"),
            new DeinflectionRule("ない", "る", "v1", "negative", "negative form of an ichidan verb"),
            new DeinflectionRule("ます", "る", "v1", "polite", "polite form of an ichidan verb"),

            // Godan verbs (v5): past/te-form with row-specific sound euphony (onbin).
            new DeinflectionRule("いた", "く", "v5", "past tense", "past tense of a godan verb ending in く"),
            new DeinflectionRule("いて", "く", "v5", "te-form", "te-form of a godan verb ending in く"),
            new DeinflectionRule("いだ", "ぐ", "v5", "past tense", "past tense of a godan verb ending in ぐ"),
            new DeinflectionRule("いで", "ぐ", "v5", "te-form", "te-form of a godan verb ending in ぐ"),
            new DeinflectionRule("した", "す", "v5", "past tense", "past tense of a godan verb ending in す"),
            new DeinflectionRule("して", "す", "v5", "te-form", "te-form of a godan verb ending in す"),
            new DeinflectionRule("った", "う", "v5", "past tense", "past tense of a godan verb ending in う"),
            new DeinflectionRule("った", "つ", "v5", "past tense", "past tense of a godan verb ending in つ"),
            new DeinflectionRule("った", "る", "v5", "past tense", "past tense of a godan verb ending in る"),
            new DeinflectionRule("って", "う", "v5", "te-form", "te-form of a godan verb ending in う"),
            new DeinflectionRule("って", "つ", "v5", "te-form", "te-form of a godan verb ending in つ"),
            new DeinflectionRule("って", "る", "v5", "te-form", "te-form of a godan verb ending in る"),
            new DeinflectionRule("んだ", "ぬ", "v5", "past tense", "past tense of a godan verb ending in ぬ"),
            new DeinflectionRule("んだ", "ぶ", "v5", "past tense", "past tense of a godan verb ending in ぶ"),
            new DeinflectionRule("んだ", "む", "v5", "past tense", "past tense of a godan verb ending in む"),
            new DeinflectionRule("んで", "ぬ", "v5", "te-form", "te-form of a godan verb ending in ぬ"),
            new DeinflectionRule("んで", "ぶ", "v5", "te-form", "te-form of a godan verb ending in ぶ"),
            new DeinflectionRule("んで", "む", "v5", "te-form", "te-form of a godan verb ending in む"),

            // Godan verbs: negative form (a-row stem + ない), unambiguous per row.
            new DeinflectionRule("かない", "く", "v5", "negative", "negative form of a godan verb ending in く"),
            new DeinflectionRule("がない", "ぐ", "v5", "negative", "negative form of a godan verb ending in ぐ"),
            new DeinflectionRule("さない", "す", "v5", "negative", "negative form of a godan verb ending in す"),
            new DeinflectionRule("たない", "つ", "v5", "negative", "negative form of a godan verb ending in つ"),
            new DeinflectionRule("なない", "ぬ", "v5", "negative", "negative form of a godan verb ending in ぬ"),
            new DeinflectionRule("ばない", "ぶ", "v5", "negative", "negative form of a godan verb ending in ぶ"),
            new DeinflectionRule("まない", "む", "v5", "negative", "negative form of a godan verb ending in む"),
            new DeinflectionRule("らない", "る", "v5", "negative", "negative form of a godan verb ending in る"),
            new DeinflectionRule("わない", "う", "v5", "negative", "negative form of a godan verb ending in う"),

            // Godan verbs: polite form (i-row stem + ます), unambiguous per row.
            new DeinflectionRule("きます", "く", "v5", "polite", "polite form of a godan verb ending in く"),
            new DeinflectionRule("ぎます", "ぐ", "v5", "polite", "polite form of a godan verb ending in ぐ"),
            new DeinflectionRule("します", "す", "v5", "polite", "polite form of a godan verb ending in す"),
            new DeinflectionRule("ちます", "つ", "v5", "polite", "polite form of a godan verb ending in つ"),
            new DeinflectionRule("にます", "ぬ", "v5", "polite", "polite form of a godan verb ending in ぬ"),
            new DeinflectionRule("びます", "ぶ", "v5", "polite", "polite form of a godan verb ending in ぶ"),
            new DeinflectionRule("みます", "む", "v5", "polite", "polite form of a godan verb ending in む"),
            new DeinflectionRule("ります", "る", "v5", "polite", "polite form of a godan verb ending in る"),
            new DeinflectionRule("います", "う", "v5", "polite", "polite form of a godan verb ending in う"),

            // i-adjectives (adj-i).
            new DeinflectionRule("かった", "い", "adj-i", "past tense", "past tense of an i-adjective"),
            new DeinflectionRule("くない", "い", "adj-i", "negative", "negative form of an i-adjective"));

    /** Deduplicated by candidate word; a shallower (fewer chained rules) match wins on conflict. */
    public List<Deinflection> deinflect(String surface) {
        Map<String, Deinflection> byCandidate = new LinkedHashMap<>();
        collect(surface, 1, null, byCandidate);
        return new ArrayList<>(byCandidate.values());
    }

    private void collect(String word, int depth, DeinflectionRule appliedFrom, Map<String, Deinflection> out) {
        if (depth > MAX_DEPTH) {
            return;
        }
        for (DeinflectionRule rule : RULES) {
            if (word.length() <= rule.suffixIn().length() || !word.endsWith(rule.suffixIn())) {
                continue;
            }
            String stem = word.substring(0, word.length() - rule.suffixIn().length());
            String candidate = stem + rule.suffixOut();

            String ruleName = appliedFrom == null ? rule.ruleName() : rule.ruleName() + " + " + appliedFrom.ruleName();
            String description = appliedFrom == null
                    ? rule.description()
                    : rule.description() + ", then " + appliedFrom.description();
            out.putIfAbsent(candidate, new Deinflection(candidate, rule.ruleTagOut(), ruleName, description));

            collect(candidate, depth + 1, rule, out);
        }
    }
}
