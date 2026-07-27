package com.java2nb.novel.core.utils;

import com.java2nb.novel.core.utils.SensitiveWordFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SensitiveWordFilterTest {

    private SensitiveWordFilter filter;

    @BeforeEach
    public void setUp() {
        filter = SensitiveWordFilter.getInstance();
        filter.addWord("đảo chính");
        filter.addWord("bạo động");
        filter.addWord("lừa đảo");
    }

    @Test
    public void testContainsSensitiveWordWithAccents() {
        assertTrue(filter.containsSensitiveWord("Đây là hành vi đảo chính chính quyền."));
        assertTrue(filter.containsSensitiveWord("Cuộc bạo động nổ ra tối qua."));
    }

    @Test
    public void testContainsSensitiveWordUnaccentedMatching() {
        // Accent normalization: "dao chinh" should match "đảo chính"
        assertTrue(filter.containsSensitiveWord("Phát hiện âm mưu dao chinh."));
        assertTrue(filter.containsSensitiveWord("Kẻ lua dao xuất hiện."));
    }

    @Test
    public void testCleanTextPasses() {
        assertFalse(filter.containsSensitiveWord("Đây là một cuốn tiểu thuyết hoàn toàn lành mạnh."));
    }

    @Test
    public void testReplaceSensitiveWord() {
        String input = "Cần ngăn chặn hành vi đảo chính và bạo động.";
        String masked = filter.replaceSensitiveWord(input, '*');
        assertFalse(masked.contains("đảo chính"));
        assertFalse(masked.contains("bạo động"));
        assertTrue(masked.contains("***"));
    }
}
