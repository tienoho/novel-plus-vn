package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FrontClientSecurityPackagingTest {

    private final Path repository = Path.of("..").toAbsolutePath().normalize();

    @Test
    void safeDomUsesTextNodesAndAllowsOnlyHttpProtocols() throws Exception {
        assertThat(read("novel-front/src/main/resources/static/javascript/safe-dom.js"))
            .contains("node.textContent = text(value)")
            .contains("parsed.protocol === \"http:\" || parsed.protocol === \"https:\"")
            .doesNotContain("innerHTML", "insertAdjacentHTML", "eval(");
    }

    @Test
    void everyOverridingCommonLayoutLoadsSafeDomBeforeApplicationScripts() throws Exception {
        for (String layout : List.of(
            "novel-front/src/main/resources/templates/common/js.html",
            "novel-front/src/main/resources/templates/mobile/common/js.html",
            "templates/green/html/common/js.html",
            "templates/green/html/mobile/common/js.html",
            "templates/orange/html/common/js.html",
            "templates/orange/html/mobile/common/js.html",
            "templates/dark/html/common/js.html",
            "templates/dark/html/mobile/common/js.html",
            "templates/blue/html/common/js.html")) {
            String source = read(layout);
            assertThat(source)
                .as(layout)
                .contains("/javascript/safe-dom.js")
                .contains("/javascript/csp-actions.js");
            assertThat(source.indexOf("/javascript/safe-dom.js"))
                .as(layout)
                .isLessThan(source.indexOf("/javascript/csp-actions.js"));
        }
    }

    @Test
    void apiAndDatabaseValuesAreRenderedThroughDomNodes() throws Exception {
        assertThat(read("novel-front/src/main/resources/templates/author/draft_list.html"))
            .contains("/javascript/safe-dom.js")
            .contains("NovelSafeDom.element('td'", "replaceChildren(fragment)")
            .doesNotContain("escapeHtml", "$('#draftRows').html(", "Number(draft.id)");
        assertThat(read("novel-front/src/main/resources/templates/author/author_analytics.html"))
            .contains("/javascript/safe-dom.js")
            .contains("NovelSafeDom.positiveId(getSearchString('bookId'))")
            .contains("NovelSafeDom.element('tr'", "replaceChildren(fragment)")
            .doesNotContain("escapeHtml", "$('#chapterAnalytics').html(",
                "Number(getSearchString('bookId'))");
        assertThat(read("templates/dark/html/book/book_detail.html"))
            .contains("NovelSafeDom.positiveId(bookIndex.id)", "replaceChildren(fragment)")
            .doesNotContain("indexListHtml", "$('#indexList').html(", "$(\"#indexList\").html(");
        assertThat(read("novel-front/src/main/resources/static/javascript/csp-actions.js"))
            .contains("window.continueDraft(target.getAttribute('data-draft-id'))")
            .doesNotContain("continueDraft(Number(");
    }

    @Test
    void commonScriptDoesNotDependOnHeaderCookiePlugin() throws Exception {
        for (String script : List.of(
            "novel-front/src/main/resources/static/javascript/common.js",
            "templates/green/static/javascript/common.js",
            "templates/orange/static/javascript/common.js")) {
            assertThat(read(script))
                .as(script)
                .contains("function novelReadCookie(name)", "novelReadCookie('XSRF-TOKEN')",
                    "novelReadCookie('NovelSession')")
                .doesNotContain("$.cookie('XSRF-TOKEN')", "$.cookie('NovelSession')");
        }
    }

    @Test
    void frontMappersDoNotUseRawStringSubstitution() throws Exception {
        Path mapperRoot = repository.resolve("novel-front/src/main/resources/mybatis");
        List<String> unsafe;
        try (var paths = Files.walk(mapperRoot)) {
            unsafe = paths.filter(path -> path.toString().endsWith(".xml"))
                .filter(path -> {
                    try {
                        return Files.readString(path, StandardCharsets.UTF_8).contains("${");
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .map(mapperRoot::relativize)
                .map(Path::toString)
                .sorted()
                .toList();
        }

        assertThat(unsafe).isEmpty();
    }

    private String read(String relativePath) throws Exception {
        return Files.readString(repository.resolve(relativePath), StandardCharsets.UTF_8)
            .replace("\r\n", "\n");
    }
}
