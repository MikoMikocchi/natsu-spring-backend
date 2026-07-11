package io.mikoshift.natsu.service.dictionary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConditionHierarchyTest {

    @Test
    void matchesBroadGodanConditionsToSpecificDictionaryRuleTags() {
        assertThat(ConditionHierarchy.conditionsMatch(List.of("v5"), List.of("v5k")))
                .isTrue();
    }

    @Test
    void matchesSpecificGodanConditionsToBroadDictionaryRuleTags() {
        assertThat(ConditionHierarchy.conditionsMatch(List.of("v5k"), List.of("v5")))
                .isTrue();
    }

    @Test
    void doesNotMatchUnrelatedVerbClasses() {
        assertThat(ConditionHierarchy.conditionsMatch(List.of("v1"), List.of("v5k")))
                .isFalse();
    }

    @Test
    void anyConditionAlwaysMatchesForTheSurfaceForm() {
        assertThat(ConditionHierarchy.conditionInMatches(null, List.of("adj-i")))
                .isTrue();
        assertThat(ConditionHierarchy.conditionInMatches(null, List.of())).isTrue();
    }

    @Test
    void emptyConditionsInOnlyMatchesTheSurfaceForm() {
        assertThat(ConditionHierarchy.conditionInMatches(List.of("v1"), List.of()))
                .isFalse();
    }

    @Test
    void chainedConditionsMustBeASubsetOfTheRulesInput() {
        assertThat(ConditionHierarchy.conditionInMatches(List.of("v5k"), List.of("v5")))
                .isTrue();
        assertThat(ConditionHierarchy.conditionInMatches(List.of("v1"), List.of("adj-i")))
                .isFalse();
    }
}
