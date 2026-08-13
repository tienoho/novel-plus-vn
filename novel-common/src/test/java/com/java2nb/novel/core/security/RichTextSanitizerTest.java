package com.java2nb.novel.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RichTextSanitizerTest {

    private final RichTextSanitizer sanitizer = new RichTextSanitizer();

    @Test
    void keepsApprovedFormattingAndRemovesExecutableMarkup() {
        String result = sanitizer.sanitize(
                "<p onclick=\"steal()\"><strong>Chương một</strong>"
                        + "<script>alert(1)</script>"
                        + "<img src=\"javascript:alert(2)\" onerror=\"steal()\">"
                        + "<a href=\"https://khoithu.vn\">Nguồn</a></p>");

        assertThat(result)
                .contains("<p>", "<strong>Chương một</strong>", "https://khoithu.vn")
                .doesNotContain("<script", "onclick", "onerror", "javascript:");
    }

    @Test
    void handlesNullWithoutCreatingLiteralText() {
        assertThat(sanitizer.sanitize(null)).isNull();
    }

    @Test
    void plainTextRemovesMarkupButKeepsVisibleText() {
        assertThat(sanitizer.sanitizeText(
                "Xin chào <img src=x onerror=steal()> <b>độc giả</b>"))
                .isEqualTo("Xin chào  độc giả");
    }
}
