package com.java2nb.novel.core.i18n;

import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class I18nConfigTest {

    @Test
    void vietnameseIsDefaultAndChineseRemainsAvailable() {
        MessageSource source = new I18nConfig().messageSource();

        assertEquals("Xu", source.getMessage("common.currency", null, Locale.forLanguageTag("vi-VN")));
        assertEquals("屋币", source.getMessage("common.currency", null, Locale.SIMPLIFIED_CHINESE));
        assertEquals(I18nConfig.VIETNAMESE, new I18nConfig().localeResolver().resolveLocale(null));
    }
}
