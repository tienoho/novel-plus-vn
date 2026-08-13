package com.java2nb.novel.core.security;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * Làm sạch HTML do tác giả, quản trị viên hoặc crawler cung cấp trước khi lưu.
 */
@Component
public class RichTextSanitizer {

    private final PolicyFactory plainTextPolicy = new HtmlPolicyBuilder().toFactory();

    private final PolicyFactory policy = new HtmlPolicyBuilder()
        .allowElements(
            "p", "br", "blockquote", "pre", "code",
            "strong", "b", "em", "i", "u", "s",
            "h1", "h2", "h3", "h4", "h5", "h6",
            "ul", "ol", "li", "a", "img")
        .allowAttributes("href", "title").onElements("a")
        .allowAttributes("src", "alt", "title").onElements("img")
        .allowUrlProtocols("http", "https")
        .requireRelNofollowOnLinks()
        .toFactory();

    public String sanitize(String html) {
        return html == null ? null : policy.sanitize(html);
    }

    public String sanitizeText(String text) {
        return text == null ? null : plainTextPolicy.sanitize(text);
    }
}
