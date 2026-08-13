package com.java2nb.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class I18nConfigTest {

    @Test
    void vietnameseIsDefaultAndChineseRemainsAvailable() {
        MessageSource source = new I18nConfig().messageSource();
        assertEquals("Quản trị Khởi Thư",
            source.getMessage("admin.title", null, Locale.forLanguageTag("vi-VN")));
        assertEquals("小说精品屋后台管理",
            source.getMessage("admin.title", null, Locale.SIMPLIFIED_CHINESE));
        assertEquals(I18nConfig.VIETNAMESE, new I18nConfig().localeResolver().resolveLocale(null));
    }
}
