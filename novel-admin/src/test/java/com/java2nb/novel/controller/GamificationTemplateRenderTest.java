package com.java2nb.novel.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.messageresolver.SpringMessageResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class GamificationTemplateRenderTest {

    private SpringTemplateEngine engine;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver templates = new ClassLoaderTemplateResolver();
        templates.setPrefix("templates/");
        templates.setSuffix(".html");
        templates.setTemplateMode("HTML");
        templates.setCharacterEncoding("UTF-8");

        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setDefaultLocale(Locale.forLanguageTag("vi-VN"));

        SpringMessageResolver messageResolver = new SpringMessageResolver();
        messageResolver.setMessageSource(messages);
        engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templates);
        engine.setMessageResolver(messageResolver);
    }

    @Test
    void settingsAndPolicyStudioRenderVietnameseCatalogWithCspNonce() {
        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        WebContext context = new WebContext(
            JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(request, response),
            Locale.forLanguageTag("vi-VN"));
        context.setVariable("cspNonce", "nonce-for-test");
        context.setVariable("currentUserId", 42L);

        String settings = engine.process("novel/gamification/settings", context);
        String policies = engine.process("novel/gamification/policies", context);

        assertThat(settings)
            .contains("lang=\"vi\"", "nonce=\"nonce-for-test\"", "Cấu hình Gamification")
            .contains("Hàng đợi phê duyệt", "Đang áp dụng")
            .contains("window.GamificationSettingsI18n")
            .doesNotContain("??admin.gamification");
        assertThat(policies)
            .contains("lang=\"vi\"", "nonce=\"nonce-for-test\"", "Bộ policy Gamification")
            .contains("window.GamificationPolicyI18n")
            .doesNotContain("??admin.gamification");
    }
}
