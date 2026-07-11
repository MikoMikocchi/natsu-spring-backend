package io.mikoshift.natsu.service.dictionary;

/**
 * A godan (五段) conjugation row, keyed by its dictionary-form (終止形) ending. {@code a/i/e/o} are
 * the row's kana on each vowel grade (未然/連用/仮定・命令/意志); {@code te}/{@code ta} are the
 * euphonic (音便) て/た forms; {@code leaf} is the specific rule-tag used to match a dictionary
 * entry's rule_tags (e.g. "v5k") once the broader "v5" tag has matched.
 */
enum GodanRow {
    U("う", "わ", "い", "え", "お", "って", "った", "v5u"),
    KU("く", "か", "き", "け", "こ", "いて", "いた", "v5k"),
    GU("ぐ", "が", "ぎ", "げ", "ご", "いで", "いだ", "v5g"),
    SU("す", "さ", "し", "せ", "そ", "して", "した", "v5s"),
    TSU("つ", "た", "ち", "て", "と", "って", "った", "v5t"),
    NU("ぬ", "な", "に", "ね", "の", "んで", "んだ", "v5n"),
    BU("ぶ", "ば", "び", "べ", "ぼ", "んで", "んだ", "v5b"),
    MU("む", "ま", "み", "め", "も", "んで", "んだ", "v5m"),
    RU("る", "ら", "り", "れ", "ろ", "って", "った", "v5r");

    final String dictEnding;
    final String a;
    final String i;
    final String e;
    final String o;
    final String te;
    final String ta;
    final String leaf;

    GodanRow(String dictEnding, String a, String i, String e, String o, String te, String ta, String leaf) {
        this.dictEnding = dictEnding;
        this.a = a;
        this.i = i;
        this.e = e;
        this.o = o;
        this.te = te;
        this.ta = ta;
        this.leaf = leaf;
    }

    String kana(Grade grade) {
        return switch (grade) {
            case A -> a;
            case I -> i;
            case E -> e;
            case O -> o;
        };
    }
}
