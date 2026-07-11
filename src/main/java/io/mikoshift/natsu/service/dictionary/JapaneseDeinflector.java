package io.mikoshift.natsu.service.dictionary;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Chained morphological deinflector for Japanese. Each conjugation is modelled as an atomic
 * transform that strips one layer of inflection and reports the grammatical category of the
 * result; transforms chain as long as the current candidate's conditions are a subset of the next
 * transform's required input conditions, so arbitrarily nested forms (食べさせられたくなかった ->
 * 食べる) can be peeled in one pass. The rule table lives in {@link DeinflectionTransforms}.
 */
@Component
public class JapaneseDeinflector {

    private static final int MAX_DEPTH = 6;
    private static final int MAX_CANDIDATES = 80;

    private record Node(String text, List<String> conditions, String origin, int depth) {}

    public List<Deinflection> deinflect(String surface) {
        if (surface == null || surface.isBlank()) {
            return List.of();
        }

        Map<String, Deinflection> results = new LinkedHashMap<>();
        Set<String> visited = new HashSet<>();
        Deque<Node> queue = new ArrayDeque<>();
        queue.add(new Node(surface, null, null, 0));

        while (!queue.isEmpty()) {
            Node node = queue.poll();
            if (results.size() >= MAX_CANDIDATES) {
                break;
            }

            for (DeinflectionRule rule : DeinflectionTransforms.RULES) {
                if (!node.text().endsWith(rule.suffix())) {
                    continue;
                }
                if (!ConditionHierarchy.conditionInMatches(node.conditions(), rule.conditionsIn())) {
                    continue;
                }

                String stem = node.text()
                        .substring(0, node.text().length() - rule.suffix().length());
                if (stem.isEmpty() && rule.replacement().isEmpty()) {
                    continue;
                }

                String candidate = stem + rule.replacement();
                if (candidate.equals(node.text()) || candidate.equals(surface)) {
                    continue;
                }

                String signature = candidate + ' ' + String.join(",", rule.conditionsOut());
                if (!visited.add(signature)) {
                    continue;
                }

                // origin preserves the outermost (surface) transform name so the reader sees the
                // inflection that was actually visible, not the deepest one in the chain.
                String ruleName = node.origin() != null ? node.origin() : rule.ruleName();

                Deinflection existing = results.get(candidate);
                if (existing == null) {
                    results.put(
                            candidate,
                            new Deinflection(
                                    candidate,
                                    rule.conditionsOut(),
                                    ruleName,
                                    DeinflectionTransforms.describe(ruleName)));
                } else {
                    results.put(
                            candidate,
                            new Deinflection(
                                    candidate,
                                    union(existing.conditionsOut(), rule.conditionsOut()),
                                    existing.ruleName(),
                                    existing.description()));
                }

                if (node.depth() + 1 < MAX_DEPTH) {
                    queue.add(new Node(candidate, rule.conditionsOut(), ruleName, node.depth() + 1));
                }
            }
        }

        return new ArrayList<>(results.values());
    }

    private static List<String> union(List<String> a, List<String> b) {
        Set<String> merged = new LinkedHashSet<>(a);
        merged.addAll(b);
        return List.copyOf(merged);
    }
}
