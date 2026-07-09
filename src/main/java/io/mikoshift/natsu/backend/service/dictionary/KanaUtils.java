package io.mikoshift.natsu.backend.service.dictionary;

/** Katakana <-> hiragana folding so a lookup on either script finds the same term. */
public final class KanaUtils {

    private static final int KATAKANA_START = 0x30A1;
    private static final int KATAKANA_END = 0x30F6;
    private static final int HIRAGANA_START = 0x3041;
    private static final int HIRAGANA_END = 0x3096;
    private static final int KANA_SHIFT = 0x60;

    private KanaUtils() {}

    public static String toHiragana(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            result.append(c >= KATAKANA_START && c <= KATAKANA_END ? (char) (c - KANA_SHIFT) : c);
        }
        return result.toString();
    }

    public static String toKatakana(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            result.append(c >= HIRAGANA_START && c <= HIRAGANA_END ? (char) (c + KANA_SHIFT) : c);
        }
        return result.toString();
    }

    /** The "other" kana-script rendering of {@code text}, or {@code text} unchanged if it has no kana. */
    public static String foldedVariant(String text) {
        String asHiragana = toHiragana(text);
        return asHiragana.equals(text) ? toKatakana(text) : asHiragana;
    }
}
