package com.java2nb.novel.core.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Challenge 3: SensitiveWordFilter DFA Accent, Case, & Special Character Matching")
class SensitiveWordFilterChallengeTest {

    private final SensitiveWordFilter filter = SensitiveWordFilter.getInstance();

    @Test
    @DisplayName("Detects default sensitive words with exact match")
    void testExactMatch() {
        assertThat(filter.containsSensitiveWord("Hành vi đảo chính là vi phạm")).isTrue();
        assertThat(filter.containsSensitiveWord("Nội dung hoàn toàn lành mạnh")).isFalse();
    }

    @Test
    @DisplayName("Detects sensitive words with upper/lowercase and Vietnamese accents normalization")
    void testCaseAndAccentNormalization() {
        assertThat(filter.containsSensitiveWord("Hành vi ĐẢO CHÍNH là vi phạm")).isTrue();
        assertThat(filter.containsSensitiveWord("Hành vi DAO CHINH là vi phạm")).isTrue();
        assertThat(filter.containsSensitiveWord("Hành vi đAo cHíNh là vi phạm")).isTrue();
    }

    @Test
    @DisplayName("Demonstrate flaw: DFA whitespace skip detects word, but filter/replace fails to filter text")
    void testWhitespaceSkipDfaDetectionVsFilterReplacementFailure() {
        String textWithExtraSpaces = "Nội dung đả  o chính có vi phạm";

        // DFA correctly identifies presence of sensitive word
        boolean detected = filter.containsSensitiveWord(textWithExtraSpaces);
        assertThat(detected).isTrue();

        Set<String> foundWords = filter.getFoundWords(textWithExtraSpaces);
        assertThat(foundWords).contains("đảo chính");

        // CRITICAL BUG DEMONSTRATION: filter() tries replaceAll("đảo chính") which fails on "đả  o chính"!
        String filteredText = filter.filter(textWithExtraSpaces, "***");
        System.out.println("Original input : " + textWithExtraSpaces);
        System.out.println("Filtered result: " + filteredText);

        // Verify filter() successfully obscures sensitive word with internal whitespace
        assertThat(filteredText).doesNotContain("đả  o chính");
        assertThat(filteredText).contains("***");
    }

    @Test
    @DisplayName("Punctuation / special characters are handled by DFA detection")
    void testPunctuationBreaksDfaDetection() {
        String textWithPunctuation = "Nội dung đ.ả.o c.h.í.n.h có vi phạm";

        // DFA detects sensitive word separated by periods or special characters
        boolean detected = filter.containsSensitiveWord(textWithPunctuation);
        assertThat(detected).isTrue();
    }

    @Test
    @DisplayName("Mask replacement with asterisks for standard input")
    void testMaskReplacementStandard() {
        String input = "Nội dung đảo chính vi phạm";
        String masked = filter.replaceSensitiveWord(input);
        assertThat(masked).doesNotContain("đảo chính");
        assertThat(masked).contains("***");
    }
}
