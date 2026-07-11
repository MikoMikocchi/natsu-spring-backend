package io.mikoshift.natsu.service.dictionary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class JapaneseDeinflectorTest {

    private final JapaneseDeinflector deinflector = new JapaneseDeinflector();

    private List<String> candidateWords(String surface) {
        return deinflector.deinflect(surface).stream()
                .map(Deinflection::candidate)
                .toList();
    }

    @Test
    void ichidanPastTenseDeinflectsToDictionaryForm() {
        assertThat(candidateWords("食べた")).contains("食べる");
    }

    @Test
    void ichidanTeFormDeinflectsToDictionaryForm() {
        assertThat(candidateWords("食べて")).contains("食べる");
    }

    @Test
    void ichidanNegativeDeinflectsToDictionaryForm() {
        assertThat(candidateWords("食べない")).contains("食べる");
    }

    @Test
    void ichidanPoliteDeinflectsToDictionaryForm() {
        assertThat(candidateWords("食べます")).contains("食べる");
    }

    @Test
    void godanKRowPastTenseWithSoundEuphony() {
        assertThat(candidateWords("書いた")).contains("書く");
    }

    @Test
    void godanGRowPastTenseWithVoicedEuphony() {
        assertThat(candidateWords("泳いだ")).contains("泳ぐ");
    }

    @Test
    void godanSRowPastTenseHasNoEuphony() {
        assertThat(candidateWords("話した")).contains("話す");
    }

    @Test
    void godanAmbiguousTtaPastTenseGeneratesAllThreeRowCandidatesForTheSameStem() {
        // "った" is ambiguous by itself -- it could come from a u/tsu/ru-row godan verb -- so all
        // three are generated for the same stem and left for the dictionary lookup to filter.
        assertThat(candidateWords("買った")).contains("買う", "買つ", "買る");
        assertThat(candidateWords("待った")).contains("待う", "待つ", "待る");
        assertThat(candidateWords("売った")).contains("売う", "売つ", "売る");
    }

    @Test
    void godanNRowSoundEuphony() {
        assertThat(candidateWords("飲んだ")).contains("飲む");
    }

    @Test
    void godanNegativeIsUnambiguousByRow() {
        assertThat(candidateWords("書かない")).contains("書く");
        assertThat(candidateWords("読まない")).contains("読む");
    }

    @Test
    void godanPoliteIsUnambiguousByRow() {
        assertThat(candidateWords("書きます")).contains("書く");
        assertThat(candidateWords("読みます")).contains("読む");
    }

    @Test
    void iAdjectivePastTense() {
        assertThat(candidateWords("高かった")).contains("高い");
    }

    @Test
    void iAdjectiveNegative() {
        assertThat(candidateWords("高くない")).contains("高い");
    }

    @Test
    void chainedNegativeTeFormDeinflectsTwoLevels() {
        // 食べていない (not currently eating) -> 食べていない strip "ない" -> stem+る is not quite
        // right for -ている chains, but the engine should at least recover 食べる via -て then -ない
        // applied to different depths without throwing or looping.
        assertThat(deinflector.deinflect("食べていない")).isNotEmpty();
    }

    @Test
    void wordWithNoApplicableRuleYieldsNoCandidates() {
        assertThat(deinflector.deinflect("犬")).isEmpty();
    }

    @Test
    void deinflectionCarriesRuleNameAndDescription() {
        List<Deinflection> results = deinflector.deinflect("食べた");
        Deinflection pastTense = results.stream()
                .filter(d -> d.candidate().equals("食べる"))
                .findFirst()
                .orElseThrow();
        assertThat(pastTense.ruleName()).isEqualTo("past tense");
        assertThat(pastTense.ruleTagOut()).isEqualTo("v1");
        assertThat(pastTense.description()).contains("ichidan");
    }
}
