package com.java2nb.novel.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VietnameseSearchNormalizationTest {

    @Test
    void keywordWhitespaceIsNormalizedWithoutRemovingVietnameseDiacritics() {
        assertThat(BookServiceImpl.normalizeSearchKeyword(" \tĐắc   Nhân\nTâm "))
            .isEqualTo("Đắc Nhân Tâm");
        assertThat(BookServiceImpl.normalizeSearchKeyword(" \r\n ")).isNull();
        assertThat(BookServiceImpl.normalizeSearchKeyword(null)).isNull();
    }
}

