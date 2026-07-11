package io.mikoshift.natsu.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextCountUtilsTest {

    @Test
    void countsSpaceDelimitedWordsInEnglish() {
        assertThat(TextCountUtils.countReadingUnits("Hello world.")).isEqualTo(2);
        assertThat(TextCountUtils.countReadingUnits("a picture")).isEqualTo(2);
    }

    @Test
    void countsEachJapaneseCharacterAsOneUnit() {
        assertThat(TextCountUtils.countReadingUnits("私は学生です")).isEqualTo(6);
        assertThat(TextCountUtils.countReadingUnits("コーヒー")).isEqualTo(4);
    }

    @Test
    void countsMixedJapaneseAndEnglish() {
        assertThat(TextCountUtils.countReadingUnits("Hello 世界")).isEqualTo(3);
    }

    @Test
    void ignoresPunctuationAndWhitespace() {
        assertThat(TextCountUtils.countReadingUnits("  \n\t")).isZero();
        assertThat(TextCountUtils.countReadingUnits("Hello, world!")).isEqualTo(2);
        assertThat(TextCountUtils.countReadingUnits("私は、学生です。")).isEqualTo(6);
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(TextCountUtils.countReadingUnits(null)).isZero();
        assertThat(TextCountUtils.countReadingUnits("")).isZero();
    }
}
