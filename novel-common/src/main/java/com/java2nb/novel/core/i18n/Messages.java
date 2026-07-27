package com.java2nb.novel.core.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/** Điểm truy cập thống nhất tới danh mục thông báo của ứng dụng. */
@Component
@ConditionalOnProperty(name = "novel.common.i18n.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class Messages {

    private final MessageSource messageSource;

    public String get(String code, Object... arguments) {
        return messageSource.getMessage(code, arguments, I18nConfig.VIETNAMESE);
    }

    /** Đọc catalog Việt cố định cho các enum và tiện ích tĩnh. */
    public static String getDefault(String code, Object... arguments) {
        String pattern = ResourceBundle.getBundle("i18n.common.messages", I18nConfig.VIETNAMESE).getString(code);
        return new MessageFormat(pattern, I18nConfig.VIETNAMESE).format(arguments);
    }
}
