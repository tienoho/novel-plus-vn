package com.java2nb.common.utils;

import com.java2nb.common.config.I18nConfig;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.ResourceBundle;
import java.text.MessageFormat;

/** Điểm truy cập thống nhất tới danh mục thông báo quản trị. */
@Component
public class Messages {

    private final MessageSource messageSource;

    public Messages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String get(String code, Object... arguments) {
        return messageSource.getMessage(code, arguments, I18nConfig.VIETNAMESE);
    }

    /**
     * Đọc thông báo mặc định cho các lớp tiện ích tĩnh không do Spring quản lý.
     */
    public static String getDefault(String code, Object... arguments) {
        Locale locale = I18nConfig.VIETNAMESE;
        String pattern = ResourceBundle.getBundle("i18n.messages", locale).getString(code);
        return new MessageFormat(pattern, locale).format(arguments);
    }
}
