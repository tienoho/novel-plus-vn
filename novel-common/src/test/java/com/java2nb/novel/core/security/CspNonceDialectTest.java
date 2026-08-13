package com.java2nb.novel.core.security;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import static org.assertj.core.api.Assertions.assertThat;

class CspNonceDialectTest {

    @Test
    void addsServerNonceToInlineAndExternalScriptElements() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode("HTML");
        engine.setTemplateResolver(resolver);
        engine.addDialect(new CspNonceDialect());
        Context context = new Context();
        context.setVariable(CspNonceDialect.NONCE_VARIABLE,
            "valid-server-generated-nonce_123");

        String rendered = engine.process(
            "<script>window.ready=true;</script><script src=\"/app.js\"></script>", context);

        assertThat(rendered).contains(
            "<script nonce=\"valid-server-generated-nonce_123\">",
            "<script src=\"/app.js\" nonce=\"valid-server-generated-nonce_123\"></script>");
    }

    @Test
    void refusesInvalidNonceFromTemplateContext() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(new StringTemplateResolver());
        engine.addDialect(new CspNonceDialect());
        Context context = new Context();
        context.setVariable(CspNonceDialect.NONCE_VARIABLE, "\" onload=\"alert(1)");

        assertThat(engine.process("<script>window.ready=true;</script>", context))
            .doesNotContain("nonce=");
    }
}
