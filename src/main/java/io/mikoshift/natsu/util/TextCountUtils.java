package io.mikoshift.natsu.util;

import java.util.Set;

/**
 * Counts reading units in plain text across scripts. Space-delimited words are counted for Latin and
 * similar scripts; scripts typically written without word separators (CJK, Thai, etc.) are counted
 * per character.
 */
public final class TextCountUtils {

    private static final Set<Character.UnicodeScript> PER_CHARACTER_SCRIPTS = Set.of(
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.HANGUL,
            Character.UnicodeScript.THAI,
            Character.UnicodeScript.LAO,
            Character.UnicodeScript.MYANMAR,
            Character.UnicodeScript.KHMER);

    private TextCountUtils() {}

    public static int countReadingUnits(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int count = 0;
        boolean inWord = false;

        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (isPerCharacterScript(codePoint)) {
                count++;
                inWord = false;
            } else if (Character.isLetterOrDigit(codePoint)) {
                if (!inWord) {
                    count++;
                    inWord = true;
                }
            } else {
                inWord = false;
            }
        }

        return count;
    }

    private static boolean isPerCharacterScript(int codePoint) {
        return PER_CHARACTER_SCRIPTS.contains(Character.UnicodeScript.of(codePoint));
    }
}
