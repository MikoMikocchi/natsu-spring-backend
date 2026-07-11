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
    void blankSurfaceYieldsNoCandidates() {
        assertThat(deinflector.deinflect("")).isEmpty();
        assertThat(deinflector.deinflect("   ")).isEmpty();
    }

    @Test
    void wordWithNoApplicableRuleYieldsNoCandidates() {
        assertThat(deinflector.deinflect("犬")).isEmpty();
    }

    @Test
    void pastTenseDeinflectsToDictionaryForm() {
        assertThat(candidateWords("食べた")).contains("食べる");
    }

    @Test
    void negativeDeinflectsToDictionaryForm() {
        assertThat(candidateWords("食べない")).contains("食べる");
    }

    @Test
    void irregularIkuPastTenseDeinflectsToDictionaryForm() {
        assertThat(candidateWords("行った")).contains("行く");
    }

    @Test
    void irregularKuruPastTenseDeinflectsToDictionaryForm() {
        assertThat(candidateWords("来た")).contains("来る");
    }

    @Test
    void chainsProgressiveTeFormBackToDictionaryForm() {
        assertThat(candidateWords("食べている")).contains("食べる");
    }

    @Test
    void handlesPoliteNegativePastForms() {
        assertThat(candidateWords("食べませんでした")).contains("食べる");
    }

    @Test
    void ichidanTeFormAndPoliteDeinflectToDictionaryForm() {
        assertThat(candidateWords("食べて")).contains("食べる");
        assertThat(candidateWords("食べます")).contains("食べる");
    }

    @Test
    void godanRowsDeinflectWithCorrectSoundEuphony() {
        assertThat(candidateWords("書いた")).contains("書く"); // k-row
        assertThat(candidateWords("泳いだ")).contains("泳ぐ"); // g-row (voiced)
        assertThat(candidateWords("話した")).contains("話す"); // s-row (no euphony)
        assertThat(candidateWords("飲んだ")).contains("飲む"); // n/b/m-row
    }

    @Test
    void godanNegativeAndPoliteAreUnambiguousByRow() {
        assertThat(candidateWords("書かない")).contains("書く");
        assertThat(candidateWords("読みます")).contains("読む");
    }

    @Test
    void chainedNegativeTeFormDoesNotThrowOrLoop() {
        assertThat(deinflector.deinflect("食べていない")).isNotEmpty();
    }

    @Test
    void handlesGodanPotentialPassiveCausativeAndVolitionalForms() {
        assertThat(candidateWords("書ける")).contains("書く");
        assertThat(candidateWords("書かれる")).contains("書く");
        assertThat(candidateWords("書かせる")).contains("書く");
        assertThat(candidateWords("書こう")).contains("書く");
    }

    @Test
    void godanAmbiguousTtaPastTenseGeneratesAllThreeRowCandidatesForTheSameStem() {
        // "った" is ambiguous by itself -- it could come from a u/tsu/ru-row godan verb -- so all
        // three are generated for the same stem and left for the dictionary lookup to filter.
        assertThat(candidateWords("買った")).contains("買う", "買つ", "買る");
    }

    @Test
    void handlesIAdjectiveAndNaAdjectiveForms() {
        assertThat(candidateWords("大きくなかった")).contains("大きい");
        assertThat(candidateWords("静かではありませんでした")).contains("静かだ");
    }

    @Test
    void chainsDeeplyNestedCompoundInflections() {
        assertThat(candidateWords("食べさせられたくなかった")).contains("食べる");
        assertThat(candidateWords("飲ませられた")).contains("飲む");
        assertThat(candidateWords("食べさせられる")).contains("食べる");
    }

    @Test
    void handlesDesiderativeTaiFormAndItsInflections() {
        assertThat(candidateWords("食べたい")).contains("食べる");
        assertThat(candidateWords("読みたくなかった")).contains("読む");
    }

    @Test
    void handlesTeFormAuxiliariesAndContractions() {
        assertThat(candidateWords("食べてしまう")).contains("食べる");
        assertThat(candidateWords("食べちゃった")).contains("食べる");
        assertThat(candidateWords("食べてください")).contains("食べる");
    }

    @Test
    void handlesObligationContractions() {
        assertThat(candidateWords("食べなきゃ")).contains("食べる");
        assertThat(candidateWords("食べなくちゃ")).contains("食べる");
    }

    @Test
    void handlesIrregularIkuEuphonicForms() {
        assertThat(candidateWords("行った")).contains("行く");
        assertThat(candidateWords("行ってしまった")).contains("行く");
    }

    @Test
    void handlesSuruPotentialAndPassiveIrregulars() {
        assertThat(candidateWords("できる")).contains("する");
        assertThat(candidateWords("されない")).contains("する");
    }

    @Test
    void handlesExpandedAuxiliaryLayers() {
        assertThat(candidateWords("行くまい")).contains("行く");
        assertThat(candidateWords("寒がる")).contains("寒い");
        assertThat(candidateWords("食べたがる")).contains("食べる");
        assertThat(candidateWords("食べながら")).contains("食べる");
        assertThat(candidateWords("飲みつつ")).contains("飲む");
        assertThat(candidateWords("食べるらしい")).contains("食べる");
        assertThat(candidateWords("食べるみたい")).contains("食べる");
    }

    @Test
    void handlesHonorificPoliteVerbsAndVolitionalSlang() {
        assertThat(candidateWords("いらっしゃいます")).contains("いらっしゃる");
        assertThat(candidateWords("行こっか")).contains("行く");
    }

    @Test
    void handlesTryTeMiruForms() {
        assertThat(candidateWords("食べてみる")).contains("食べる");
        assertThat(candidateWords("書いてみる")).contains("書く");
    }

    @Test
    void handlesWantDoneTeHoshiiForms() {
        assertThat(candidateWords("食べてほしい")).contains("食べる");
        assertThat(candidateWords("書いてほしい")).contains("書く");
    }

    @Test
    void handlesSequentialTeKaraForms() {
        assertThat(candidateWords("食べてから")).contains("食べる");
        assertThat(candidateWords("書いてから")).contains("書く");
    }

    @Test
    void deinflectionCarriesRuleNameAndDescription() {
        Deinflection pastTense = deinflector.deinflect("食べたい").stream()
                .filter(d -> d.candidate().equals("食べる"))
                .findFirst()
                .orElseThrow();
        assertThat(pastTense.ruleName()).isEqualTo("desiderative");
        assertThat(pastTense.description()).isNotBlank();
    }

    // ---- gap fixes found while porting from the Rails deinflector -----------------------------

    @Test
    void handlesSentenceFinalQuestionParticle() {
        assertThat(candidateWords("食べますか")).contains("食べる");
        assertThat(candidateWords("食べたのか")).contains("食べる");
        assertThat(candidateWords("大丈夫ですか")).contains("大丈夫だ");
    }

    @Test
    void handlesPermissionTeMoIiForms() {
        assertThat(candidateWords("食べてもいい")).contains("食べる");
        assertThat(candidateWords("行かなくてもいい")).contains("行く");
    }

    @Test
    void handlesFormalObligationForms() {
        assertThat(candidateWords("行かなければならない")).contains("行く");
        assertThat(candidateWords("行かなければいけない")).contains("行く");
    }

    @Test
    void handlesProhibitionForms() {
        assertThat(candidateWords("食べてはいけない")).contains("食べる");
    }

    @Test
    void handlesConjectureForms() {
        assertThat(candidateWords("食べるだろう")).contains("食べる");
        assertThat(candidateWords("食べるでしょう")).contains("食べる");
        assertThat(candidateWords("静かだろう")).contains("静かだ");
    }
}
