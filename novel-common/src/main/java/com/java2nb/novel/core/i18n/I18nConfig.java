package com.java2nb.novel.core.i18n;

import org.springframework.context.MessageSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Cấu hình ngôn ngữ dùng chung cho cổng đọc và trình thu thập. */
@Configuration
@ConditionalOnProperty(name = "novel.common.i18n.enabled", havingValue = "true", matchIfMissing = true)
public class I18nConfig {

    public static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("i18n/common/messages", "i18n/messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setDefaultLocale(VIETNAMESE);
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }

    @Bean
    public LocaleResolver localeResolver() {
        return new FixedLocaleResolver(VIETNAMESE);
    }

    @Bean
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource);
        return validator;
    }
}
