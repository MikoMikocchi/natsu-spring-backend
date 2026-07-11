package io.mikoshift.natsu.service.dictionary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The full Japanese deinflection rule table: every grammatical transform layer, expanded into
 * concrete suffix rules, plus the English description shown to the reader for each rule name.
 *
 * <p>Each layer mirrors one entry of the Rails port's {@code config/japanese/transforms.yml}, built
 * from the same primitives:
 *
 * <ul>
 *   <li>{@link #rt} -- a single literal suffix/replacement rule.
 *   <li>{@link #allVerbs} -- every verb class (ichidan directly on the ending; godan via the grade
 *       kana; suru/kuru via their irrealis/continuative stems), toggled with {@code irregular}.
 *   <li>{@link #godanGrade} -- godan verbs only, one rule per row.
 *   <li>{@link #godanEuphonic} -- godan verbs only, keyed by the row's て/た euphonic form.
 * </ul>
 *
 * A trailing "gap fixes" section adds layers absent from the Rails source (sentence-final か/のか,
 * explanatory の, permission てもいい, formal obligation なければならない, prohibition てはいけない, and
 * conjecture でしょう／だろう) discovered while porting.
 */
final class DeinflectionTransforms {

    private static final List<String> NONE = List.of();
    private static final List<String> OUT_TE = List.of("-te");
    private static final List<String> OUT_BA = List.of("-ba");
    private static final List<String> OUT_MASU = List.of("-masu");
    private static final List<String> OUT_ADJ_I = List.of("adj-i");
    private static final List<String> OUT_ADJ_NA = List.of("adj-na");
    private static final List<String> OUT_V1 = List.of("v1");
    private static final List<String> OUT_VS = List.of("vs");
    private static final List<String> OUT_VK = List.of("vk");
    private static final List<String> OUT_V = List.of("v");
    private static final List<String> OUT_V_ADJ_I = List.of("v", "adj-i");
    private static final List<String> OUT_V_ADJ_I_ADJ_NA = List.of("v", "adj-i", "adj-na");

    private static final List<String> IN_V1 = List.of("v1");
    private static final List<String> IN_V5 = List.of("v5");
    private static final List<String> IN_VK = List.of("vk");
    private static final List<String> IN_ADJ_I = List.of("adj-i");
    private static final List<String> IN_TA = List.of("-ta");
    private static final List<String> IN_TE = List.of("-te");
    private static final List<String> IN_BA = List.of("-ba");
    private static final List<String> IN_MASU = List.of("-masu");
    private static final List<String> IN_N = List.of("-n");
    private static final List<String> IN_NASAI = List.of("-nasai");
    private static final List<String> IN_V1_V5 = List.of("v1", "v5");

    static final List<DeinflectionRule> RULES;
    private static final Map<String, String> DESCRIPTIONS;

    private DeinflectionTransforms() {}

    static String describe(String ruleName) {
        return DESCRIPTIONS.get(ruleName);
    }

    // --- rule-generation primitives, mirroring the Rails loader's expand_entry family -----------

    private record RuleTemplate(String suffix, String replacement, List<String> in, List<String> out) {
        DeinflectionRule withName(String ruleName) {
            return new DeinflectionRule(suffix, replacement, in, out, ruleName);
        }
    }

    private static List<RuleTemplate> rt(String suffix, String replacement, List<String> in, List<String> out) {
        return List.of(new RuleTemplate(suffix, replacement, in, out));
    }

    private static List<RuleTemplate> godanGrade(String ending, Grade grade, List<String> in) {
        List<RuleTemplate> rules = new ArrayList<>();
        for (GodanRow row : GodanRow.values()) {
            rules.add(new RuleTemplate(row.kana(grade) + ending, row.dictEnding, in, List.of("v5", row.leaf)));
        }
        return rules;
    }

    private enum Euphonic {
        TE,
        TA
    }

    private static List<RuleTemplate> godanEuphonic(Euphonic which, String append, List<String> in) {
        List<RuleTemplate> rules = new ArrayList<>();
        for (GodanRow row : GodanRow.values()) {
            String base = which == Euphonic.TE ? row.te : row.ta;
            rules.add(new RuleTemplate(base + append, row.dictEnding, in, List.of("v5", row.leaf)));
        }
        return rules;
    }

    private static List<RuleTemplate> godanEuphonic(Euphonic which, List<String> in) {
        return godanEuphonic(which, "", in);
    }

    private static String suruStem(Grade grade) {
        return switch (grade) {
            case A, I, O -> "し";
            case E -> "せ";
        };
    }

    private static String kuruStem(Grade grade) {
        return switch (grade) {
            case A, E, O -> "こ";
            case I -> "き";
        };
    }

    private static List<RuleTemplate> allVerbs(String ending, Grade grade, List<String> in) {
        return allVerbs(ending, grade, in, true);
    }

    private static List<RuleTemplate> allVerbs(String ending, Grade grade, List<String> in, boolean irregular) {
        List<RuleTemplate> rules = new ArrayList<>();
        rules.add(new RuleTemplate(ending, "る", in, OUT_V1));
        rules.addAll(godanGrade(ending, grade, in));
        if (irregular) {
            rules.add(new RuleTemplate(suruStem(grade) + ending, "する", in, OUT_VS));
            rules.add(new RuleTemplate(kuruStem(grade) + ending, "くる", in, OUT_VK));
        }
        return rules;
    }

    // --- layer assembly ---------------------------------------------------------------------------

    private record Layer(String name, String description, List<DeinflectionRule> rules) {}

    @SafeVarargs
    private static Layer layer(String name, String description, List<RuleTemplate>... groups) {
        List<DeinflectionRule> rules = new ArrayList<>();
        for (List<RuleTemplate> group : groups) {
            for (RuleTemplate template : group) {
                rules.add(template.withName(name));
            }
        }
        return new Layer(name, description, rules);
    }

    private static List<String> irregularConditionIn(String ruleName) {
        return switch (ruleName) {
            case "te-form" -> IN_TE;
            case "past" -> IN_TA;
            default -> NONE;
        };
    }

    /** 行く/いく/逝く/往く: godan-く euphony that irregularly uses って／った instead of いて／いた. */
    private static List<DeinflectionRule> ikuRules() {
        Map<String, String> forms = new LinkedHashMap<>();
        forms.put("って", "te-form");
        forms.put("った", "past");
        forms.put("ったら", "conditional-past");
        forms.put("ったり", "alternative");

        List<String> out = List.of("v5", "v5k-s");
        List<DeinflectionRule> rules = new ArrayList<>();
        for (String dict : List.of("行く", "いく", "逝く", "往く")) {
            String stem = dict.substring(0, dict.length() - 1);
            for (Map.Entry<String, String> form : forms.entrySet()) {
                String ruleName = form.getValue();
                rules.add(new DeinflectionRule(
                        stem + form.getKey(), dict, irregularConditionIn(ruleName), out, ruleName));
            }
        }
        return rules;
    }

    /** 来る/來る: kanji orthography doesn't share kana stems with the generated verb rules. */
    private static List<DeinflectionRule> kuruKanjiRules() {
        Map<String, String> forms = new LinkedHashMap<>();
        forms.put("ない", "negative");
        forms.put("なかった", "negative-past");
        forms.put("た", "past");
        forms.put("て", "te-form");
        forms.put("ます", "polite");
        forms.put("ました", "past-polite");
        forms.put("ません", "negative-polite");
        forms.put("ませんでした", "negative-polite-past");
        forms.put("れば", "conditional");
        forms.put("よう", "volitional");
        forms.put("い", "imperative");
        forms.put("たい", "desiderative");
        forms.put("させる", "causative");
        forms.put("られる", "passive");
        forms.put("させられる", "causative");
        forms.put("ている", "progressive");
        forms.put("てる", "progressive");
        forms.put("ていた", "progressive");
        forms.put("てください", "request");

        List<String> out = List.of("vk");
        List<DeinflectionRule> rules = new ArrayList<>();
        for (String stem : List.of("来", "來")) {
            String dict = stem + "る";
            for (Map.Entry<String, String> form : forms.entrySet()) {
                String ruleName = form.getValue();
                rules.add(new DeinflectionRule(
                        stem + form.getKey(), dict, irregularConditionIn(ruleName), out, ruleName));
            }
        }
        return rules;
    }

    static {
        List<Layer> layers = new ArrayList<>();

        // ---- polite (-masu) layer ------------------------------------------------------------
        layers.add(layer("polite", "Polite non-past form (-masu).", allVerbs("ます", Grade.I, IN_MASU)));
        layers.add(layer("past-polite", "Polite past form (-mashita).", rt("ました", "ます", NONE, OUT_MASU)));
        layers.add(layer(
                "negative-polite",
                "Polite negative form (-masen).",
                rt("ません", "ます", NONE, OUT_MASU),
                rt("くありません", "い", NONE, OUT_ADJ_I)));
        layers.add(layer(
                "negative-polite-past",
                "Polite negative past form (-masen deshita).",
                rt("ませんでした", "ます", NONE, OUT_MASU),
                rt("くありませんでした", "い", NONE, OUT_ADJ_I)));
        layers.add(layer("volitional-polite", "Polite volitional form (-mashou).", rt("ましょう", "ます", NONE, OUT_MASU)));
        layers.add(layer("te-polite", "Polite te-form (-mashite).", rt("まして", "ます", NONE, OUT_MASU)));
        layers.add(layer(
                "honorific-polite",
                "Honorific polite verbs (irassharu, kudasaru, etc.).",
                rt("いらっしゃいます", "いらっしゃる", IN_MASU, List.of("v5", "v5r")),
                rt("ございます", "ござる", IN_MASU, List.of("v5", "v5r")),
                rt("なさいます", "なさる", IN_MASU, List.of("v5", "v5r")),
                rt("くださいます", "くださる", IN_MASU, List.of("v5", "v5r")),
                rt("おっしゃいます", "おっしゃる", IN_MASU, List.of("v5", "v5r"))));

        // ---- negative --------------------------------------------------------------------------
        layers.add(layer(
                "negative",
                "Plain negative form (-nai). Conjugates as an i-adjective.",
                rt("くない", "い", IN_ADJ_I, OUT_ADJ_I),
                allVerbs("ない", Grade.A, IN_ADJ_I)));
        layers.add(layer(
                "negative-classical-zu",
                "Classical/adverbial negative (-zu).",
                allVerbs("ず", Grade.A, NONE, false),
                rt("せず", "する", NONE, OUT_VS),
                rt("こず", "くる", NONE, OUT_VK)));
        layers.add(layer(
                "negative-classical-nu",
                "Classical negative (-nu).",
                allVerbs("ぬ", Grade.A, NONE, false),
                rt("せぬ", "する", NONE, OUT_VS),
                rt("こぬ", "くる", NONE, OUT_VK)));
        layers.add(layer(
                "negative-n",
                "Colloquial negative (-n), a sound change of -nu.",
                allVerbs("ん", Grade.A, IN_N, false),
                rt("せん", "する", IN_N, OUT_VS),
                rt("こん", "くる", IN_N, OUT_VK)));
        layers.add(
                layer("negative-volitional", "Negative volitional / presumptive (-mai).", rt("まい", "", NONE, OUT_V)));

        // ---- past / te ---------------------------------------------------------------------------
        layers.add(layer(
                "past",
                "Plain past form (-ta).",
                rt("かった", "い", IN_TA, OUT_ADJ_I),
                rt("た", "る", IN_TA, OUT_V1),
                godanEuphonic(Euphonic.TA, IN_TA),
                rt("した", "する", IN_TA, OUT_VS),
                rt("きた", "くる", IN_TA, OUT_VK)));
        layers.add(layer(
                "te-form",
                "Conjunctive te-form (-te).",
                rt("くて", "い", IN_TE, OUT_ADJ_I),
                rt("て", "る", IN_TE, OUT_V1),
                godanEuphonic(Euphonic.TE, IN_TE),
                rt("して", "する", IN_TE, OUT_VS),
                rt("きて", "くる", IN_TE, OUT_VK)));

        // ---- volitional / imperative ------------------------------------------------------------
        layers.add(layer(
                "volitional",
                "Volitional form (-ou / -you): intention or invitation.",
                rt("よう", "る", NONE, OUT_V1),
                godanGrade("う", Grade.O, NONE),
                rt("しよう", "する", NONE, OUT_VS),
                rt("こよう", "くる", NONE, OUT_VK),
                rt("かろう", "い", NONE, OUT_ADJ_I)));
        layers.add(layer(
                "volitional-slang",
                "Colloquial volitional + ka (-kka / -yokka).",
                rt("よっか", "る", NONE, OUT_V1),
                godanGrade("っか", Grade.O, NONE),
                rt("しよっか", "する", NONE, OUT_VS),
                rt("こよっか", "くる", NONE, OUT_VK),
                rt("ましょっか", "ます", NONE, OUT_MASU)));
        layers.add(layer(
                "imperative",
                "Imperative form: command.",
                rt("ろ", "る", NONE, OUT_V1),
                rt("よ", "る", NONE, OUT_V1),
                godanGrade("", Grade.E, NONE),
                rt("しろ", "する", NONE, OUT_VS),
                rt("せよ", "する", NONE, OUT_VS),
                rt("こい", "くる", NONE, OUT_VK)));

        // ---- causative / passive / potential -----------------------------------------------------
        layers.add(layer(
                "causative",
                "Causative form (-saseru / -seru): make/let someone do.",
                rt("させる", "る", IN_V1, OUT_V1),
                godanGrade("せる", Grade.A, IN_V1),
                rt("させる", "する", IN_V1, OUT_VS),
                rt("こさせる", "くる", IN_V1, OUT_VK)));
        layers.add(layer(
                "short-causative",
                "Contracted causative (-su / -sasu).",
                rt("さす", "る", NONE, OUT_V1),
                godanGrade("す", Grade.A, NONE),
                rt("さす", "する", NONE, OUT_VS),
                rt("こさす", "くる", NONE, OUT_VK)));
        layers.add(layer(
                "passive",
                "Passive form (-reru / -rareru). Conjugates as ichidan.",
                godanGrade("れる", Grade.A, IN_V1),
                rt("られる", "る", IN_V1, OUT_V1),
                rt("こられる", "くる", IN_V1, OUT_VK),
                rt("される", "する", IN_V1, OUT_VS),
                rt("せられる", "する", IN_V1, OUT_VS)));
        layers.add(layer(
                "potential",
                "Potential form: ability to do something.",
                rt("れる", "る", IN_V1, OUT_V1),
                godanGrade("る", Grade.E, IN_V1),
                rt("できる", "する", IN_V1, OUT_VS),
                rt("これる", "くる", IN_V1, OUT_VK)));

        // ---- conditional -------------------------------------------------------------------------
        layers.add(layer(
                "conditional",
                "Provisional conditional (-ba): if/when.",
                rt("ければ", "い", IN_BA, OUT_ADJ_I),
                rt("れば", "る", IN_BA, OUT_V1),
                godanGrade("ば", Grade.E, IN_BA),
                rt("すれば", "する", IN_BA, OUT_VS),
                rt("くれば", "くる", IN_BA, OUT_VK)));
        layers.add(layer(
                "conditional-contracted",
                "Contraction of -ba (-ya / -kya).",
                rt("けりゃ", "ければ", NONE, OUT_BA),
                rt("きゃ", "ければ", NONE, OUT_BA),
                rt("や", "えば", NONE, OUT_BA),
                rt("ぎゃ", "げば", NONE, OUT_BA),
                rt("しゃ", "せば", NONE, OUT_BA),
                rt("にゃ", "ねば", NONE, OUT_BA),
                rt("びゃ", "べば", NONE, OUT_BA),
                rt("みゃ", "めば", NONE, OUT_BA),
                rt("りゃ", "れば", NONE, OUT_BA)));
        layers.add(layer(
                "conditional-past",
                "Conditional / temporal (-tara).",
                rt("かったら", "い", NONE, OUT_ADJ_I),
                rt("たら", "る", NONE, OUT_V1),
                godanEuphonic(Euphonic.TA, "ら", NONE),
                rt("したら", "する", NONE, OUT_VS),
                rt("きたら", "くる", NONE, OUT_VK)));
        layers.add(layer(
                "alternative",
                "Representative / alternative listing (-tari).",
                rt("かったり", "い", NONE, OUT_ADJ_I),
                rt("たり", "る", NONE, OUT_V1),
                godanEuphonic(Euphonic.TA, "り", NONE),
                rt("したり", "する", NONE, OUT_VS),
                rt("きたり", "くる", NONE, OUT_VK)));

        // ---- desiderative --------------------------------------------------------------------------
        layers.add(layer(
                "desiderative",
                "Desiderative form (-tai): want to do. Conjugates as i-adjective.",
                rt("たい", "る", IN_ADJ_I, OUT_V1),
                godanGrade("たい", Grade.I, IN_ADJ_I),
                rt("したい", "する", IN_ADJ_I, OUT_VS),
                rt("きたい", "くる", IN_ADJ_I, OUT_VK)));
        layers.add(layer(
                "desiderative-third", "Third-person desiderative / emotive (-garu).", rt("がる", "い", IN_V5, OUT_ADJ_I)));

        // ---- te-form auxiliaries -------------------------------------------------------------------
        layers.add(layer(
                "progressive",
                "Progressive / resultative state (-te iru / -teru).",
                rt("ている", "て", IN_V1_V5, OUT_TE),
                rt("でいる", "で", IN_V1_V5, OUT_TE),
                rt("てる", "て", IN_V1_V5, OUT_TE),
                rt("でる", "で", IN_V1_V5, OUT_TE),
                rt("ておる", "て", IN_V1_V5, OUT_TE),
                rt("でおる", "で", IN_V1_V5, OUT_TE)));
        layers.add(layer(
                "completion",
                "Completion / regret (-te shimau, -chau, -chimau).",
                rt("てしまう", "て", IN_V5, OUT_TE),
                rt("でしまう", "で", IN_V5, OUT_TE),
                rt("ちゃう", "て", IN_V5, OUT_TE),
                rt("じゃう", "で", IN_V5, OUT_TE),
                rt("ちまう", "て", IN_V5, OUT_TE),
                rt("じまう", "で", IN_V5, OUT_TE)));
        layers.add(layer(
                "conditional-te",
                "-te wa / -cha: conditional or 'must not' constructions.",
                rt("ては", "て", NONE, OUT_TE),
                rt("では", "で", NONE, OUT_TE),
                rt("ちゃ", "て", NONE, OUT_TE),
                rt("じゃ", "で", NONE, OUT_TE)));
        layers.add(layer(
                "preparation",
                "Preparatory action (-te oku / -toku).",
                rt("ておく", "て", IN_V5, OUT_TE),
                rt("でおく", "で", IN_V5, OUT_TE),
                rt("とく", "て", IN_V5, OUT_TE),
                rt("どく", "で", IN_V5, OUT_TE)));
        layers.add(layer(
                "resultative",
                "Resultative state (-te aru).",
                rt("てある", "て", IN_V5, OUT_TE),
                rt("である", "で", IN_V5, OUT_TE)));
        layers.add(layer(
                "directional",
                "Directional aspect (-te iku / -te kuru).",
                rt("ていく", "て", IN_V5, OUT_TE),
                rt("でいく", "で", IN_V5, OUT_TE),
                rt("てくる", "て", IN_VK, OUT_TE),
                rt("でくる", "で", IN_VK, OUT_TE)));
        layers.add(layer(
                "try", "Attempt (-te miru): try doing.", rt("てみる", "て", NONE, OUT_TE), rt("でみる", "で", NONE, OUT_TE)));
        layers.add(layer(
                "want-done",
                "Want someone to do (-te hoshii).",
                rt("てほしい", "て", NONE, OUT_TE),
                rt("でほしい", "で", NONE, OUT_TE)));
        layers.add(layer(
                "sequential",
                "Sequential action (-te kara): after doing.",
                rt("てから", "て", NONE, OUT_TE),
                rt("でから", "で", NONE, OUT_TE)));
        layers.add(layer(
                "request",
                "Polite request (-te kudasai).",
                rt("てください", "て", NONE, OUT_TE),
                rt("でください", "で", NONE, OUT_TE)));
        layers.add(layer(
                "obligation",
                "Obligation contractions (-nakya / -nakucha): must do.",
                rt("なくては", "ない", NONE, OUT_ADJ_I),
                rt("なくちゃ", "ない", NONE, OUT_ADJ_I),
                rt("なきゃ", "ない", NONE, OUT_ADJ_I)));

        // ---- continuative auxiliaries ----------------------------------------------------------
        layers.add(layer("polite-imperative", "Polite imperative (-nasai).", allVerbs("なさい", Grade.I, IN_NASAI)));
        layers.add(layer(
                "appearance",
                "Appears that / looking like (-sou).",
                rt("そう", "い", NONE, OUT_ADJ_I),
                allVerbs("そう", Grade.I, NONE)));
        layers.add(layer(
                "excess",
                "Excess (-sugiru): too much / too ...",
                rt("すぎる", "い", IN_V1, OUT_ADJ_I),
                rt("過ぎる", "い", IN_V1, OUT_ADJ_I),
                allVerbs("すぎる", Grade.I, IN_V1),
                allVerbs("過ぎる", Grade.I, IN_V1)));
        layers.add(layer("manner", "Simultaneous action (-nagara): while doing.", allVerbs("ながら", Grade.I, NONE)));
        layers.add(
                layer("simultaneous", "Literary simultaneous / concessive (-tsutsu).", allVerbs("つつ", Grade.I, NONE)));
        layers.add(layer("hearsay-seems", "Seems / I heard that (-rashii).", rt("らしい", "", NONE, OUT_V_ADJ_I)));
        layers.add(layer("similarity", "Looks like / seems like (-mitai).", rt("みたい", "", NONE, OUT_V_ADJ_I)));

        // ---- adjective-only forms ----------------------------------------------------------------
        layers.add(
                layer("adverbial", "Adverbial form of i-adjectives (-ku).", rt("く", "い", List.of("-ku"), OUT_ADJ_I)));
        layers.add(layer(
                "negative-past",
                "Plain negative past of i-adjectives (-kunakatta).",
                rt("くなかった", "い", NONE, OUT_ADJ_I)));
        layers.add(
                layer("nominalization", "Nominalizing suffix of i-adjectives (-sa).", rt("さ", "い", NONE, OUT_ADJ_I)));
        layers.add(layer("appearance-ge", "Appearance suffix of i-adjectives (-ge).", rt("げ", "い", NONE, OUT_ADJ_I)));

        // ---- copula / na-adjective ---------------------------------------------------------------
        layers.add(layer(
                "copula-negative",
                "Negative copula (ja nai / de wa nai).",
                rt("じゃない", "だ", NONE, OUT_ADJ_NA),
                rt("ではない", "だ", NONE, OUT_ADJ_NA)));
        layers.add(layer(
                "copula-negative-past",
                "Negative past copula (ja nakatta).",
                rt("じゃなかった", "だ", NONE, OUT_ADJ_NA),
                rt("ではなかった", "だ", NONE, OUT_ADJ_NA)));
        layers.add(layer(
                "copula-past",
                "Past copula (datta / deshita).",
                rt("だった", "だ", NONE, OUT_ADJ_NA),
                rt("でした", "だ", NONE, OUT_ADJ_NA)));
        layers.add(layer(
                "copula-negative-polite",
                "Polite negative copula (ja arimasen).",
                rt("じゃありません", "だ", NONE, OUT_ADJ_NA),
                rt("ではありません", "だ", NONE, OUT_ADJ_NA),
                rt("じゃありませんでした", "だ", NONE, OUT_ADJ_NA),
                rt("ではありませんでした", "だ", NONE, OUT_ADJ_NA)));
        layers.add(layer(
                "copula-conditional",
                "Conditional copula (nara / de areba).",
                rt("なら", "だ", NONE, OUT_ADJ_NA),
                rt("であれば", "だ", NONE, OUT_ADJ_NA)));

        // ---- gap fixes (absent from the Rails source; found while porting) ----------------------
        layers.add(layer(
                "copula-polite", "Polite copula (desu): polite non-past 'to be'.", rt("です", "だ", NONE, OUT_ADJ_NA)));
        layers.add(layer(
                "question-particle",
                "Sentence-final question particle (-ka).",
                rt("か", "", NONE, NONE),
                // Chains through explanatory の / polite copula です in one step: a root-only
                // rule's wildcard output can feed rules with non-empty conditionsIn, but not
                // another root-only rule, so these stacked combinations need their own literal
                // suffix rather than two sequential strips.
                rt("のか", "", NONE, NONE),
                rt("ですか", "だ", NONE, OUT_ADJ_NA)));
        layers.add(layer("nominalizer-no", "Explanatory / nominalizing sentence-final の.", rt("の", "", NONE, NONE)));
        layers.add(layer(
                "permission",
                "Permission (-te mo ii): may / it's fine to do.",
                rt("てもいい", "て", NONE, OUT_TE),
                rt("でもいい", "で", NONE, OUT_TE),
                rt("くてもいい", "い", NONE, OUT_ADJ_I)));
        layers.add(layer(
                "obligation-formal",
                "Formal obligation (-nakereba naranai / -nakereba ikenai): must do.",
                rt("なければならない", "ない", NONE, OUT_ADJ_I),
                rt("なければいけない", "ない", NONE, OUT_ADJ_I),
                rt("なきゃならない", "ない", NONE, OUT_ADJ_I),
                rt("なくてはならない", "ない", NONE, OUT_ADJ_I),
                rt("なくてはいけない", "ない", NONE, OUT_ADJ_I)));
        layers.add(layer(
                "prohibition",
                "Prohibition (-te wa ikenai / -cha ikenai): must not do.",
                rt("てはいけない", "て", NONE, OUT_TE),
                rt("ではいけない", "で", NONE, OUT_TE),
                rt("ちゃいけない", "て", NONE, OUT_TE),
                rt("じゃいけない", "で", NONE, OUT_TE)));
        layers.add(layer(
                "conjecture",
                "Conjecture / probably (-darou / -deshou).",
                rt("でしょう", "", NONE, OUT_V_ADJ_I_ADJ_NA),
                rt("だろう", "", NONE, OUT_V_ADJ_I_ADJ_NA),
                rt("でしょう", "だ", NONE, OUT_ADJ_NA),
                rt("だろう", "だ", NONE, OUT_ADJ_NA)));

        List<DeinflectionRule> allRules = new ArrayList<>();
        Map<String, String> descriptions = new LinkedHashMap<>();
        for (Layer definedLayer : layers) {
            descriptions.put(definedLayer.name(), definedLayer.description());
            allRules.addAll(definedLayer.rules());
        }
        allRules.addAll(ikuRules());
        allRules.addAll(kuruKanjiRules());

        // Longer suffixes first so more specific forms win the visited-signature dedup race.
        allRules.sort(Comparator.comparingInt((DeinflectionRule r) -> r.suffix().length())
                .reversed());

        RULES = List.copyOf(allRules);
        DESCRIPTIONS = Map.copyOf(descriptions);
    }
}
